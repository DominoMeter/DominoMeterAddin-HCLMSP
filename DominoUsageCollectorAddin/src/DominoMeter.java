import java.util.Calendar;

import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.notes.addins.JavaServerAddin;
import prominic.dm.api.Log;
import prominic.dm.api.Ping;
import prominic.dm.report.Report;
import prominic.dm.update.ProgramConfig;
import prominic.dm.update.UpdateRobot;

public class DominoMeter extends JavaServerAddin {
	final String			JADDIN_NAME				= "DominoMeter";
	final String			JADDIN_VERSION			= "44";
	final String			JADDIN_DATE				= "2020-09-30 11:55 CET";
	final long				JADDIN_TIMER			= 10000;	// 10000 - 10 seconds; 60000 - 1 minute; 3600000 - 1 hour;

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
		// 2. show help command
		if (this.args == null || this.args.length < 1 || "-h".equalsIgnoreCase(this.args[0]) || "help".equalsIgnoreCase(this.args[0])) {
			int year = Calendar.getInstance().get(Calendar.YEAR);
			logMessage("*** Usage ***");
			AddInLogMessageText("	[Load]:    load runjava DominoMeter <endpoint>");
			AddInLogMessageText("	[Unload]:  tell runjava unload DominoMeter");
			AddInLogMessageText("	[Help]:    tell runjava DominoMeter help (or -h)");
			AddInLogMessageText("	[Version]: tell runjava version (or -v)");
			AddInLogMessageText("Copyright (C) Prominic.NET, Inc. 2020" + (year > 2020 ? " - " + Integer.toString(year) : ""));
			AddInLogMessageText("See http://dominometer.com/ for more details.");
			return;
		}

		// 3. version
		if ("-v".equalsIgnoreCase(this.args[0]) || "version".equalsIgnoreCase(this.args[0])) {
			logMessage("Version: " + JADDIN_VERSION + ". Build date: " + JADDIN_DATE);
			return;
		}
		runLoop();
	}

	private void runLoop() {
		// Set the Java thread name to the class name (default would be "Thread-n")		
		this.setName(JADDIN_NAME);

		// Create the status line showed in 'Show Task' console command
		this.dominoTaskID = AddInCreateStatusLine(this.JADDIN_NAME + " loaded");
		Session session = null;
		String endpoint = "";

		try {
			session = NotesFactory.createSession();
			endpoint = args[0];

			// check if connection could be established
			if (!Ping.isLive(endpoint, session.getServerName())) {
				Log.sendError(session, endpoint, "connection (*FAILED*) with: " + endpoint, "");
				logMessage("connection (*FAILED*) with: " + endpoint);
				return;
			}

			logMessage("connection (OK) with: " + args[0]);
			logMessage("version " + this.JADDIN_VERSION);
			logMessage("endpoint: " + endpoint);
			logMessage("timer: " + JADDIN_TIMER);

			String version = this.JADDIN_NAME + "-" + JADDIN_VERSION + ".jar";
			Log.sendLog(session, endpoint, "started: " + version, "");

			ProgramConfig pc = new ProgramConfig(session, endpoint);
			pc.setupServerStartUp(JADDIN_NAME);			// create server-startup run program
			pc.setupRunOnce(JADDIN_NAME, false);		// disable one-time run program

			int curHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
			int hourEvent = curHour - 1;

			UpdateRobot ur = new UpdateRobot();
			ur.cleanOldVersions(session, endpoint, version);

			while (this.addInRunning()) {
				JavaServerAddin.sleep(JADDIN_TIMER);

				if (hourEvent != curHour) {
					String newAddinFile = ur.applyNewVersion(session, endpoint, version);
					if (!newAddinFile.isEmpty()) {
						Log.sendLog(session, endpoint, version + " - will be unloaded to upgrade to a newer version: " + newAddinFile, "New version " + newAddinFile + " should start in ~20 mins");
						int pos = newAddinFile.indexOf("-");
						String newAddinName = newAddinFile.substring(0, pos);
						pc.setupRunOnce(newAddinName, true);
						pc.setupServerStartUp(newAddinName);				
						this.stopAddin();
					}
				}

				if (hourEvent != curHour && curHour % 2 == 0) {
					Report dc = new Report();
					if (this.addInRunning() && !dc.send(session, endpoint, version)) {
						Log.sendError(session, endpoint, "report has not been sent", "");
					}
				}

				if (hourEvent != curHour) {
					hourEvent = curHour;
				}

				curHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
			}

			Log.sendLog(session, endpoint, "unloaded " + version, "");
			logMessage("UNLOADED (OK) " + version);

			session.recycle();
		} catch(Exception e) {
			if (session != null && !endpoint.isEmpty()) {
				Log.sendError(session, endpoint, "stopped to work", e.getLocalizedMessage());	
			}
			e.printStackTrace();
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
	 * Set the text of the add-in which is shown in command <code>"show tasks"</code>.
	 * 
	 * @param	text	Text to be set
	 */
	@SuppressWarnings("unused")
	private final void setAddinState(String text) {
		if (this.dominoTaskID == 0) return;
		AddInSetStatusLine(this.dominoTaskID, text);
	}
}
