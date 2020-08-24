

import java.util.StringJoiner;

import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.notes.addins.JavaServerAddin;

public class DominoUsageCollectorAddin extends JavaServerAddin {
	// Constants
	final String			JADDIN_NAME				= "DominoUsageCollectorAddin";
	final String			JADDIN_VERSION			= "46";
	final String			JADDIN_DATE				= "2020-08-25";
	final long				JADDIN_TIMER			= 20000;	// 20 seconds; 3600000 - 1 hour
	
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
			logMessage("Parameters: Endpoints (string)");
			return;
		}
		
		this.setName(JADDIN_NAME);
		
		// Create the status line showed in 'Show Task' console command
		this.dominoTaskID = createAddinStatusLine(this.JADDIN_NAME + " Main Task");
		
		// Set the initial state
		setAddinState("Initialization in progress");
		
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

			DataCollector dc = new DataCollector(session, endpoint, server, this.JADDIN_VERSION);
			
			ProgramConfig pc = new ProgramConfig(session, endpoint);
			pc.setupServerStartUp();	// run addin when server restarts
			pc.setupOnce(false, 0);		// disable one-time run (must be enabled when new version released)
			
			UpdateRobot ur = new UpdateRobot();
			while (this.addInRunning()) {
				setAddinState("Idle");
				JavaServerAddin.sleep(JADDIN_TIMER);

				setAddinState("Sending data to prominic.net");
				dc.send();
				
				setAddinState("Checking for a new version of DominoUsageCollectorAddin");
				boolean res = ur.applyNewVersion(session, endpoint, server, JADDIN_VERSION);
				if (res) {
					pc.setupOnce(true, 5);
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
	 * Set the text of the add-in which is shown in command <code>"show tasks"</code>.
	 * 
	 * @param	text	Text to be set
	 */
	private final void setAddinState(String text) {
		
		if (this.dominoTaskID == 0)
			return;
		
		AddInSetStatusLine(this.dominoTaskID, text);
	}
}
