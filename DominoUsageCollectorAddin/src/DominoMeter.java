import java.util.Calendar;

import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.notes.addins.JavaServerAddin;
import prominic.dm.api.Log;
import prominic.dm.report.Report;
import prominic.dm.update.ProgramConfig;
import prominic.dm.update.UpdateRobot;

public class DominoMeter extends JavaServerAddin {
	final String			JADDIN_NAME				= "DominoMeter";
	final String			JADDIN_VERSION			= "17";
	final String			JADDIN_DATE				= "2020-09-18 23:01 CET";
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

		// 4. go
		runLoop();
	}

	private void runLoop() {
		// Set the Java thread name to the class name (default would be "Thread-n")		
		this.setName(JADDIN_NAME);

		// Create the status line showed in 'Show Task' console command
		this.dominoTaskID = AddInCreateStatusLine(this.JADDIN_NAME + " loaded");

		try {
			Session session = NotesFactory.createSession();
			String endpoint = args[0];

			logMessage(" version " + this.JADDIN_VERSION);
			logMessage(" endpoint: " + this.args[0]);
			logMessage(" timer: " + JADDIN_TIMER);

			ProgramConfig pc = new ProgramConfig(session, endpoint);
			pc.setupServerStartUp(JADDIN_NAME);			// create server-startup run program
			pc.setupRunOnce(JADDIN_NAME, false);		// disable one-time run program

			Report dc = new Report(session, endpoint, JADDIN_VERSION);

			int curHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
			int hourEvent = curHour - 1;

			UpdateRobot ur = new UpdateRobot();
			while (this.addInRunning()) {
				JavaServerAddin.sleep(JADDIN_TIMER);

				if (hourEvent != curHour) {
					String version = this.JADDIN_NAME + "-" + JADDIN_VERSION + ".jar";
					String newAddinFile = ur.applyNewVersion(session, endpoint, version);
					if (!newAddinFile.isEmpty()) {
						Log.send(session, endpoint, JADDIN_NAME + " - will be unloaded for upgrade", "New version " + newAddinFile + " will start shortly (~20 mins)", 2);
						int pos = newAddinFile.indexOf("-");
						String newAddinName = newAddinFile.substring(0, pos);
						pc.setupRunOnce(newAddinName, true);
						pc.setupServerStartUp(newAddinName);							
						this.stopAddin();
					}
				}

				if (hourEvent != curHour) {
					if (!dc.send()) {
						this.logMessage("Data has not been sent to prominic");
						Log.send(session, endpoint, "New Report (failed)", "Detailed report has been not provided (faield)", 4);
					}	
				}

				if (hourEvent != curHour) {
					hourEvent = curHour;
				}

				curHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
			}

			logMessage("UNLOADED (OK) " + JADDIN_NAME + " " + this.JADDIN_VERSION);
		} catch(Exception e) {
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
	private final void setAddinState(String text) {
		if (this.dominoTaskID == 0) return;
		AddInSetStatusLine(this.dominoTaskID, text);
	}

}
