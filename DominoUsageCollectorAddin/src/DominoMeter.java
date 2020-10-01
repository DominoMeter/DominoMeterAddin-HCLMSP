import java.util.Calendar;

import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;

import prominic.dm.api.Log;
import prominic.dm.api.Ping;
import prominic.dm.report.Report;
import prominic.dm.update.ProgramConfig;
import prominic.dm.update.UpdateRobot;

public class DominoMeter extends JavaServerAddin {
	final String			JADDIN_NAME				= "DominoMeter";
	final String			JADDIN_VERSION			= "50";
	final String			JADDIN_DATE				= "2020-10-01 22:40 CET";
	final long				JADDIN_TIMER			= 10000;	// 10000 - 10 seconds; 60000 - 1 minute; 3600000 - 1 hour;

	// Message Queue name for this Addin (normally uppercase);
	// MSG_Q_PREFIX is defined in JavaServerAddin.class
	final String 			qName 					= MSG_Q_PREFIX + JADDIN_NAME.toUpperCase();

	// MessageQueue Constants
	public static final int MQ_MAX_MSGSIZE = 1024;
	// this is already defined (should be = 1):
	public static final int	MQ_WAIT_FOR_MSG = MessageQueue.MQ_WAIT_FOR_MSG;

	// MessageQueue errors:
	public static final int PKG_MISC = 0x0400;
	public static final int ERR_MQ_POOLFULL = PKG_MISC+94;
	public static final int ERR_MQ_TIMEOUT = PKG_MISC+95;
	public static final int ERR_MQSCAN_ABORT = PKG_MISC+96;
	public static final int ERR_DUPLICATE_MQ = PKG_MISC+97;
	public static final int ERR_NO_SUCH_MQ = PKG_MISC+98;
	public static final int ERR_MQ_EXCEEDED_QUOTA = PKG_MISC+99;
	public static final int ERR_MQ_EMPTY = PKG_MISC+100;
	public static final int ERR_MQ_BFR_TOO_SMALL = PKG_MISC+101;
	public static final int ERR_MQ_QUITTING = PKG_MISC+102;

	// Instance variables
	private String[] 		args 					= null;
	private int 			dominoTaskID			= 0;

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
		Session session = null;
		Database ab = null;
		String endpoint = "";
		String server = "";
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
			String version = this.JADDIN_NAME + "-" + JADDIN_VERSION + ".jar";

			// check if connection could be established
			if (!Ping.isLive(endpoint, server)) {
				Log.sendError(server, endpoint, "connection (*FAILED*) with: " + endpoint, "");
				logMessage("connection (*FAILED*) with: " + endpoint);
				return;
			}

			MessageQueue mq = new MessageQueue();
			int messageQueueState = mq.create(qName, 0, 0);	// use like MQCreate in API
			if (messageQueueState == MessageQueue.ERR_DUPLICATE_MQ) {
				logMessage(this.JADDIN_NAME + " task is already running");
				terminate(session, ab, mq, version);
				return;
			}
			
			if (messageQueueState != MessageQueue.NOERROR) {
				logMessage("Unable to create the Domino message queue");
				terminate(session, ab, mq, version);
				return;
			}
			
			if (mq.open(qName, 0) != MessageQueue.NOERROR) {
				logMessage("Unable to open Domino message queue");
				terminate(session, ab, mq, version);
				return;
			}
			
			logMessage("connection (OK) with: " + endpoint);
			logMessage("version " + this.JADDIN_VERSION);
			logMessage("date " + this.JADDIN_DATE);
			logMessage("endpoint: " + endpoint);

			Log.sendLog(server, endpoint, "started: " + version, "");

			ProgramConfig pc = new ProgramConfig(server, endpoint, JADDIN_NAME);
			pc.setState(ab, ProgramConfig.LOAD);		// set program documents in LOAD state

			UpdateRobot ur = new UpdateRobot();
			ur.cleanOldVersions(server, endpoint, version);

			while (this.addInRunning() && (messageQueueState != ERR_MQ_QUITTING)) {
				/* gives control to other task in non preemprive os */
				OSPreemptOccasionally();

				setAddinState("Idle");

				// wait half a second (500 milliseconds) for a message,
				// then check for other conditions -- use 0 as the last
				// parameter to wait forever. You can use a longer interval
				// if you're not checking for any of the AddInElapsed
				// conditions -- otherwise you should keep the timeout to
				// a second or less (see comments below)
				messageQueueState = mq.get(qBuffer, MQ_MAX_MSGSIZE, MessageQueue.MQ_WAIT_FOR_MSG, 1000);
				
				String cmd = qBuffer.toString().trim();
				if (!cmd.isEmpty()) {
					resolveCmd(cmd);
				}
				
				if (this.AddInHasMinutesElapsed(60)) {
					setAddinState("Report");
					Report dc = new Report();
					if (dc.send(session, ab, server, endpoint, version)) {
						Log.sendLog(server, endpoint, "report has been sent", "");
					}
					else {
						Log.sendError(server, endpoint, "report has not been sent", "");
					}
					
					setAddinState("UpdateRobot");
					String newAddinFile = ur.applyNewVersion(session, server, endpoint, version);
					if (newAddinFile.isEmpty()) {
						Log.sendLog(server, endpoint, version + " is up to date", "");
					}
					else {
						pc.setState(ab, ProgramConfig.UNLOAD);		// set program documents in UNLOAD state
						Log.sendLog(server, endpoint, version + " - will be unloaded to upgrade to a newer version: " + newAddinFile, "New version " + newAddinFile + " should start in ~20 mins");
						this.stopAddin();
					}
				}
			}

			terminate(session, ab, mq, version);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private void resolveCmd(String cmd) {
		if ("-h".equals(cmd) || "help".equals(cmd)) {
			int year = Calendar.getInstance().get(Calendar.YEAR);
			logMessage("*** Usage ***");
			AddInLogMessageText("[LOAD]");
			AddInLogMessageText("   load runjava DominoMeter <endpoint>");
			AddInLogMessageText("[TELL DominoMeter]");
			AddInLogMessageText("	quit       Unload DominoMeter");
			AddInLogMessageText("	help       Show help information (or -h)");
			AddInLogMessageText("	version    Show version of DominoMeter (or -v)");
			AddInLogMessageText("Copyright (C) Prominic.NET, Inc. 2020" + (year > 2020 ? " - " + Integer.toString(year) : ""));
			AddInLogMessageText("See https://dominometer.com for more details.");
		}

		// 3. version
		if ("-v".equals(cmd) || "version".equals(cmd)) {
			logMessage("Version: " + JADDIN_VERSION + ". Build date: " + JADDIN_DATE);
			return;
		}

	}

	/**
	 * This method is called by the Java runtime during garbage collection.
	 */
	public void finalize() {
		// Call the superclass method
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
	 * Delay the execution of the current thread.
	 * 
	 * Note: This method is also called by the JAddinThread and the user add-in
	 * 
	 * @param	sleepTime	Delay time in milliseconds
	 */
	public final void waitMilliSeconds(long sleepTime) {
		try {
			Thread.sleep(sleepTime);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Terminate all variables
	 */
	private void terminate(Session session, Database ab, MessageQueue mq, String version) {
		try {
			ab.recycle();
			session.recycle();
			
			setAddinState("Terminating...");
			mq.close(0);
			AddInDeleteStatusLine(dominoTaskID);
			logMessage("UNLOADED (OK) " + version);
		} catch (NotesException e) {}
	}

}
