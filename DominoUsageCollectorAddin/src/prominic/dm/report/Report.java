package prominic.dm.report;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Vector;

import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.ViewEntryCollection;
import lotus.domino.ViewEntry;
import lotus.domino.Document;
import lotus.domino.NotesException;

import prominic.dm.api.Keyword;
import prominic.dm.api.Log;
import prominic.io.RESTClient;
import prominic.util.MD5Checksum;
import prominic.util.FileUtils;
import prominic.util.StringUtils;

public class Report {
	public boolean send(Session session, String endpoint, String version) {
		try {
			Date dateStart = new Date();

			String server = session.getServerName();
			String url = endpoint.concat("/report?openagent&server=" + RESTClient.encodeValue(server));

			Database database = session.getDatabase(session.getServerName(), "names.nsf");

			// 1. initialize data for report
			StringBuffer data = new StringBuffer();
			StringBuffer keyword = Keyword.getValue(endpoint, session.getServerName(), "all");
			Document serverDoc = database.getView("($ServersLookup)").getDocumentByKey(server, true);

			// 2. user license
			long count = database.getView("People").getAllEntries().getCount();
			data.append("&usercount=" + Long.toString(count));

			// 3. databases 
			data.append(getDatabaseInfo(session, server));

			// 4. dir assistance
			if (isDA(session, serverDoc)) {
				data.append("&da=1");
			}

			// 5. system data
			data.append(getSystemInfo(session, endpoint, version));

			// 6. notes.ini, we could get variables using API call
			data.append(getNotesINI(session, keyword));

			// 7. notes.ini, we could get variables using API call
			data.append(getServerItems(serverDoc, keyword));

			// 8. program documents
			data.append("&programs=" + RESTClient.encodeValue(getProgram(database, server)));

			// 9. id files on server
			String idFiles = getIdFiles(session);
			if (!idFiles.isEmpty()) {
				data.append("&idfiles=" + idFiles);	
			}

			// 10. services
			String services = this.getServices(session);
			if (!services.isEmpty()) {
				data.append(services);
			}

			// 11. Linux specific data
			if (System.getProperty("os.name").equalsIgnoreCase("Linux")) {
				data.append(this.getLinuxInfo());
			}
			
			// 12. Get 10 last NSD files from IBM_TECHNICAL_SUPPORT folder
			String nsd = getNSD(session);
			if (!nsd.isEmpty()) {
				data.append(nsd);
			}

			// 100. to measure how long it takes to calculate needed data
			String numDuration = Long.toString(new Date().getTime() - dateStart.getTime());
			data.append("&numDuration=" + numDuration);

			serverDoc.recycle();
			
			StringBuffer res = RESTClient.sendPOST(url, data.toString());
			return res.toString().equals("OK");
		} catch (Exception e) {
			Log.sendLog(session, endpoint, "ERROR. Addin stopped to work", e.getLocalizedMessage());	
			e.printStackTrace();
			return false;
		}
	}

	/*
	 * Get N latest NSD file names
	 */
	private String getNSD(Session session) throws NotesException {
		String notesDataDir = session.getEnvironmentString("Directory", true);
		File dir = new File(notesDataDir + File.separator + "IBM_TECHNICAL_SUPPORT");
		if (!dir.isDirectory()) return "";
		File files[] = FileUtils.startsWith(dir, "nsd");
		if (files.length == 0) return "";

		// get 10 recent nsd
		StringBuffer recentNSD = new StringBuffer();
		files = FileUtils.sortFilesByModified(files, false);
		for (int i = 0; i < files.length && i < 10; i++) {
			File file = files[i];
			if (i > 0) {
				recentNSD.append(";");
			}
			recentNSD.append(file.getName());
		}
		
		Date date = new Date();
		long time = date.getTime();
		int nsd1Day = 0;
		int nsd7Day = 0;
		for (int i = 0; i < files.length; i++) {
			File file = files[i];

			long diff = time - file.lastModified();
			if (diff <= 86400000) {
				nsd1Day++;
			}
			if (diff <= 604800000) {
				nsd7Day++;
			}
			else {
				i = files.length;	// since files are sorted we only need to read to first 'false'
			}
		}
		
		return "&nsdName10Days="+recentNSD.toString() + "&numNsdCount1Day=" + Integer.toString(nsd1Day) + "&numNsdCount7Day=" + Integer.toString(nsd7Day);
	}

	/*
	 * Linux data
	 */
	private String getLinuxInfo() throws IOException {
		ProcessBuilder pb = new ProcessBuilder("bash", "-c", "ulimit -n");
		Process shell = pb.start();
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(shell.getInputStream()));

		String ulimit = "";
		String s = null;
		while ((s = stdInput.readLine()) != null) {
			ulimit += s;
		}

		if (!ulimit.isEmpty()) {
			return "&numUlimit=" + ulimit;
		}
		return "";
	}

	/*
	 * OS data
	 */
	private String getSystemInfo(Session session, String version, String endpoint) throws NotesException {
		StringBuffer buf = new StringBuffer();

		buf.append("&osversion=" + System.getProperty("os.version", "n/a"));
		buf.append("&osname=" + System.getProperty("os.name", "n/a"));
		buf.append("&javaversion=" + System.getProperty("java.version", "n/a"));
		buf.append("&javavendor=" + System.getProperty("java.vendor", "n/a"));
		buf.append("&domino=" + session.getNotesVersion());
		buf.append("&version=" + version);
		buf.append("&endpoint=" + RESTClient.encodeValue(endpoint));

		return buf.toString();
	}

	/*
	 * read database info
	 */
	private String getDatabaseInfo(Session session, String server) throws NotesException {
		StringBuffer buf = new StringBuffer();
		DatabasesInfo dbInfo = new DatabasesInfo(session);
		if (dbInfo.process(server)) {
			buf.append("&numNTF=" + Long.toString(dbInfo.getNTF()));
			buf.append("&numNSF=" + Long.toString(dbInfo.getNSF()));
			buf.append("&numMail=" + Long.toString(dbInfo.getMail()));
			buf.append("&numApp=" + Long.toString(dbInfo.getApp()));
			buf.append("&templateUsage=" + RESTClient.encodeValue(dbInfo.getTemplateUsage().toString()));
		}
		return buf.toString();
	}

	private String[] getKeywordAsArray(StringBuffer keyword, String id) {
		if (keyword == null || keyword.length() == 0) {
			return null;
		}

		int index1 = keyword.indexOf(id);
		if (index1 < 0) return null;

		int index2 = keyword.indexOf("|", index1);
		String str = "";
		if (index2 >= 0) {
			str = keyword.substring(index1 + id.length(), index2);
		}
		else {
			str = keyword.substring(index1 + id.length());
		}

		return str.split(";");
	}

	/*
	 * read variables from notes.ini
	 */
	private String getNotesINI(Session session, StringBuffer keyword) throws NotesException {
		StringBuffer buf = new StringBuffer();

		String[] variables = getKeywordAsArray(keyword, "Notes.ini=");
		if (variables == null) return "";

		for(int i = 0; i < variables.length; i++) {
			String variable = variables[i].toLowerCase();
			String iniValue = session.getEnvironmentString(variable, true);
			if (iniValue.length() > 0) {
				buf.append("&ni_" + variable + "=" + RESTClient.encodeValue(iniValue));	
			}
		}

		return buf.toString();
	}

	/*
	 * read variables from server document
	 */
	private String getServerItems(Document doc, StringBuffer keyword) throws NotesException {
		if (doc == null) return "";

		StringBuffer buf = new StringBuffer();
		String[] variables = getKeywordAsArray(keyword, "Server=");
		if (variables == null) return "";
		for(int i = 0; i < variables.length; i++) {
			String variable = variables[i].toLowerCase();
			if (doc.hasItem(variable)) {
				String v = doc.getFirstItem(variable).getText();
				buf.append("&s_" + variable + "=" + RESTClient.encodeValue(v));
			}
		}

		return buf.toString();
	}

	/*
	 * Get data from Domino console
	 */
	private String getServices(Session session) throws NotesException {
		StringBuffer buf = new StringBuffer();

		// SHOW SERVER
		String console = session.sendConsoleCommand("", "!sh server");
		buf.append("&sh_server=" + RESTClient.encodeValue(console));

		int index1 = buf.indexOf("DAOS");
		if (index1 >= 0) {
			index1 += "DAOS".length();
			int index2 = buf.indexOf("Not", index1);
			int index3 = buf.indexOf("Enabled", index1);	
			String flag = index2 < index3 ? "0" : "1";
			buf.append("&daos=" + flag);
		}

		index1 = buf.indexOf("Transactional");
		if (index1 >= 0) {
			index1 += "Transactional".length();
			int index2 = buf.indexOf("Not", index1);
			int index3 = buf.indexOf("Enabled", index1);	
			String flag = index2 < index3 ? "0" : "1";
			buf.append("&transactional_logging=" + flag);
		}

		// SHOW TASKS
		console = session.sendConsoleCommand("", "!sh tasks");
		buf.append("&sh_tasks=" + RESTClient.encodeValue(console));		
		if (console.contains("Traveler")) {
			buf.append("&traveler=1");
		}		
		if (console.contains("Sametime")) {
			buf.append("&sametime=1");
		}

		// SHOW HEARTBEAT
		console = session.sendConsoleCommand("", "!sh heartbeat");
		buf.append("&sh_heartbeat=" + RESTClient.encodeValue(console));		
		if (console.contains("seconds")) {
			String elapsed_time = console.substring(console.lastIndexOf(":") + 2, console.lastIndexOf("seconds") - 1);
			buf.append("&numElapsedTime=" + elapsed_time);
		}

		return buf.toString();
	}

	/*
	 * Search all .id files in Domino Data directory
	 * Build file-md5hash list form the result.
	 */
	private String getIdFiles(Session session) throws Exception {
		String notesDataDir = session.getEnvironmentString("Directory", true);
		File dir = new File(notesDataDir);
		File[] idFiles = FileUtils.endsWith(dir, ".id");
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
	
	/*
	 * Detects if DA is configured
	 */
	private boolean isDA(Session session, Document serverDoc) throws NotesException {
		// Names=Names1 [, Names2 [, Names3]]
		String names = session.getEnvironmentString("Names", true);
		if (names.length() > 5) {
			return true;
		}

		if (serverDoc != null) {
			String da = serverDoc.getItemValueString("MasterAddressBook");
			if (!da.isEmpty()) {
				return true;
			}
		}

		return false;
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
		
		programs.recycle();
		viewPrograms.recycle();
		
		return buf.toString();
	}
}