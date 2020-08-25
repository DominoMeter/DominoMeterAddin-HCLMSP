import java.util.Date;
import java.util.StringJoiner;

import lotus.domino.Database;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.notes.addins.JavaServerAddin;

public class DominoUsageCollectorAddin extends JavaServerAddin {
	final String			JADDIN_NAME				= "DominoUsageCollectorAddin";
	final String			JADDIN_VERSION			= "48";
	final String			JADDIN_DATE				= "2020-08-25";
	final long				JADDIN_TIMER			= 30000;	// 30 seconds; 3600000 - 1 hour

	final int 				PROGRAM_AHEAD_MINUTES 	= 5;

	// Instance variables
	private String[] 		args 					= null;
	private int 			dominoTaskID			= 0;
	private Date			startDate				= null;
	
	// constructor if parameters are provided
	public DominoUsageCollectorAddin(String[] args) {
		this.args = args;
	}

	// constructor if no parameters
	public DominoUsageCollectorAddin() {
	}

	public void runNotes() {
		// First test if the JVM meets the minimum requirement
		try {
			String jvmVersion = System.getProperty("java.specification.version", "0");

			if (Double.parseDouble(jvmVersion) < 1.8) {
				logMessage("Current Java Virtual Machine version " + jvmVersion + " must be 1.8 or higher");
				return;
			}
		} catch (Exception e) {
			logMessage("Unable to detect the Java Virtual Machine version number: " + e.getMessage());
			return;
		}
		
		if (this.args == null || this.args.length < 1) {
			logMessage("Missing required parameter: Endpoints (string)");
			logMessage("Usage: 'Load RunJava DominoUsageCollectorAddin <endpoint>'");
			return;
		}

		// Set the Java thread name to the class name (default would be "Thread-n")		
		this.setName(JADDIN_NAME);
		logMessage(" version " + this.JADDIN_VERSION);
		
		startDate = new Date();
		
		// Create the status line showed in 'Show Task' console command
		this.dominoTaskID = AddInCreateStatusLine(this.JADDIN_NAME + " Main Task");
		logMessage("dominoTaskID = " + Integer.toString(this.dominoTaskID));
		
		// Set the initial state
		setAddinState("Initialization in progress...");
		
		StringJoiner joiner = new StringJoiner("; ");
		for(int i = 0; i < this.args.length; i++) {
			joiner.add(this.args[i]);
		}
		
		try {
			Session session = NotesFactory.createSession();
			String server = session.createName(session.getServerName()).getAbbreviated();
			String endpoint = args[0];
			
			logMessage(" version " + this.JADDIN_VERSION);
			logMessage(" will be called with parameters: " + joiner.toString());
			logMessage(" timer: " + JADDIN_TIMER);

			ProgramConfig pc = new ProgramConfig(session, endpoint);
			pc.setupServerStartUp();	// run addin when server restarts
			pc.setupOnce(false, 0);		// disable one-time run (must be enabled when new version released)

			UpdateRobot ur = new UpdateRobot();
			while (this.addInRunning()) {
				setAddinState("Idle :-)");
				JavaServerAddin.sleep(JADDIN_TIMER);

				setAddinState("Sending data to prominic.net");
				this.sendStat(session, endpoint, server);

				setAddinState("Checking for a new version of DominoUsageCollectorAddin");
				boolean res = ur.applyNewVersion(session, endpoint, server, JADDIN_VERSION);
				if (res) {
					pc.setupOnce(true, PROGRAM_AHEAD_MINUTES);
					this.stopAddin();
				}
				
				logMessage(JADDIN_NAME + " " + this.JADDIN_VERSION);
			}

			logMessage("UNLOADED (OK) " + JADDIN_NAME + " " + this.JADDIN_VERSION);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean sendStat(Session session, String endpoint, String server) throws Exception {
		Database database = session.getDatabase(session.getServerName(), "names.nsf");
		if (database == null) return false;
		
		View view = database.getView("($People)");
		long count = view.getAllEntries().getCount();
		
		String statOS = System.getProperty("os.version", "n/a") + " (" + System.getProperty("os.name", "n/a") + ")";
		String statJavaVersion = System.getProperty("java.version", "n/a") + " (" + System.getProperty("java.vendor", "n/a") + ")";
		
		StringBuffer url = new StringBuffer(endpoint);
		url.append("/config?openagent");
		url.append("&server=" + server);	// key
		
		url.append("&addinVersion=" + this.JADDIN_VERSION);
		url.append("&addinReleaseDate=" + this.JADDIN_DATE);
		url.append("&addinStartDate=" + startDate.toString());

		url.append("&usercount=" + Long.toString(count));
		url.append("&os=" + statOS);
		url.append("&java=" + statJavaVersion);
		
		return RESTClient.sendPOST(url.toString());
	}
	
	/**
	 * This method is called by the Java runtime during garbage collection.
	 */
	public void finalize() {
		logMessage("-- finalize");
		
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
