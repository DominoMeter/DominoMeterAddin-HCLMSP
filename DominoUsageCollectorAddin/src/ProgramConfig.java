import java.util.Date;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DateTime;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class ProgramConfig {
	private final static String COMMENT_PROMINIC_STARTUP_ONLY = "[PROMINIC.NET] DominoUsageCollectorAddin - at server startup only. Don't change comment!";
	private final static String COMMENT_PROMINIC_ONCE = "[PROMINIC.NET] DominoUsageCollectorAddin - once. Don't change comment!";
	
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

			String comment = doc.getItemValueString("Comments");
			if (comment.equalsIgnoreCase(COMMENT_PROMINIC_STARTUP_ONLY)) {
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
			program = database.createDocument();
			program.replaceItemValue("Form", "Program");
			program.replaceItemValue("Type", "Program");
			program.replaceItemValue("Source", database.getServer());
			program.replaceItemValue("Program", "runjava");
			program.replaceItemValue("Enabled", "2");
			program.replaceItemValue("Comments", COMMENT_PROMINIC_STARTUP_ONLY);
			program.replaceItemValue("CmdLine", "DominoUsageCollectorAddin " + m_endpoint);
			program.computeWithForm(true, false);
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
	public boolean setupOnce(boolean enabled, int adjustMinutes) throws NotesException {
		updateOnce(this.getAddressBook(), enabled, adjustMinutes);
		return true;
	}

	/*
	 * Create/Update/Enable "Run once at specific time"
	 * Used when we want to load a new version of DominoUsageCollectoAddin.
	 */
	private Document updateOnce(Database database, boolean enabled, int adjustMinutes) throws NotesException {
		System.out.println("updateOnce - " + database.getTitle());

		View view = database.getView("($Programs)");
		Document doc = view.getFirstDocument();
		Document nextDoc = null;
		Document program = null;
		while (doc != null) {
			nextDoc = view.getNextDocument(doc);

			String comment = doc.getItemValueString("Comments");
			if (comment.equalsIgnoreCase(COMMENT_PROMINIC_ONCE)) {
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
			program = database.createDocument();
			program.replaceItemValue("Form", "Program");
			program.replaceItemValue("Type", "Program");
			program.replaceItemValue("Source", database.getServer());
			program.replaceItemValue("Program", "runjava");
			program.replaceItemValue("Comments", COMMENT_PROMINIC_ONCE);
			program.replaceItemValue("CmdLine", "DominoUsageCollectorAddin " + m_endpoint);
			program.computeWithForm(true, false);
		}
		else {
			System.out.println("updateOnce - update program document");
		}
		
		Date jDate = new Date();
		DateTime dt = m_session.createDateTime(jDate);
		dt.adjustMinute(adjustMinutes);
		program.replaceItemValue("Schedule", dt);

		program.replaceItemValue("Enabled", enabled ? "1" : "0");

		program.save();

		return program;
	}
}
