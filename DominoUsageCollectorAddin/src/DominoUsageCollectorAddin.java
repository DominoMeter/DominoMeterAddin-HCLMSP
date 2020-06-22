import lotus.notes.addins.JavaServerAddin;

public class DominoUsageCollectorAddin extends JavaServerAddin {
	private String[] 		args 					= null;

	// constructor if parameters are provided
	public DominoUsageCollectorAddin(String[] args) {
		this.args = args;
	}

	// constructor if no parameters
	public DominoUsageCollectorAddin() {
	}

	public void runNotes()
	{
		AddInLogMessageText("Hello DominoUsageCollectorAddin", 0);
	}

}
