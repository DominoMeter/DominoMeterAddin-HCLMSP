package prominic.dm.update;

import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.DocumentCollection;
import lotus.domino.Document;
import lotus.domino.DateTime;
import lotus.domino.NotesException;

public class ProgramConfig {
	private final static String COMMENT_PROMINIC = "[PROMINIC.NET] DominoMeter (created automatically). Please do not delete it.\nPlease contact Support@Prominic.NET with any questions about this program document.";

	private String m_server;
	private String m_endpoint;

	public ProgramConfig(String server, String endpoint) {
		m_server = server;
		m_endpoint = endpoint;
	}

	/*
	 * Create/Update program document "At server startup only"
	 * Must be run once when Addin loads
	 */
	public boolean setupServerStartUp(Database database, String addinName) throws NotesException {
		Document doc = updateServerStartUp(database, addinName);
		return doc != null;
	}

	/*
	 * Create "At server startup only" if it does not exist in database
	 * Delete if find duplicates (in case of some error etc).
	 */
	private Document updateServerStartUp(Database database, String addinName) throws NotesException {
		View view = database.getView("($Programs)");
		DocumentCollection col = view.getAllDocumentsByKey(m_server, true);
		Document program = null;
		Document doc = col.getFirstDocument();
		while (doc != null) {
			Document nextDoc = col.getNextDocument(doc);

			if (isDominoMeter(doc) && isProgramAtStartupOnly(doc)) {
				if (program == null) {
					program = doc;
				}
				else {
					doc.remove(true);
					log("program document (at server start up only) - deleted (duplicate)");
				}
			}

			doc = nextDoc;
		}

		boolean toSave = false;
		if (program == null) {
			log("program document (at server start up only) - created");
			program = createProgram(database, addinName, "2");
			toSave = true;
		}
		else {
			String val = addinName + " " + m_endpoint;
			if (!val.equalsIgnoreCase(program.getItemValueString("CmdLine"))) {
				program.replaceItemValue("CmdLine", val);
				toSave = true;
				log("program document (at server start up only) - updated. CmdLine: " + val);
			}
		}

		if (toSave) {
			program.save();
		}
		
		col.recycle();
		view.recycle();

		return program;
	}
	
	/*
	 * Enable/Disable program document "Run once at specific time"
	 * Used to run a new version of DominoMeter
	 */
	public boolean setupRunOnce(Database database, String addinName, boolean enable) throws NotesException {
		int adjustMinutes = enable ? 20 : 0;
		Document doc = updateOnce(database, addinName, adjustMinutes, enable);

		return doc != null;
	}

	/*
	 * Create/Update/Enable/Disable "Run once at specific time"
	 * Used when we want to load a new version of DominoUsageCollectoAddin.
	 */
	private Document updateOnce(Database database, String addinName, int adjustMinutes, boolean enabled) throws NotesException {
		View view = database.getView("($Programs)");
		DocumentCollection col = view.getAllDocumentsByKey(m_server, true);
		Document program = null;
		Document doc = col.getFirstDocument();
		while (doc != null) {
			Document nextDoc = col.getNextDocument(doc);

			if (isDominoMeter(doc) && !isProgramAtStartupOnly(doc)) {
				if (program == null) {
					program = doc;
				}
				else {
					doc.remove(true);
					log("program document (run at specific time) - deleted (duplicate)");
				}
			}

			doc = nextDoc;
		}

		boolean toSave = false;
		String sEnabled = enabled ? "1" : "0";
		if (program == null) {
			program = createProgram(database, addinName, sEnabled);
			log("program document (run at specific time) - created. Enabled: " + sEnabled);
			toSave = true;
		}
		else {
			if (!sEnabled.equalsIgnoreCase(program.getItemValueString("Enabled"))) {
				program.replaceItemValue("Enabled", sEnabled);
				toSave = true;
				log("program document (run at specific time) - updated. Enabled: " + sEnabled);
			}
			
			String val = addinName + " " + m_endpoint;
			if (!val.equalsIgnoreCase(program.getItemValueString("CmdLine"))) {
				program.replaceItemValue("CmdLine", val);
				toSave = true;
				log("program document (run at specific time) - updated. CmdLine: " + val);
			}
		}

		// this is only value we need to modify
		if (adjustMinutes > 0) {
		    DateTime dt = database.getParent().createDateTime("Today");
		    dt.setNow();
			dt.adjustMinute(adjustMinutes);
			program.replaceItemValue("Schedule", dt);
			log("program document (run at specific time) - updated. Schedule: " + dt.getLocalTime());

			dt.recycle();
			toSave = true;
		}
		
		if (toSave) {
			program.save();
		}
		
		col.recycle();
		view.recycle();
		
		return program;
	}

	/* 
	 * Create stub program
	 */
	private Document createProgram(Database database, String addinName, String enabled) throws NotesException {
		Document doc = database.createDocument();

		doc.replaceItemValue("Form", "Program");
		doc.replaceItemValue("Type", "Program");
		doc.replaceItemValue("Source", database.getServer());
		doc.replaceItemValue("Program", "runjava");
		doc.replaceItemValue("Enabled", enabled);
		doc.replaceItemValue("Comments", COMMENT_PROMINIC);
		doc.replaceItemValue("CmdLine", addinName + " " + m_endpoint);
		doc.computeWithForm(true, false);

		return doc;
	}

	/*
	 * Check if Program document is DominoMeter
	 */
	private boolean isDominoMeter(Document doc) throws NotesException {
		String cmdLine = doc.getItemValueString("CmdLine");
		return cmdLine.toLowerCase().contains("DominoMeter".toLowerCase());
	}

	/*
	 * Check if Program document is set to be scheduled
	 */
	private boolean isProgramAtStartupOnly(Document doc) throws NotesException {
		return "2".equals(doc.getItemValueString("Enabled"));
	}

	private void log(Object msg) {
		System.out.println("[ProgramConfig] " + msg.toString());
	}
}
