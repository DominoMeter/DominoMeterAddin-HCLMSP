import java.util.StringJoiner;

import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.notes.addins.JavaServerAddin;

public class DominoUsageCollectorAddin extends JavaServerAddin {
	final String			JADDIN_NAME				= "DominoUsageCollectorAddin";
	final String			JADDIN_VERSION			= "56";
	final String			JADDIN_DATE				= "2020-08-25";
	final long				JADDIN_TIMER			= 30000;	// 30 seconds; 3600000 - 1 hour

	// Instance variables
	private String[] 		args 					= null;
	private int 			dominoTaskID			= 0;
	
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
		
		// Create the status line showed in 'Show Task' console command
		this.dominoTaskID = AddInCreateStatusLine(this.JADDIN_NAME + " Main Task");
		
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
			pc.setupServerStartUp();	// create server-startup run program
			pc.deleteRunOnce();			// delete one-time run program

			DataCollector dc = new DataCollector(session, endpoint, server, JADDIN_VERSION);
			
			UpdateRobot ur = new UpdateRobot();
			while (this.addInRunning()) {
				setAddinState("Idle ");
				JavaServerAddin.sleep(JADDIN_TIMER);

				setAddinState("Sending data to prominic");
				if (!dc.send()) {
					this.logMessage("Data has not been sent to prominic");
				}

				setAddinState("Checking for a new version of DominoUsageCollectorAddin");
				boolean res = ur.applyNewVersion(session, endpoint, server, JADDIN_VERSION);
				if (res) {
					pc.setupRunOnce();	// create one-time run program. It's critical task.
					this.stopAddin();
				}
				
				logMessage(JADDIN_NAME + " " + this.JADDIN_VERSION);
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
