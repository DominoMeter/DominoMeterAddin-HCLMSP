import java.util.Calendar;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;
import prominic.dm.api.Config;
import prominic.dm.api.Log;
import prominic.dm.api.Ping;
import prominic.dm.report.Report;
import prominic.dm.update.ProgramConfig;
import prominic.dm.update.UpdateRobot;

public class DominoMeter extends JavaServerAddin {
	final String			JADDIN_NAME				= "DominoMeter";
	final String			JADDIN_VERSION			= "81";
	final String			JADDIN_DATE				= "2020-10-30 15:30 CET";

	// Message Queue name for this Addin (normally uppercase);
	// MSG_Q_PREFIX is defined in JavaServerAddin.class
	final String 			qName 					= MSG_Q_PREFIX + JADDIN_NAME.toUpperCase();

	// MessageQueue Constants
	public static final int MQ_MAX_MSGSIZE = 1024;
	// this is already defined (should be = 1):
	public static final int	MQ_WAIT_FOR_MSG = MessageQueue.MQ_WAIT_FOR_MSG;

	// Instance variables
	private String[] 		args 					= null;
	private int 			dominoTaskID			= 0;

	MessageQueue 			mq						= null;
	Session 				session 				= null;
	Database 				ab 						= null;
	private int				interval				= 60;
	private String 			server					= "";
	private String 			endpoint				= "";
	private String 			version					= "";
	private int				failedCounter			= 0;

	// constructor if parameters are provided
	public DominoMeter(String[] args) {
		this.args = args;
	}

	// constructor if no parameters
	public DominoMeter() {
	}

	public void runNotes() {
		// 1. minimal system requirement
		try {
			String jvmVersion = System.getProperty("java.specification.version", "0");
			if (Double.parseDouble(jvmVersion) < 1.6) {
				logMessage("Current Java Virtual Machine version " + jvmVersion + " must be 1.6 or higher");
				return;
			}
		} catch (Exception e) {
			logMessage("Unable to detect the Java Virtual Machine version number: " + e.getMessage());
			return;
		}

		runLoop();
	}

	@SuppressWarnings("deprecation")
	private void runLoop() {
		StringBuffer qBuffer = new StringBuffer(1024);

		try {
			// Set the Java thread name to the class name (default would be "Thread-n")		
			this.setName(JADDIN_NAME);
			this.dominoTaskID = createAddinStatusLine(this.JADDIN_NAME);
			setAddinState("Initializing");

			session = NotesFactory.createSession();
			ab = session.getDatabase(session.getServerName(), "names.nsf");
			endpoint = args[0];
			if (endpoint.equalsIgnoreCase("dev")) {
				endpoint = "https://prominic-dev.dominometer.com/duca.nsf";
			}
			else if(endpoint.equalsIgnoreCase("prod")) {
				endpoint = "https://prominic.dominometer.com/duca.nsf";
			}
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

			Config config = new Config();
			loadConfig(config);
			showInfo();

			ProgramConfig pc = new ProgramConfig(server, endpoint, JADDIN_NAME);
			pc.setState(ab, ProgramConfig.LOAD);		// set program documents in LOAD state

			if (check) sendReport();	

			UpdateRobot ur = new UpdateRobot();
			if (check) updateVersion(ur, pc, config.getJAR());	
			ur.cleanOldVersions(server, endpoint, version);

			while (this.addInRunning() && (messageQueueState != MessageQueue.ERR_MQ_QUITTING)) {
				/* gives control to other task in non preemptive os*/
				OSPreemptOccasionally();

				setAddinState("Idle");

				// check for command from console
				messageQueueState = mq.get(qBuffer, MQ_MAX_MSGSIZE, MessageQueue.MQ_WAIT_FOR_MSG, 1000);
				resolveMessageQueueState(qBuffer, ur, pc, config);

				if (this.AddInHasMinutesElapsed(interval)) {
					if (checkConnection()) {
						loadConfig(config);
						sendReport();
						updateVersion(ur, pc, config.getJAR());	
					}
				}				
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	private boolean checkConnection() {
		Ping ping = new Ping();
		if (ping.check(endpoint, server)) {
			failedCounter = 0;
			return true;
		};

		failedCounter++;

		logMessage("connection (*FAILED*) with: " + endpoint);
		logMessage("> " + ping.getLastError());
		logMessage("> counter: " + Integer.toString(failedCounter));

		if (failedCounter > 10) {
			this.stopAddin();		
		}
		return false;
	}

	private void resolveMessageQueueState(StringBuffer qBuffer, UpdateRobot ur, ProgramConfig pc, Config config) {
		String cmd = qBuffer.toString().trim();
		if (cmd.isEmpty()) return;

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
			boolean res = sendReport();
			logMessage(res ? "report (OK)" : "report (*FAILED*)");
		}
		else if ("-c".equals(cmd) || "config".equals(cmd)) {
			boolean res = loadConfig(config);
			logMessage(res ? "updated (OK)" : "updated (*FAILED*)");
		}
	}

	private boolean loadConfig(Config config) {
		boolean res = config.load(endpoint, server);
		if (res) {
			interval = config.getInterval();
		}
		return res;
	}

	private boolean updateVersion(UpdateRobot ur, ProgramConfig pc, String jar) {
		setAddinState("UpdateRobot");
		String newAddinFile = ur.applyNewVersion(session, server, endpoint, jar, version);
		if (newAddinFile.isEmpty()) {
			if (ur.getLastError().length() > 0) {
				Log.sendError(server, endpoint, ur.getLastError(), "");
			}
			return false;
		}

		pc.setState(ab, ProgramConfig.UNLOAD);		// set program documents in UNLOAD state
		Log.sendLog(server, endpoint, version + " - will be unloaded to upgrade to a newer version: " + newAddinFile, "New version " + newAddinFile + " should start in ~20 mins");
		this.stopAddin();

		return true;
	}

	private boolean sendReport() {
		setAddinState("Report");
		Report report = new Report();
		boolean res = report.send(session, ab, server, endpoint, version);

		if (!res) {
			Log.sendError(server, endpoint, report.getLastError(), "");
		}

		return res;
	}

	private void showInfo() {
		logMessage("version " + this.JADDIN_VERSION);
		logMessage("build date " + this.JADDIN_DATE);
		logMessage("endpoint " + this.endpoint);
		logMessage("interval " + Integer.toString(this.interval) + " minutes");
	}

	private void showHelp() {
		int year = Calendar.getInstance().get(Calendar.YEAR);
		logMessage("*** Usage ***");
		AddInLogMessageText("[load]");
		AddInLogMessageText("	load runjava DominoMeter <endpoint>");
		AddInLogMessageText("[tell DominoMeter]");
		AddInLogMessageText("	quit       Unload DominoMeter");
		AddInLogMessageText("	help       Show help information (or -h)");
		AddInLogMessageText("	info	   Show version and more of DominoMeter (or -i)");
		AddInLogMessageText("	update     Check for a new version (or -u)");
		AddInLogMessageText("	report     Send report (or -r)");
		AddInLogMessageText("	config     Reload config for addin (or -c)");
		AddInLogMessageText("Copyright (C) Prominic.NET, Inc. 2020" + (year > 2020 ? " - " + Integer.toString(year) : ""));
		AddInLogMessageText("See https://dominometer.com for more details.");
	}

	/**
	 * This method is called by the Java runtime during garbage collection.
	 */
	public void finalize() {
		terminate();

		super.finalize();
	}

	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 * 
	 * @param	message		Message to be displayed
	 */
	private final void logMessage(String message) {
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

		AddInSetStatusLine(this.dominoTaskID, text);
	}

	/**
	 * Set the text of the add-in which is shown in command <code>"show tasks"</code>.
	 * 
	 * Note: This method is also called by the JAddinThread and the user add-in
	 * 
	 * @param	id		Domino task id
	 * @param	message	Text to be set
	 */
	public final void setAddinState(int id, String message) {

		if (id == 0)
			return;

		AddInSetStatusLine(id, message);
	}

	/**
	 * Terminate all variables
	 */
	private void terminate() {
		try {
			AddInDeleteStatusLine(dominoTaskID);

			if (this.ab != null) {
				this.ab.recycle();
			}
			if (this.session != null) {
				this.session.recycle();
			}
			if (this.mq != null) {
				this.mq.close(0);	
			}

			logMessage("UNLOADED (OK) " + version);
		} catch (NotesException e) {
			logMessage("UNLOADED (**FAILED**) " + version);
		}
	}
}
