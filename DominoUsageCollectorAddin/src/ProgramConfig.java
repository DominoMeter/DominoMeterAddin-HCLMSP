import java.util.Date;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DateTime;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class ProgramConfig {
	private final static String COMMENT_PROMINIC = "[PROMINIC.NET] DominoUsageCollectorAddin (created automatically). Please do not delete it. Please contact Support@Prominic.NET with any questions about this program document.";

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

			if (isDominoUsageCollectorAddin(doc) && "2".equalsIgnoreCase(doc.getItemValueString("Enabled"))) {
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
		Document doc = updateOnce(this.getAddressBook(), 20, "1");

		return doc != null;
	}

	/*
	 * Disable program document "Run once at specific time"
	 * Used to run a new version of DominoUsageCollectorAddin
	 */
	public boolean disableRunOnce() throws NotesException {
		Document doc = updateOnce(this.getAddressBook(), 0, "0");

		return doc != null;
	}


	/*
	 * Create/Update/Enable/Disable "Run once at specific time"
	 * Used when we want to load a new version of DominoUsageCollectoAddin.
	 */
	private Document updateOnce(Database database, int adjustMinutes, String enabled) throws NotesException {
		System.out.println("updateOnce - " + database.getTitle());

		View view = database.getView("($Programs)");
		Document doc = view.getFirstDocument();
		Document nextDoc = null;
		Document program = null;
		while (doc != null) {
			nextDoc = view.getNextDocument(doc);

			if (isDominoUsageCollectorAddin(doc)) {
				String pEnabled = doc.getItemValueString("Enabled");
				if ("1".equalsIgnoreCase(pEnabled) || "0".equalsIgnoreCase(pEnabled)) {
					if (program == null) {
						program = doc;
					}
					else {
						doc.remove(true);
						System.out.println("updateOnce - deleted program document (dupilcated)");
					}
				}
			}

			doc = nextDoc;
		}

		if (program == null) {
			System.out.println("updateOnce - create program document. Enable: " + enabled);
			program = createProgram(database, enabled);
		}
		else {
			System.out.println("updateOnce - update program document. Enable: " + enabled);
			program.replaceItemValue("Enabled", enabled);
		}

		// this is only value we need to modify
		if (adjustMinutes > 0) {
			Date jDate = new Date();
			DateTime dt = m_session.createDateTime(jDate);
			dt.adjustMinute(adjustMinutes);
			program.replaceItemValue("Schedule", dt);
		}

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
	 * Check if Program document is DominoUsageCollectorAddin
	 */
	private boolean isDominoUsageCollectorAddin(Document doc) throws NotesException {
		return doc.getItemValueString("CmdLine").toLowerCase().contains("dominousagecollectoraddin");
	}

}
