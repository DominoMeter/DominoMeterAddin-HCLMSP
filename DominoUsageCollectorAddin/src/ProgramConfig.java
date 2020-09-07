import java.util.Date;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DateTime;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class ProgramConfig {
	private final static String COMMENT_PROMINIC = "[PROMINIC.NET] DominoUsageCollectorAddin (created automatically). Please do not delete it.\nPlease contact Support@Prominic.NET with any questions about this program document.";

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
		View view = database.getView("($Programs)");
		Document doc = view.getFirstDocument();
		Document nextDoc = null;
		Document program = null;
		while (doc != null) {
			nextDoc = view.getNextDocument(doc);

			if (isDominoUsageCollectorAddin(doc, true)) {
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

		return program;
	}

	/*
	 * Enable/Disable program document "Run once at specific time"
	 * Used to run a new version of DominoUsageCollectorAddin
	 */
	public boolean setupRunOnce(boolean enable) throws NotesException {
		Document doc = updateOnce(this.getAddressBook(), 20, enable);

		return doc != null;
	}

	/*
	 * Create/Update/Enable/Disable "Run once at specific time"
	 * Used when we want to load a new version of DominoUsageCollectoAddin.
	 */
	private Document updateOnce(Database database, int adjustMinutes, boolean enabled) throws NotesException {
		View view = database.getView("($Programs)");
		Document doc = view.getFirstDocument();
		Document nextDoc = null;
		Document program = null;
		while (doc != null) {
			nextDoc = view.getNextDocument(doc);

			if (isDominoUsageCollectorAddin(doc, false)) {
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

		String sEnabled = enabled ? "1" : "0";
		if (program == null) {
			System.out.println("updateOnce - create program document. Enable: " + sEnabled);
			program = createProgram(database, sEnabled);
		}
		else {
			if (!sEnabled.equalsIgnoreCase(program.getItemValueString("Enabled"))) {
				program.replaceItemValue("Enabled", sEnabled);
				System.out.println("updateOnce - update program document. Enable: " + sEnabled);
			}
		}

		// this is only value we need to modify
		if (adjustMinutes > 0) {
			Date jDate = new Date();
			DateTime dt = m_session.createDateTime(jDate);
			dt.adjustMinute(adjustMinutes);
			program.replaceItemValue("Schedule", dt);
			System.out.println("updateOnce - update program document. Schedule: " + dt.getLocalTime());
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
	private boolean isDominoUsageCollectorAddin(Document doc, boolean scheduled) throws NotesException {
		return doc.getItemValueString("CmdLine").toLowerCase().contains("dominousagecollectoraddin") && "2".equalsIgnoreCase(doc.getItemValueString("Enabled"));
	}

}
