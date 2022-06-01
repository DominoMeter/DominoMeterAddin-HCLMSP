package net.prominic.install;

import java.io.File;

import java.io.IOException;
import java.io.Reader;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.NotesException;
import net.prominic.gja_v20220601.GLogger;
import net.prominic.gja_v20220601.ProgramConfig;
import net.prominic.io.RESTClient;

public class JSONRulesStub {
	private Session m_session;
	private Database m_ab;
	private StringBuffer m_logBuffer;
	private GLogger m_logger;
	
	public JSONRulesStub(Session session, Database ab, GLogger logger) {
		m_session = session;
		m_ab = ab;
		m_logger = logger;
	}

	public boolean execute(String json) {
		JSONParser parser = new JSONParser();
		try {
			JSONObject jsonObject = (JSONObject) parser.parse(json);
			return execute(jsonObject);
		} catch (ParseException e) {
			log(e);
		}
		return false;
	}

	public boolean execute(Reader reader) {
		JSONParser parser = new JSONParser();
		try {
			JSONObject jsonObject = (JSONObject) parser.parse(reader);
			return execute(jsonObject);
		} catch (IOException e) {
			log(e);
		} catch (ParseException e) {
			log(e);
		}
		return false;
	}

	/*
	 * Exectute JSON
	 */
	private boolean execute(JSONObject obj) {
		m_logBuffer = new StringBuffer();
		
		// if error
		if (obj.containsKey("error")) {
			String error = (String) obj.get("error");	
			log(error);
			return false;
		}

		JSONArray steps = (JSONArray) obj.get("steps");
		if (steps.size() == 0) {
			log("Invalid JSON structure (no steps defined)");
			return false;
		}

		if (obj.containsKey("title")) {
			log(obj.get("title"));
		}

		for(int i=0; i<steps.size(); i++) {
			JSONObject step = (JSONObject) steps.get(i);
			parseStep(step);
		}
		
		return true;
	}

	/*
	 * Parse a step
	 */
	private void parseStep(JSONObject step) {
		if (step.containsKey("title")) {
			log(step.get("title"));
		}
		if(step.containsKey("files")) {
			doFiles((JSONArray) step.get("files"));
		}
		else if(step.containsKey("notesINI")) {
			doNotesINI((JSONArray) step.get("notesINI"));
		}
		else if(step.containsKey("messages")) {
			doMessages((JSONArray) step.get("messages"));
		}
		else if(step.containsKey("programConfig")) {
			programConfig((Long)step.get("programConfig"));
		}
	}

	/*
	 * Used to setup program documents to load addin
	 */
	private void programConfig(long state) {
		ProgramConfig gpc = new ProgramConfig("Genesis", null, m_logger);
		gpc.setState(m_ab, (int)state);		// set program documents in LOAD state
	}

	/*
	 * Display messages to Domino console
	 */
	private void doMessages(JSONArray list) {
		if (list == null || list.size() == 0) return;

		for(int i=0; i<list.size(); i++) {
			String v = (String) list.get(i);
			log(v);
		}
	}

	/*
	 * Download files
	 */
	private void doFiles(JSONArray list) {
		if (list == null || list.size() == 0) return;

		String directory;
		try {
			directory = this.m_session.getEnvironmentString("Directory", true);

			for(int i=0; i<list.size(); i++) {
				JSONObject obj = (JSONObject) list.get(i);

				String from = (String) obj.get("from");
				String to = (String) obj.get("to");

				if (to.indexOf("${directory}")>=0) {
					to = to.replace("${directory}", directory);
				};

				saveFile(from, to);
				log("> " + to);
			}
		} catch (NotesException e) {
			log(e);
		} catch (IOException e) {
			log(e);
		}
	}

	private void saveFile(String from, String to) throws IOException {
		log("Download: " + from);
		log("To: " + to);

		// check if file already exists (by default skip)
		File file = new File(to);
		if (file.exists()) {
			log("> skip (already exists)");
			return;
		}

		// create sub folders if needed
		String toPath = to.substring(0, to.lastIndexOf("/"));
		File dir = new File(toPath);
		if (!dir.exists()) {
			dir.mkdirs();
		}

		boolean res = RESTClient.saveURLTo(from, to);
		if (!res) {
			log("> failed to download");
		}

		log("> done");					
	}

	/*
	 * notes.INI handling
	 */
	private void doNotesINI(JSONArray list) {
		if (list == null || list.size() == 0) return;

		for(int i=0; i<list.size(); i++) {
			JSONObject obj = (JSONObject) list.get(i);

			String name = (String) obj.get("name");
			String value = String.valueOf(obj.get("value"));

			boolean multivalue = obj.containsKey("multivalue") && (Boolean)obj.get("multivalue");
			String sep = multivalue ? (String) obj.get("sep") : "";

			try {
				setNotesINI(name, value, multivalue, sep);
			} catch (NotesException e) {
				log(e);
			}
		}
	}

	/*
	 * notes.INI variables
	 */
	private void setNotesINI(String name, String value, boolean multivalue, String sep) throws NotesException {
		if (multivalue) {
			String currentValue = m_session.getEnvironmentString(name, true);
			if (!currentValue.contains(value)) {
				if (!currentValue.isEmpty()) {
					currentValue += sep;
				}
				currentValue += value;
			}
			value = currentValue;
		}

		m_session.setEnvironmentVar(name, value, true);	
	}

	public StringBuffer getLogBuffer() {
		return m_logBuffer;
	}

	private void log(Exception e) {
		e.printStackTrace();
		m_logBuffer.append(e.getLocalizedMessage());
		m_logBuffer.append(System.getProperty("line.separator"));
	}
	
	private void log(Object o) {
		System.out.println(o.toString());
		m_logBuffer.append(o.toString());
		m_logBuffer.append(System.getProperty("line.separator"));
	}
}
