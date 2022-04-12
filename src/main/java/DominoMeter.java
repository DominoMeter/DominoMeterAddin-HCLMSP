import java.io.File;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;
import net.prominic.dm.api.Config;
import net.prominic.dm.api.Log;
import net.prominic.dm.api.Ping;
import net.prominic.dm.update.ProgramConfig;
import net.prominic.dm.update.UpdateRobot;
import net.prominic.genesis.JSONRules;
import net.prominic.io.RESTClient;
import net.prominic.util.FileLogger;
import net.prominic.util.FileUtils;
import net.prominic.util.ParsedError;

public class DominoMeter extends JavaServerAddin {
	final String			JADDIN_NAME				= "DominoMeter";
	final String			JADDIN_VERSION			= "116";
	final String			JADDIN_DATE				= "2022-04-12 09:50 (mvn, mfa, genesis)";

	// Message Queue name for this Addin (normally uppercase);
	// MSG_Q_PREFIX is defined in JavaServerAddin.class
	final String 			qName 					= MSG_Q_PREFIX + JADDIN_NAME.toUpperCase();

	// MessageQueue Constants
	public static final int MQ_MAX_MSGSIZE = 1024;
	// this is already defined (should be = 1):
	public static final int	MQ_WAIT_FOR_MSG = MessageQueue.MQ_WAIT_FOR_MSG;

	public static long total_exception_count = 0;
	
	// Instance variables
	private String[] 		args 					= null;
	private int 			dominoTaskID			= 0;

	MessageQueue 			mq						= null;
	Session 				session 				= null;
	Database 				ab 						= null;
	private int				interval				= 120;
	private String 			server					= "";
	private String 			endpoint				= "";
	private String 			version					= "";
	private int				failedCounter			= 0;
	private FileLogger		fileLogger				= null;
	private String 			startDateTime			= "";
	private String 			bufState				= "";

	private ReportThread 	thread					= null;

	// constructor if parameters are provided
	public DominoMeter(String[] args) {
		this.args = args;
	}

	public DominoMeter() {
	}

	@Override
	public void runNotes() {
		try {
			session = NotesFactory.createSession();
			startDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());

			initLogger();

			if (args == null) {
				logMessage("You must provide an endpoint to send data, see instructions below");
				showHelp();
				return;
			}

			// endpoint
			endpoint = args[0];
			if ("dev".equalsIgnoreCase(endpoint)) {
				endpoint = "https://prominic-dev.dominometer.com/duca.nsf";
			}
			else if("prod".equalsIgnoreCase(endpoint)) {
				endpoint = "https://prominic.dominometer.com/duca.nsf";
			}
			else if("belsoft".equalsIgnoreCase(endpoint)) {
				endpoint = "https://belsoft.dominometer.com/duca.nsf";
			}

			if (args.length > 1) {
				setLogLevel(args[1]);
			}
			
			// TODO: install Genesis
			genesis();

			runLoop();
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

	private void genesis() {
		try {
			// check if already installed
			String GJA_Genesis = session.getEnvironmentString("GJA_Genesis", true);
			if (!GJA_Genesis.isEmpty()) {
				logMessage("Genesis - already installed (skip)");
				return;
			}
			
			// find addin in catalog
			String catalog = "https://domino-1.dmytro.cloud/gc.nsf";
			StringBuffer buf = RESTClient.sendGET(catalog + "/app?openagent&name=dominometer-genesis");

			JSONRules rules = new JSONRules(session, ab);
			boolean res = rules.execute(buf.toString());
			
			if (res) {
				logMessage("Genesis installed (OK)");
			}
			else {
				logMessage("Genesis FAILED");
				System.out.println(rules.getLogBuffer());
			}
		} catch (IOException e) {
			logMessage("Install command failed: " + e.getMessage());
		} catch (NotesException e) {
			logMessage("Install command failed: " + e.getMessage());
		}
	}

	@SuppressWarnings("deprecation")
	private void runLoop() {
		StringBuffer qBuffer = new StringBuffer(1024);

		try {
			setAddinState("Initializing");
			fileLogger.info("--------------------------------------");
			fileLogger.info("Initializing");

			// Set the Java thread name to the class name (default would be "Thread-n")
			this.setName(JADDIN_NAME);
			this.dominoTaskID = createAddinStatusLine(this.JADDIN_NAME);

			ab = session.getDatabase(session.getServerName(), "names.nsf");
			server = session.getServerName();
			version = this.JADDIN_NAME + "-" + JADDIN_VERSION + ".jar";

			// check if connection could be established
			boolean check = checkConnection();

			mq = new MessageQueue();
			int messageQueueState = mq.create(qName, 0, 0);	// use like MQCreate in API
			if (messageQueueState == MessageQueue.ERR_DUPLICATE_MQ) {
				logMessage(this.JADDIN_NAME + " task is already running");
				return;
			}

			if (messageQueueState != MessageQueue.NOERROR) {
				logMessage("Unable to create the Domino message queue");
				return;
			}

			if (mq.open(qName, 0) != MessageQueue.NOERROR) {
				logMessage("Unable to open Domino message queue");
				return;
			}

			if (check) Log.sendLog(server, endpoint, version + " - started", "");

			Config config = new Config();
			loadConfig(config);
			showInfo();

			ProgramConfig pc = new ProgramConfig(server, endpoint, JADDIN_NAME, fileLogger);
			pc.setState(ab, ProgramConfig.LOAD);		// set program documents in LOAD state

			UpdateRobot ur = new UpdateRobot(fileLogger);

			// if new version is detected on load - no need to continue
			if (check) {
				boolean updateOnStartup = updateVersion(ur, pc, config.getJAR());
				if (updateOnStartup) return;
			}

			cleanOutdatedFiles(".jar");

			if (check) sendReport(false);

			while (this.addInRunning() && (messageQueueState != MessageQueue.ERR_MQ_QUITTING)) {
				/* gives control to other task in non preemptive os*/
				OSPreemptOccasionally();

				if (thread != null && thread.isAlive()) {
					setAddinState("Report");
				}
				else {
					setAddinState("Idle");
				}

				// check for command from console
				messageQueueState = mq.get(qBuffer, MQ_MAX_MSGSIZE, MessageQueue.MQ_WAIT_FOR_MSG, 1000);
				if (messageQueueState == MessageQueue.ERR_MQ_QUITTING) {
					setAddinState("Quit");
					fileLogger.info("Quit command is sent");
					return;
				}

				resolveMessageQueueState(qBuffer, ur, pc, config);

				if (this.AddInHasMinutesElapsed(interval)) {
					cleanOutdatedFiles(".log");

					if (checkConnection()) {
						loadConfig(config);
						sendReport(false);
						updateVersion(ur, pc, config.getJAR());
					}
				}					
			}
		} catch(Exception e) {
			fileLogger.severe(e);
			e.printStackTrace();
		}
	}
	
	private void initLogger() throws NotesException {
		String separator = System.getProperty("file.separator");
		String directory = session.getEnvironmentString("Directory", true) + separator + "DominoMeterAddin" + separator;
		fileLogger = new FileLogger(directory);
	}

	/*
	 * Clean old jar and log files
	 * We keep last 5 jar files and last 5 log files
	 */
	public void cleanOutdatedFiles(String ext) {
		try {
			File dir = new File("DominoMeterAddin");
			if (!dir.isDirectory()) return;

			File files[] = FileUtils.endsWith(dir, ext);
			if (files.length <= 5) return;

			int count = 0;
			StringBuffer deletedFiles = new StringBuffer();
			files = FileUtils.sortFilesByModified(files, false);
			for (int i = 5; i < files.length; i++) {
				File file = files[i];
				if (!file.getName().equalsIgnoreCase(version)) {
					file.delete();
					if (count > 0) deletedFiles.append(", ");
					deletedFiles.append(file.getName());
					count++;
				}
			}

			if (count>0) {
				fileLogger.info("Removed files (" + Integer.toString(count) + "): " + deletedFiles.toString());
				Log.sendLog(server, endpoint, "Removed files (" + Integer.toString(count) + ")", deletedFiles.toString());
			}

		} catch (Exception e) {
			incrementExceptionTotal();
			fileLogger.severe(e);
			Log.sendError(server, endpoint, new ParsedError(e));
			e.printStackTrace();
		}
	}

	private void resolveMessageQueueState(StringBuffer qBuffer, UpdateRobot ur, ProgramConfig pc, Config config) {
		String cmd = qBuffer.toString().trim();
		if (cmd.isEmpty()) return;
		
		fileLogger.info(qBuffer + " command is sent");

		if ("-h".equals(cmd) || "help".equals(cmd)) {
			showHelp();
		}
		else if ("-i".equals(cmd) || "info".equals(cmd)) {
			showInfo();
		}
		else if ("-u".equals(cmd) || "update".equals(cmd)) {
			config.load(endpoint, server);
			boolean res = updateVersion(ur, pc, config.getJAR());
			if (!res) logMessage("version is up to date");
		}
		else if ("-r".equals(cmd) || "report".equals(cmd)) {
			sendReport(true);
		}
		else if ("-c".equals(cmd) || "config".equals(cmd)) {
			boolean res = loadConfig(config);
			logMessage(res ? "updated (OK)" : "updated (*FAILED*)");
		}
		else {
			logMessage("invalid command (use -h or help to get details)");
		}
	}

	private void setLogLevel(String level) {
		fileLogger.setLevel(Integer.parseInt(level));
	}

	private boolean checkConnection() {
		fileLogger.info("CheckConnection");

		Ping ping = new Ping();
		boolean res = ping.check(endpoint, server);
		fileLogger.info("- " + String.valueOf(res));

		if (res) {
			failedCounter = 0;
			return true;
		};

		failedCounter++;

		logMessage("connection (*FAILED*) with: " + endpoint);
		logMessage("> counter: " + Integer.toString(failedCounter));
		if (ping.getParsedError() != null) {
			logMessage("> " + ping.getParsedError().getMessage());
		}

		if (failedCounter > 24) {
			this.stopAddin();
		}
		return false;
	}

	private boolean loadConfig(Config config) {
		fileLogger.info("LoadConfig");

		boolean res = config.load(endpoint, server);
		fileLogger.info("- " + String.valueOf(res));
		if (res && config.getInterval() > 0) {
			interval = config.getInterval();
		}
		return res;
	}

	private boolean updateVersion(UpdateRobot ur, ProgramConfig pc, String jar) {
		setAddinState("UpdateRobot");
		fileLogger.info("UpdateRobot");

		String newAddinFile = ur.applyNewVersion(session, server, endpoint, jar, version);
		if (newAddinFile.isEmpty()) {
			if (ur.getParsedError() != null) {
				Log.sendError(server, endpoint, ur.getParsedError());
				fileLogger.severe(ur.getParsedError().getMessage());
			}

			fileLogger.info("- " + String.valueOf(false));
			return false;
		}

		pc.setState(ab, ProgramConfig.UNLOAD);		// set program documents in UNLOAD state
		Log.sendLog(server, endpoint, version + " - will be unloaded to upgrade to a newer version: " + newAddinFile, "New version " + newAddinFile + " should start in ~20 mins");
		this.stopAddin();

		fileLogger.info("- " + String.valueOf(true));
		return true;
	}

	private void sendReport(boolean manual) {
		if (thread == null || !thread.isAlive()) {
			thread = new ReportThread(server, endpoint, version, fileLogger, manual);
			thread.start();
		}
		else {
			if (manual) {
				this.logMessage("ReportThread: already running");
			}
		}
	}

	private void showInfo() {
		String abbreviate;
		try {
			Name nServer = session.createName(session.getServerName());
			abbreviate = nServer.getAbbreviated();
		} catch (NotesException e) {
			abbreviate = this.server;
			incrementExceptionTotal();
			e.printStackTrace();
		}
		
		logMessage("server     " + abbreviate);
		logMessage("version    " + this.JADDIN_VERSION);
		logMessage("build date " + this.JADDIN_DATE);
		logMessage("endpoint   " + this.endpoint);
		logMessage("interval   " + Integer.toString(this.interval) + " minutes");
		logMessage("log folder " + fileLogger.getDirectory());
		logMessage("logging    " + fileLogger.getLevelLabel());
		logMessage("started    " + startDateTime);
		logMessage("errors     " + String.valueOf(total_exception_count));
	}

	private void showHelp() {
		int year = Calendar.getInstance().get(Calendar.YEAR);
		logMessage("*** Usage ***");
		AddInLogMessageText("load runjava DominoMeter <endpoint> [logLevel]");
		AddInLogMessageText("   <endpoint> - required. url to send data");
		AddInLogMessageText("   [logLevel] - optional. '0 - debug', '1- info', '2 - severe' (default)");
		AddInLogMessageText("tell DominoMeter <command>");
		AddInLogMessageText("   quit       Unload DominoMeter");
		AddInLogMessageText("   help       Show help information (or -h)");
		AddInLogMessageText("   info       Show version and more of DominoMeter (or -i)");
		AddInLogMessageText("   update     Check for a new version (or -u)");
		AddInLogMessageText("   report     Send report (or -r)");
		AddInLogMessageText("   config     Reload config for addin (or -c)");
		AddInLogMessageText("Copyright (C) Prominic.NET, Inc. 2020" + (year > 2020 ? " - " + Integer.toString(year) : ""));
		AddInLogMessageText("See https://dominometer.com for more details.");
	}

	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 *
	 * @param	message		Message to be displayed
	 */
	private final void logMessage(String message) {
		fileLogger.info(message);
		AddInLogMessageText(this.JADDIN_NAME + ": " + message, 0);
	}

	/**
	 * Create the Domino task status line which is shown in <code>"show tasks"</code> command.
	 *
	 * Note: This method is also called by the JAddinThread and the user add-in
	 *
	 * @param	name	Name of task
	 * @return	Domino task ID
	 */
	public final int createAddinStatusLine(String name) {
		return (AddInCreateStatusLine(name));
	}

	/**
	 * Delete the Domino task status line.
	 *
	 * Note: This method is also called by the JAddinThread and the user add-in
	 *
	 * @param	id	Domino task id
	 */
	public final void deleteAddinStatusLine(int id) {
		if (id != 0)
			AddInDeleteStatusLine(id);
	}

	/**
	 * Set the text of the add-in which is shown in command <code>"show tasks"</code>.
	 *
	 * @param	text	Text to be set
	 */
	private final void setAddinState(String text) {

		if (this.dominoTaskID == 0)
			return;

		if (!text.equals(this.bufState)) {
			AddInSetStatusLine(this.dominoTaskID, text);
			bufState = text;
		}
	}

	@Override
	public void termThread() {
		logMessage("MainThread: termThread");

		terminate();
		super.termThread();
	}

	/**
	 * Terminate all variables
	 */
	private void terminate() {
		try {
			terminateReportThread();

			AddInDeleteStatusLine(dominoTaskID);

			if (this.ab != null) {
				this.ab.recycle();
				this.ab = null;
			}
			if (this.session != null) {
				this.session.recycle();
				this.session = null;
			}
			if (this.mq != null) {
				this.mq.close(0);
				this.mq = null;
			}

			logMessage("UNLOADED (OK) " + version);
		} catch (NotesException e) {
			fileLogger.info("finalize");
			logMessage("UNLOADED (**FAILED**) " + version);
		}
	}
	
    /**
     * Exception total (child + main threads)
     */
    synchronized static public void incrementExceptionTotal() {
		total_exception_count++;
    }
    
    /**
     * Return counter from child thread
     */
    synchronized static public long getExceptionTotal() {
		return total_exception_count;
    }

	private void terminateReportThread() {
		if (thread == null || !thread.isAlive()) return;

		logMessage("ReportThread: is alive, stopping...");

		long counter = 0;
		thread.interrupt();
		while(thread.isAlive()) {
			try {
				counter++;
				sleep(100);

				if (counter % 50 == 0) {
					logMessage("ReportThread: is stopping, please wait...");
				}
			} catch (InterruptedException e) {
				incrementExceptionTotal();
				fileLogger.severe(e);
				e.printStackTrace();
			}

			// 1 hour (but Domino has setting to shut down after 5 mins by default)
			if (counter > 36000) {
				logMessage("ReportThread: forcing to quit after 2 hours");
				return;
			}
		}

		logMessage("ReportThread: has been stopped nicely");
	}
}
