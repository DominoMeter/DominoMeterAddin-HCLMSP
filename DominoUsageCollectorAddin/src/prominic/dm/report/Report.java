package prominic.dm.report;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Vector;

import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.ViewEntryCollection;
import lotus.domino.ViewEntry;
import lotus.domino.Document;
import lotus.domino.NoteCollection;
import lotus.domino.NotesException;

import prominic.dm.api.Keyword;
import prominic.dm.api.Log;
import prominic.dm.api.Ping;
import prominic.io.Bash;
import prominic.io.RESTClient;
import prominic.util.MD5Checksum;
import prominic.util.ParsedError;
import prominic.util.FileUtils;
import prominic.util.StringUtils;

public class Report {
	private Session m_session = null;
	private String m_server = null;
	private String m_endpoint = null;
	private ParsedError m_pe = null;

	public Report(Session session, String server, String endpoint) {
		m_session = session;
		m_server = server;
		m_endpoint = endpoint;
	}
	
	public boolean send(Database ab, String version) {
		try {
			Date dateStart = new Date();

			m_pe = null;
			String ndd = m_session.getEnvironmentString("Directory", true);
			String url = m_endpoint.concat("/report?openagent&server=" + RESTClient.encodeValue(m_server));

			// 1. initialize data for report
			Date stepStart = new Date();
			StringBuffer data = new StringBuffer();
			StringBuffer keyword = Keyword.getValue(m_endpoint, m_server, "all");
			Document serverDoc = ab.getView("($ServersLookup)").getDocumentByKey(m_server, true);
			data.append("numStep1=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 2. users
			stepStart = new Date();
			data.append(usersInfo(ab, serverDoc));
			data.append("&numStep2=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 3. databases
			stepStart = new Date();
			data.append(getDatabaseInfo());
			data.append("&numStep3=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 4. dir assistance
			stepStart = new Date();
			if (isDA(serverDoc)) {
				data.append("&da=1");
			}
			data.append("&numStep4=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 5. system data
			stepStart = new Date();
			data.append(getSystemInfo(ab, version));
			data.append("&numStep5=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 6. notes.ini, we could get variables using API call
			stepStart = new Date();
			data.append(getNotesINI(keyword));
			data.append("&numStep6=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 7. server document items
			stepStart = new Date();
			data.append(getServerItems(serverDoc, keyword));
			data.append("&numStep7=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 8. program documents
			stepStart = new Date();
			data.append("&programs=" + RESTClient.encodeValue(getProgram(ab)));
			data.append("&numStep8=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 9. id files on server
			stepStart = new Date();
			String idFiles = getIdFiles(ndd);
			if (!idFiles.isEmpty()) {
				data.append("&idfiles=" + idFiles);	
			}
			data.append("&numStep9=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 10. services
			stepStart = new Date();
			String services = this.getServices();
			if (!services.isEmpty()) {
				data.append(services);
			}
			data.append("&numStep10=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 11. Linux specific data
			stepStart = new Date();
			if (System.getProperty("os.name").equalsIgnoreCase("Linux")) {
				data.append(this.getLinuxInfo());
			}
			data.append("&numStep11=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 12. Get 10 last NSD files from IBM_TECHNICAL_SUPPORT folder
			stepStart = new Date();
			String nsd = getNSD(ndd);
			if (!nsd.isEmpty()) {
				data.append(nsd);
			}
			data.append("&numStep12=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 13. In case if connection is done via HTTP we still need to check if HTTPS works
			stepStart = new Date();
			data.append(checkHTTPSConnection());
			data.append("&numStep13=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 14. Jedi upload files
			stepStart = new Date();
			if (System.getProperty("os.name").equalsIgnoreCase("Linux")) {
				data.append(jedi());	
			}
			data.append("&numStep14=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 100. to measure how long it takes to calculate needed data
			String numDuration = Long.toString(new Date().getTime() - dateStart.getTime());
			data.append("&numDuration=" + numDuration);

			serverDoc.recycle();

			StringBuffer res = RESTClient.sendPOST(url, data.toString());
			return res.toString().equals("OK");
		} 
		catch (NotesException e) {
			m_pe = new ParsedError(e);
		}
		catch (Exception e) {
			m_pe = new ParsedError(e);
		}
		return false;
	}

	private String usersInfo(Database ab, Document serverDoc) {
		StringBuffer buf = new StringBuffer();
		
		UsersInfo ui = new UsersInfo();
		if (ui.process(ab, m_server, serverDoc)) {
			buf.append("&users=" + Long.toString(ui.getUsers()));
			buf.append("&usersNotes=" + Long.toString(ui.getUsersNotes()));
			buf.append("&usersWeb=" + Long.toString(ui.getUsersWeb()));
			buf.append("&usersNotesWeb=" + Long.toString(ui.getUsersNotesWeb()));
			buf.append("&usersPNI=" + Long.toString(ui.getUsersPNI()));
			buf.append("&usersMail=" + Long.toString(ui.getUsersMail()));
			buf.append("&usersConflict=" + Long.toString(ui.getUsersConflict()));

			buf.append("&usersAllow=" + Long.toString(ui.getAllowCount()));
			buf.append("&usersDeny=" + Long.toString(ui.getDenyCount()));
		}
		else {
			Log.sendError(m_server, m_endpoint, ui.getParsedError());
		}
		
		return buf.toString();
	}

	private String jedi() throws IOException {
		String res = "";

		StringBuffer partitionsxml = FileUtils.readFileContent("/opt/prominic/jedi/etc/partitions.xml");
		if (partitionsxml != null) {
			res += "&FilePartitionsxml=" + RESTClient.encodeValue(partitionsxml.toString());
		}

		StringBuffer jdicfg = FileUtils.readFileContent("/opt/prominic/jedi/etc/jdi.cfg");
		if (jdicfg != null) {
			res += "&FileJdiCfg=" + RESTClient.encodeValue(jdicfg.toString());

			/*
			int start = jdicfg.indexOf("server.text.port=");
			if (start > 0) {
				start += "server.text.port=".length();
				int end = jdicfg.indexOf(System.getProperty("line.separator"), start);
				if (end > start && end - start < 10) {

					int port = Integer.parseInt(jdicfg.substring(start, end));
					System.out.print(port);
					EchoClient echoClient = new EchoClient();
					System.out.print("1");
					boolean connect = echoClient.startConnection("0", port);
					System.out.print("2");
					System.out.print(connect);
					String jedi = echoClient.readBufferReaderReady();
					System.out.print(jedi);

					String answer = echoClient.sendMessage("Gstatus\r\n");
					System.out.print(answer);


					echoClient.stopConnection();
					System.out.print("closed connection");

					String answer = echoClient.sendMessage("Glogin admin pass\r\n");
					System.out.print("3");
					System.out.print(answer);
					echoClient.stopConnection();
					System.out.print("perfect");
					/*
					if (!bashRes.contains("refused")) {
						res += "&jdiTelnet=" + RESTClient.encodeValue(bashRes);

						bashRes = Bash.exec("Glogin admin pass");
						System.out.println(bashRes);

						res += "&jdiLogin=" + RESTClient.encodeValue(bashRes);
						if (bashRes.contains("denied")) {
							bashRes = Bash.exec("Gstatus");
							System.out.println(bashRes);

							res += "&jdiStatus=" + RESTClient.encodeValue(bashRes);
							bashRes = Bash.exec("Glogout");
							System.out.println(bashRes);
							res += "&jdiLogout=" + RESTClient.encodeValue(bashRes);
						}
					}
				}
			}
			 */
		}

		return res;
	}

	private String checkHTTPSConnection() {
		StringBuffer buf = new StringBuffer();
		if (!m_endpoint.startsWith("http://")) return "";

		String testHTTPS = "https" + m_endpoint.substring(4);
		Ping ping = new Ping();
		boolean check = ping.check(testHTTPS, m_server);
		buf.append("&checkhttps=" + (check ? "1" : "0"));
		if (!check) {
			buf.append("&checkhttpserror=" + ping.getParsedError().getMessage());
		}

		return buf.toString();
	}

	/*
	 * Get N latest NSD file names
	 */
	private String getNSD(String ndd) throws NotesException {
		File dir = new File(ndd + File.separator + "IBM_TECHNICAL_SUPPORT");
		if (!dir.isDirectory()) return "";
		File files[] = FileUtils.startsWith(dir, "nsd");
		if (files.length == 0) return "&numNsdCount1Day=0&numNsdCount7Day=0";

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
		String ulimit = Bash.exec("ulimit -n");
		if (ulimit.isEmpty()) return "";

		return "&numUlimit=" + ulimit;
	}

	/*
	 * OS data
	 */
	private String getSystemInfo(Database ab, String version) throws NotesException {
		StringBuffer buf = new StringBuffer();

		buf.append("&osversion=" + System.getProperty("os.version", "n/a"));
		buf.append("&osname=" + System.getProperty("os.name", "n/a"));
		buf.append("&javaversion=" + System.getProperty("java.version", "n/a"));
		buf.append("&javavendor=" + System.getProperty("java.vendor", "n/a"));
		buf.append("&domino=" + m_session.getNotesVersion());
		buf.append("&username=" + System.getProperty("user.name", "n/a"));
		buf.append("&version=" + version);
		buf.append("&endpoint=" + RESTClient.encodeValue(m_endpoint));
		buf.append("&templateVersion=" + getDatabaseVersionNumber(ab));

		String host = "";
		try {
			InetAddress local = InetAddress.getLocalHost();
			host = local.getHostName();
		} catch (UnknownHostException e) {
			host = "n/a";
		}
		buf.append("&hostname=" + host);

		return buf.toString();
	}

	/*
	 * read database info
	 */
	private String getDatabaseInfo() throws NotesException {
		StringBuffer buf = new StringBuffer();

		DatabasesInfo dbInfo = new DatabasesInfo();
		if (dbInfo.process(m_session, m_server)) {
			buf.append("&numNTF=" + Long.toString(dbInfo.getNTF()));
			buf.append("&numNSF=" + Long.toString(dbInfo.getNSF()));
			buf.append("&numMail=" + Long.toString(dbInfo.getMail()));
			buf.append("&numApp=" + Long.toString(dbInfo.getApp()));
			buf.append("&templateUsage=" + RESTClient.encodeValue(dbInfo.getTemplateUsage().toString()));
			buf.append("&anonymousAccessDbList=" + RESTClient.encodeValue(StringUtils.join(dbInfo.getAnonymousAccess(), ";")));
		}
		else {
			Log.sendError(m_server, m_endpoint, dbInfo.getParsedError());
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
	private String getNotesINI(StringBuffer keyword) throws NotesException {
		StringBuffer buf = new StringBuffer();

		String[] variables = getKeywordAsArray(keyword, "Notes.ini=");
		if (variables == null) return "";

		for(int i = 0; i < variables.length; i++) {
			String variable = variables[i].toLowerCase();
			String iniValue = m_session.getEnvironmentString(variable, true);
			if (iniValue.length() > 0) {
				buf.append("&" + variable + "=" + RESTClient.encodeValue(iniValue));	
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
				buf.append("&" + variable + "=" + RESTClient.encodeValue(v));
			}
		}

		return buf.toString();
	}

	private static String getDatabaseVersionNumber(Database database) throws NotesException {
		NoteCollection noteCollection;
		noteCollection = database.createNoteCollection(true);
		noteCollection.setSelectSharedFields(true);
		noteCollection.setSelectionFormula("$TITLE=\"$TemplateBuild\"");
		noteCollection.buildCollection();
		final String noteID = noteCollection.getFirstNoteID();
		final Document designDoc = database.getDocumentByID(noteID);

		return designDoc.getItemValueString("$TemplateBuild");
	}

	/*
	 * Get data from Domino console
	 */
	private String getServices() {
		StringBuffer buf = new StringBuffer();

		// SHOW SERVER
		try {
			String console = m_session.sendConsoleCommand("", "!sh server");
			buf.append("&sh_server=" + RESTClient.encodeValue(console));

			console = m_session.sendConsoleCommand("", "!sh cluster");
			buf.append("&sh_cluster=" + RESTClient.encodeValue(console));

			int index1 = buf.indexOf("DAOS");
			if (index1 >= 0) {
				index1 += "DAOS".length();
				int index2 = buf.indexOf("Not", index1);
				int index3 = buf.indexOf("Enabled", index1);	
				if( index2 >= index3) {
					buf.append("&daos=1");
				};
			}

			index1 = buf.indexOf("Transactional");
			if (index1 >= 0) {
				index1 += "Transactional".length();
				int index2 = buf.indexOf("Not", index1);
				int index3 = buf.indexOf("Enabled", index1);	
				if (index2 >= index3) {
					buf.append("&transactional_logging=1");	
				}
			}

			// SHOW TASKS
			console = m_session.sendConsoleCommand("", "!sh tasks");
			buf.append("&sh_tasks=" + RESTClient.encodeValue(console));		
			if (console.contains("Traveler")) {
				buf.append("&traveler=1");
			}
			if (console.contains("Sametime")) {
				buf.append("&sametime=1");
			}

			// SHOW HEARTBEAT
			console = m_session.sendConsoleCommand("", "!sh heartbeat");
			buf.append("&sh_heartbeat=" + RESTClient.encodeValue(console));		
			if (console.contains("seconds")) {
				String elapsed_time = console.substring(console.lastIndexOf(":") + 2, console.lastIndexOf("seconds") - 1);
				buf.append("&numElapsedTime=" + elapsed_time);
			}
		} catch (NotesException e) {
			Log.sendError(m_server, m_endpoint, e);
		}

		return buf.toString();
	}

	/*
	 * Search all .id files in Domino Data directory
	 * Build file-md5hash list form the result.
	 */
	private String getIdFiles(String ndd) throws Exception {
		File dir = new File(ndd);
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
	private boolean isDA(Document serverDoc) throws NotesException {
		// Names=Names1 [, Names2 [, Names3]]
		String names = m_session.getEnvironmentString("Names", true);
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


	private String getProgram(Database database) throws NotesException {
		StringBuffer buf = new StringBuffer();
		View view = database.getView("($Programs)");
		buf.append(StringUtils.join(view.getColumnNames(), "|"));

		ViewEntryCollection entries = view.getAllEntriesByKey(m_server, true);
		ViewEntry entry = entries.getFirstEntry();
		while (entry != null) {
			ViewEntry nextEntry = entries.getNextEntry();

			@SuppressWarnings("rawtypes")
			Vector v = entry.getColumnValues();
			String s = "";
			for(int i = 0; i < v.size(); i++) {
				if (i > 0) {
					s = s + "|";
				}
				s = s + v.get(i).toString();
			}
			buf.append("~").append(s);

			entry.recycle();
			entry = nextEntry;
		}

		entries.recycle();
		view.recycle();

		return buf.toString();
	}

	public ParsedError getParsedError() {
		return m_pe;
	}
}