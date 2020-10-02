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
	final String			JADDIN_VERSION			= "54";
	final String			JADDIN_DATE				= "2020-10-02 22:40 CET";
	final long				JADDIN_TIMER			= 10000;	// 10000 - 10 seconds; 60000 - 1 minute; 3600000 - 1 hour;

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

	private String 			server					= "";
	private String 			endpoint				= "";
	private String 			version					= "";

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
			if (!Ping.isLive(endpoint, server)) {
				Log.sendError(server, endpoint, "connection (*FAILED*) with: " + endpoint, "");
				logMessage("connection (*FAILED*) with: " + endpoint);
				return;
			}

			MessageQueue mq = new MessageQueue();
			int messageQueueState = mq.create(qName, 0, 0);	// use like MQCreate in API
			if (messageQueueState == MessageQueue.ERR_DUPLICATE_MQ) {
				logMessage(this.JADDIN_NAME + " task is already running");
				terminate(session, ab, mq);
				return;
			}

			if (messageQueueState != MessageQueue.NOERROR) {
				logMessage("Unable to create the Domino message queue");
				terminate(session, ab, mq);
				return;
			}

			if (mq.open(qName, 0) != MessageQueue.NOERROR) {
				logMessage("Unable to open Domino message queue");
				terminate(session, ab, mq);
				return;
			}

			logMessage("connection (OK) with: " + endpoint);
			logMessage("version " + this.JADDIN_VERSION);
			logMessage("date " + this.JADDIN_DATE);
			logMessage("endpoint: " + endpoint);

			ProgramConfig pc = new ProgramConfig(server, endpoint, JADDIN_NAME);
			pc.setState(ab, ProgramConfig.LOAD);		// set program documents in LOAD state

			sendReport(session, ab);

			UpdateRobot ur = new UpdateRobot();
			updateVersion(session, ab, ur, pc);
			ur.cleanOldVersions(server, endpoint, version);

			while (this.addInRunning() && (messageQueueState != MessageQueue.ERR_MQ_QUITTING)) {
				/* gives control to other task in non preemptive os*/
				OSPreemptOccasionally();

				setAddinState("Idle");

				messageQueueState = mq.get(qBuffer, MQ_MAX_MSGSIZE, MessageQueue.MQ_WAIT_FOR_MSG, 1000);
				String cmd = qBuffer.toString().trim();
				if (!cmd.isEmpty()) {
					if ("-h".equals(cmd) || "help".equals(cmd)) {
						showHelp();
					}
					else if ("-v".equals(cmd) || "version".equals(cmd)) {
						logMessage("Version: " + JADDIN_VERSION + ". Build date: " + JADDIN_DATE);
					}
					else if ("-u".equals(cmd) || "update".equals(cmd)) {
						boolean res = updateVersion(session, ab, ur, pc);
						if (!res) logMessage("version is up to date");
					}
					else if ("-r".equals(cmd) || "report".equals(cmd)) {
						boolean res = sendReport(session, ab);
						logMessage(res ? "report (OK)" : "report (*FAILED*)");
					}
				}

				if (this.AddInHasMinutesElapsed(60)) {
					sendReport(session, ab);
					updateVersion(session, ab, ur, pc);
				}				
			}

			terminate(session, ab, mq);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	private boolean updateVersion(Session session, Database ab, UpdateRobot ur, ProgramConfig pc) {
		setAddinState("UpdateRobot");
		String newAddinFile = ur.applyNewVersion(session, server, endpoint, version);
		if (newAddinFile.isEmpty()) return false;

		pc.setState(ab, ProgramConfig.UNLOAD);		// set program documents in UNLOAD state
		Log.sendLog(server, endpoint, version + " - will be unloaded to upgrade to a newer version: " + newAddinFile, "New version " + newAddinFile + " should start in ~20 mins");
		this.stopAddin();

		return true;
	}

	private boolean sendReport(Session session, Database ab) {
		setAddinState("Report");
		Report dc = new Report();
		boolean res= dc.send(session, ab, server, endpoint, version);

		if (!res) {
			Log.sendError(server, endpoint, "report has not been sent", "");
		}

		return res;
	}

	private void showHelp() {
		int year = Calendar.getInstance().get(Calendar.YEAR);
		logMessage("*** Usage ***");
		AddInLogMessageText("[LOAD]");
		AddInLogMessageText("	load runjava DominoMeter <endpoint>");
		AddInLogMessageText("[tell DominoMeter]");
		AddInLogMessageText("	quit       Unload DominoMeter");
		AddInLogMessageText("	help       Show help information (or -h)");
		AddInLogMessageText("	version    Show version of DominoMeter (or -v)");
		AddInLogMessageText("	update     Check for a new version (or -u)");
		AddInLogMessageText("	report     Send report (or -r)");
		AddInLogMessageText("Copyright (C) Prominic.NET, Inc. 2020" + (year > 2020 ? " - " + Integer.toString(year) : ""));
		AddInLogMessageText("See https://dominometer.com for more details.");
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
	 * Terminate all variables
	 */
	private void terminate(Session session, Database ab, MessageQueue mq) {
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
