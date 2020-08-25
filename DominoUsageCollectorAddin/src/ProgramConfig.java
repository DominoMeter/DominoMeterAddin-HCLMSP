import java.util.Date;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DateTime;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class ProgramConfig {
	private final static String COMMENT_PROMINIC = "[PROMINIC.NET] DominoUsageCollectorAddin (created automatically). Please do not delete it.";
	
	private Session m_session = null;
	private Database m_database = null;
	private String m_endpoint = null;

	public ProgramConfig(Session session, String endpoint) {
		m_session = session;
		m_endpoint = endpoint;
	}
	
	// TODO: session.getAddressBooks()
	private Database getAddressBook() throws NotesException {
		if (m_database == null) {
			m_database = m_session.getDatabase(m_session.getServerName(), "names.nsf");
		}
		return m_database;
	}
	
	/*
	 * Create/Update program document "At server startup only"
	 * Must be run once when Addin loads
	 */
	public boolean setupServerStartUp() throws NotesException {
		updateServerStartUp(this.getAddressBook());
		return true;
	}
	
	/*
	 * Create "At server startup only" if it does not exist in database
	 * Delete if find duplicates (in case of some error etc).
	 */
	private Document updateServerStartUp(Database database) throws NotesException {
		System.out.println("updateServerStartUp - " + database.getTitle());
		
		View view = database.getView("($Programs)");
		Document doc = view.getFirstDocument();
		Document nextDoc = null;
		Document program = null;
		while (doc != null) {
			nextDoc = view.getNextDocument(doc);

			if (isDominoUsageCollectorAddin(doc, "2")) {
				if (program == null) {
					program = doc;
				}
				else {
					doc.remove(true);
					System.out.println("updateServerStartUp - deleted program document (dupilcated)");
				}
			}
			
			doc = nextDoc;
		}

		if (program == null) {
			System.out.println("updateServerStartUp - create program document");
			program = createProgram(database, "2");
			program.save();
		}
		else {
			System.out.println("updateServerStartUp - program document already exists (no actions)");
		}

		return program;
	}
	
	/*
	 * Create/Update program document "Run once at specific time"
	 * Used to run a new version of DominoUsageCollectorAddin
	 */
	public boolean setupRunOnce() throws NotesException {
		Document doc = updateOnce(this.getAddressBook());
		
		return doc != null;
	}

	/*
	 * Create/Update/Enable "Run once at specific time"
	 * Used when we want to load a new version of DominoUsageCollectoAddin.
	 */
	private Document updateOnce(Database database) throws NotesException {
		System.out.println("updateOnce - " + database.getTitle());

		View view = database.getView("($Programs)");
		Document doc = view.getFirstDocument();
		Document nextDoc = null;
		Document program = null;
		while (doc != null) {
			nextDoc = view.getNextDocument(doc);

			if (isDominoUsageCollectorAddin(doc, "1")) {
				if (program == null) {
					program = doc;
				}
				else {
					doc.remove(true);
					System.out.println("updateOnce - deleted program document (dupilcated)");
				}
			}
			
			doc = nextDoc;
		}

		if (program == null) {
			System.out.println("updateOnce - create program document");
			program = createProgram(database, "1");
		}
		else {
			System.out.println("updateOnce - update program document");
		}

		// this is only value we need to modify
		Date jDate = new Date();
		DateTime dt = m_session.createDateTime(jDate);
		dt.adjustMinute(5);
		program.replaceItemValue("Schedule", dt);

		program.save();

		return program;
	}
	
	/* 
	 * Create stub program
	 */
	private Document createProgram(Database database, String enabled) throws NotesException {
		Document doc = database.createDocument();

		doc.replaceItemValue("Form", "Program");
		doc.replaceItemValue("Type", "Program");
		doc.replaceItemValue("Source", database.getServer());
		doc.replaceItemValue("Program", "runjava");
		doc.replaceItemValue("Enabled", enabled);
		doc.replaceItemValue("Comments", COMMENT_PROMINIC);
		doc.replaceItemValue("CmdLine", "DominoUsageCollectorAddin " + m_endpoint);
		doc.computeWithForm(true, false);
		
		return doc;
	}

	/*
	 * Delete one time run DominoUsageCollectorAddin
	 */
	public void deleteRunOnce() throws NotesException {
		Database database = this.getAddressBook();
		View view = database.getView("($Programs)");
		Document doc = view.getFirstDocument();
		Document nextDoc = null;
		while (doc != null) {
			nextDoc = view.getNextDocument(doc);

			if (isDominoUsageCollectorAddin(doc, "1")) {
				doc.remove(true);
				System.out.println("updateOnce - deleted program document");
			}
			
			doc = nextDoc;
		}
	}

	/*
	 * Check if Program document is DominoUsageCollectorAddin with specific type
	 */
	private boolean isDominoUsageCollectorAddin(Document doc, String enabled) throws NotesException {
		String cmdLine = doc.getItemValueString("CmdLine");
		return cmdLine.toLowerCase().contains("DominoUsageCollectorAddin".toLowerCase()) && doc.getItemValueString("Enabled").equalsIgnoreCase(enabled);
	}

}
