import java.util.Date;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.DateTime;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class ProgramConfig {
	private final static String COMMENT_PROMINIC = "[PROMINIC.NET] DominoUsageCollector (created automatically). Please do not delete it.\nPlease contact Support@Prominic.NET with any questions about this program document.";

	private Session m_session = null;
	private Database m_database = null;
	private String m_endpoint = null;

	public ProgramConfig(Session session, String endpoint) {
		m_session = session;
		m_endpoint = endpoint;
	}

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
		String server = m_session.getServerName();
		View view = database.getView("($Programs)");
		DocumentCollection col = view.getAllDocumentsByKey(server, true);
		Document doc = col.getFirstDocument();
		Document nextDoc = null;
		Document program = null;
		while (doc != null) {
			nextDoc = col.getNextDocument(doc);

			if (isDominoUsageCollector(doc) && isProgramAtStartupOnly(doc)) {
				if (program == null) {
					program = doc;
				}
				else {
					doc.remove(true);
					log("duplicate program document detected (at server start up only) - deleted");
				}
			}

			doc = nextDoc;
		}

		boolean toSave = false;
		if (program == null) {
			log("program document (at server start up only) - created");
			program = createProgram(database, "2");
			toSave = true;
		}
		else if (!program.getItemValueString("CmdLine").equalsIgnoreCase("DominoUsageCollector " + m_endpoint)) {
			program.replaceItemValue("CmdLine", "DominoUsageCollector " + m_endpoint);
			toSave = true;
		}

		if (toSave) {
			program.save();
		}

		return program;
	}

	/*
	 * Enable/Disable program document "Run once at specific time"
	 * Used to run a new version of DominoUsageCollector
	 */
	public boolean setupRunOnce(boolean enable) throws NotesException {
		int adjustMinutes = enable ? 20 : 0;
		Document doc = updateOnce(this.getAddressBook(), adjustMinutes, enable);

		return doc != null;
	}

	/*
	 * Create/Update/Enable/Disable "Run once at specific time"
	 * Used when we want to load a new version of DominoUsageCollectoAddin.
	 */
	private Document updateOnce(Database database, int adjustMinutes, boolean enabled) throws NotesException {
		String server = m_session.getServerName();
		View view = database.getView("($Programs)");
		DocumentCollection col = view.getAllDocumentsByKey(server, true);
		Document doc = col.getFirstDocument();
		Document nextDoc = null;
		Document program = null;
		while (doc != null) {
			nextDoc = col.getNextDocument(doc);

			if (isDominoUsageCollector(doc) && !isProgramAtStartupOnly(doc)) {
				if (program == null) {
					program = doc;
				}
				else {
					doc.remove(true);
					log("duplicate program document detected (run at specific time) - deleted");
				}
			}

			doc = nextDoc;
		}

		boolean toSave = true;
		String sEnabled = enabled ? "1" : "0";
		if (program == null) {
			program = createProgram(database, sEnabled);
			log("program document (run at specific time) - created. Enabled: " + sEnabled);
			toSave = true;
		}
		else {
			if (!sEnabled.equalsIgnoreCase(program.getItemValueString("Enabled"))) {
				program.replaceItemValue("Enabled", sEnabled);
				log("program document (run at specific time) - updated. Enabled: " + sEnabled);
				toSave = true;
			}
		}

		// this is only value we need to modify
		if (adjustMinutes > 0) {
			Date jDate = new Date();
			DateTime dt = m_session.createDateTime(jDate);
			dt.adjustMinute(adjustMinutes);
			program.replaceItemValue("Schedule", dt);
			log("program document (run at specific time) - updated. Schedule: " + dt.getLocalTime());
			toSave = true;
		}
		
		if (!program.getItemValueString("CmdLine").equalsIgnoreCase("DominoUsageCollector " + m_endpoint)) {
			program.replaceItemValue("CmdLine", "DominoUsageCollector " + m_endpoint);
			toSave = true;
		}

		if (toSave) {
			program.save();
		}

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
		doc.replaceItemValue("CmdLine", "DominoUsageCollector " + m_endpoint);
		doc.computeWithForm(true, false);

		return doc;
	}

	/*
	 * Check if Program document is DominoUsageCollector
	 */
	private boolean isDominoUsageCollector(Document doc) throws NotesException {
		return doc.getItemValueString("CmdLine").toLowerCase().contains("dominousagecollector");
	}
	
	/*
	 * Check if Program document is set to be scheduled
	 */
	private boolean isProgramAtStartupOnly(Document doc) throws NotesException {
		return "2".equalsIgnoreCase(doc.getItemValueString("Enabled"));
	}

	private void log(Object msg) {
		System.out.println("[ProgramConfig] " + msg.toString());
	}
}
