import java.util.Calendar;

import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.Database;
import lotus.notes.addins.JavaServerAddin;

import prominic.dm.api.Log;
import prominic.dm.api.Ping;
import prominic.dm.report.Report;
import prominic.dm.update.ProgramConfig;
import prominic.dm.update.UpdateRobot;

public class DominoMeter extends JavaServerAddin {
	final String			JADDIN_NAME				= "DominoMeter";
	final String			JADDIN_VERSION			= "49";
	final String			JADDIN_DATE				= "2020-10-01 15:30 CET";
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

		// Set the Java thread name to the class name (default would be "Thread-n")		
		this.setName(JADDIN_NAME);
		this.dominoTaskID = createAddinStatusLine(this.JADDIN_NAME);
		setAddinState("Initializing");
		
		runLoop();
	}

	private void runLoop() {
		Session session = null;
		Database ab = null;
		String endpoint = "";
		String server = "";

		try {
			session = NotesFactory.createSession();
			ab = session.getDatabase(session.getServerName(), "names.nsf");
			endpoint = args[0];
			server = session.getServerName();
			
			// check if connection could be established
			if (!Ping.isLive(endpoint, server)) {
				Log.sendError(server, endpoint, "connection (*FAILED*) with: " + endpoint, "");
				logMessage("connection (*FAILED*) with: " + endpoint);
				return;
			}

			logMessage("connection (OK) with: " + args[0]);
			logMessage("version " + this.JADDIN_VERSION);
			logMessage("date " + this.JADDIN_DATE);
			logMessage("endpoint: " + endpoint);
			logMessage("timer: " + JADDIN_TIMER);

			String version = this.JADDIN_NAME + "-" + JADDIN_VERSION + ".jar";
			Log.sendLog(server, endpoint, "started: " + version, "");

			ProgramConfig pc = new ProgramConfig(session.getServerName(), endpoint);
			pc.setupServerStartUp(ab, JADDIN_NAME);			// create server-startup run program
			pc.setupRunOnce(ab, JADDIN_NAME, false);		// disable one-time run program

			int curHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
			int hourEvent = curHour - 1;

			UpdateRobot ur = new UpdateRobot();
			ur.cleanOldVersions(server, endpoint, version);

			while (this.addInRunning()) {
				setAddinState("Idle");
				waitMilliSeconds(JADDIN_TIMER);

				if (hourEvent != curHour) {
					setAddinState("UpdateRobot");
					String newAddinFile = ur.applyNewVersion(session, server, endpoint, version);
					if (!newAddinFile.isEmpty()) {
						Log.sendLog(server, endpoint, version + " - will be unloaded to upgrade to a newer version: " + newAddinFile, "New version " + newAddinFile + " should start in ~20 mins");
						int pos = newAddinFile.indexOf("-");
						String newAddinName = newAddinFile.substring(0, pos);
						pc.setupRunOnce(ab, newAddinName, true);
						pc.setupServerStartUp(ab, newAddinName);				
						this.stopAddin();
					}
				}

				if (hourEvent != curHour) {
					Report dc = new Report();
					setAddinState("Report");
					if (this.addInRunning() && !dc.send(session, ab, server, endpoint, version)) {
						Log.sendError(server, endpoint, "report has not been sent", "");
					}
				}

				if (hourEvent != curHour) {
					hourEvent = curHour;
				}

				curHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
			}

			setAddinState("Terminate");
			Log.sendLog(server, endpoint, "unloaded " + version, "");
			logMessage("UNLOADED (OK) " + version);

			ab.recycle();
			session.recycle();
		} catch(Exception e) {
			if (session != null && !endpoint.isEmpty()) {
				Log.sendError(server, endpoint, "stopped to work", e.getLocalizedMessage());	
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
}
