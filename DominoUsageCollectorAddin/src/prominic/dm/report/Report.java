package prominic.dm.report;

import java.io.File;

import java.util.Date;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;
import prominic.dm.api.Keyword;
import prominic.io.RESTClient;
import prominic.util.MD5Checksum;
import prominic.util.SearchFiles;
import prominic.util.StringUtils;

public class Report {
	private Session m_session = null;
	private Database m_database = null;
	private String m_endpoint = null;
	private String m_version = "";

	public Report(Session session, String endpoint, String version) {
		m_session = session;
		m_endpoint = endpoint;
		m_version = version;
	}

	private Database getAddressBook() throws NotesException {
		if (m_database == null) {
			m_database = m_session.getDatabase(m_session.getServerName(), "names.nsf");
		}
		return m_database;
	}

	/*
	 * Detects if DA is configured
	 */
	private boolean isDA() throws NotesException {
		// Names=Names1 [, Names2 [, Names3]]
		String names = m_session.getEnvironmentString("Names", true);
		if (names.length() > 5) {
			return true;
		}

		Document serverDoc = m_database.getView("($ServersLookup)").getDocumentByKey(m_session.getServerName(), true);
		if (serverDoc != null) {
			String da = serverDoc.getItemValueString("MasterAddressBook");
			if (!da.isEmpty()) {
				return true;
			}
		}

		return false;
	}

	public boolean send() {
		try {
			Date dateStart = new Date();

			String url = m_endpoint.concat("/report?openagent");

			Database database = getAddressBook();
			if (database == null) {
				return false;
			}

			// 1. server
			String server = m_session.getServerName();
			StringBuffer data = new StringBuffer("server=" + RESTClient.encodeValue(server));

			// 2. user license
			View view = database.getView("People");
			long count = view.getAllEntries().getCount();
			data.append("&usercount=" + Long.toString(count));

			// 3. databases 
			data.append(getDatabaseInfo(server));

			// 4. dir assistance
			boolean da = isDA();
			if (da) {
				data.append("&da=1");
			}

			// 5. system data
			data.append(getSystemInfo());
			
			// 6. notes.ini, we could get variables using API call
			data.append(getNotesINI());

			// 7. program documents
			data.append("&programs=" + RESTClient.encodeValue(getProgram(database, server)));

			// 8. id files on server
			String idFiles = getIdFiles();
			if (!idFiles.isEmpty()) {
				data.append("&idfiles=" + idFiles);	
			}
			
			// 9. services
			String services = this.getServices();
			if (!services.isEmpty()) {
				data.append(services);
			}

			// 100. to measure how long it takes to calculate needed data
			String numDuration = Long.toString(new Date().getTime() - dateStart.getTime());
			data.append("&numDuration=" + numDuration);

			StringBuffer res = RESTClient.sendPOST(url, data.toString());
			return res.toString().equals("OK");
		} catch (Exception e) {
			return false;
		}
	}
	
	/*
	 * OS data
	 */
	private String getSystemInfo() throws NotesException {
		StringBuffer buf = new StringBuffer();

		String statOS = System.getProperty("os.version", "n/a") + " (" + System.getProperty("os.name", "n/a") + ")";
		String statJavaVersion = System.getProperty("java.version", "n/a") + " (" + System.getProperty("java.vendor", "n/a") + ")";
		String statDomino = m_session.getNotesVersion();

		buf.append("&os=" + RESTClient.encodeValue(statOS));
		buf.append("&java=" + RESTClient.encodeValue(statJavaVersion));
		buf.append("&domino=" + RESTClient.encodeValue(statDomino));
		buf.append("&version=" + m_version);
		buf.append("&endpoint=" + RESTClient.encodeValue(m_endpoint));
		
		return buf.toString();
	}

	/*
	 * read database info
	 */
	private String getDatabaseInfo(String server) throws NotesException {
		StringBuffer buf = new StringBuffer();
		DatabasesInfo dbInfo = new DatabasesInfo(m_session);
		if (dbInfo.process(server)) {
			buf.append("&numNTF=" + Long.toString(dbInfo.getNTF()));
			buf.append("&numNSF=" + Long.toString(dbInfo.getNSF()));
			buf.append("&numMail=" + Long.toString(dbInfo.getMail()));
			buf.append("&numApp=" + Long.toString(dbInfo.getApp()));
			buf.append("&templateUsage=" + RESTClient.encodeValue(dbInfo.getTemplateUsage().toString()));
		}
		return buf.toString();
	}
	
	/*
	 * read variables from notes.ini
	 */
	private String getNotesINI() throws NotesException {
		StringBuffer buf = new StringBuffer();

		StringBuffer keyword = Keyword.getValue(m_endpoint, m_session.getServerName(), "Notes.ini");
		if (keyword != null && !keyword.toString().isEmpty()) {
			String[] iniVariables = keyword.toString().split(";");
			for(int i = 0; i < iniVariables.length; i++) {
				String variable = iniVariables[i].toLowerCase();
				String iniValue = m_session.getEnvironmentString(variable, true);
				if (iniValue.length() > 0) {
					buf.append("&ni_" + variable + "=" + RESTClient.encodeValue(iniValue));	
				}
			}
		}
		
		return buf.toString();
	}
	
	/*
	 * Check if Traveler, Sametime etc are running
	 */
	private String getServices() throws NotesException {
		String console = m_session.sendConsoleCommand("", "show tasks");
		
		StringBuffer buf = new StringBuffer();
		
		if (console.contentEquals("Traveler")) {
			buf.append("&traveler=1");
		}		
		if (console.contentEquals("Sametime")) {
			buf.append("&sametime=1");
		}
		if (console.contentEquals("IMSMO")) {
			buf.append("&imsmo=1");
		}
		
		return buf.toString();
	}

	/*
	 * Search all .id files in Domino Data directory
	 * Build file-md5hash list form the result.
	 */
	private String getIdFiles() throws Exception {
		String notesDataDir = m_session.getEnvironmentString("Directory", true);
		File[] idFiles = SearchFiles.endsWith(notesDataDir, ".id");
		if (idFiles.length == 0) return "";

		StringBuffer buf = new StringBuffer();
		for(int i = 0; i < idFiles.length; i++) {
			if(i > 0) buf.append("~");
			File file = idFiles[i];
			
			buf.append(file.getName());
			buf.append("|");

			String md5hash = MD5Checksum.getMD5Checksum(file);
			buf.append(md5hash);
		}

		return buf.toString();
	}

	private String getProgram(Database database, String server) throws NotesException {
		StringBuffer buf = new StringBuffer();
		View viewPrograms = database.getView("($Programs)");
		buf.append(StringUtils.join(viewPrograms.getColumnNames(), "|"));

		ViewEntryCollection programs = viewPrograms.getAllEntriesByKey(server, true);
		ViewEntry program = programs.getFirstEntry();
		while (program != null) {
			@SuppressWarnings("rawtypes")
			Vector v = program.getColumnValues();
			String s = "";
			for(int i = 0; i < v.size(); i++) {
				if (i > 0) {
					s = s + "|";
				}
				s = s + v.get(i).toString();
			}
			buf.append("~").append(s);

			program = programs.getNextEntry();
		}

		return buf.toString();
	}
}