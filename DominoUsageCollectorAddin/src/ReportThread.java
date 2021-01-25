import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.TimeZone;
import java.util.Vector;

import javax.net.ssl.SSLContext;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NoteCollection;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.NotesThread;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;
import prominic.dm.api.Keyword;
import prominic.dm.api.Log;
import prominic.dm.api.Ping;
import prominic.dm.report.Catalog;
import prominic.dm.report.DatabasesInfo;
import prominic.dm.report.UsersInfo;
import prominic.io.Bash;
import prominic.io.EchoClient;
import prominic.io.RESTClient;
import prominic.util.FileLogger;
import prominic.util.FileUtils;
import prominic.util.MD5Checksum;
import prominic.util.StringUtils;

public class ReportThread extends NotesThread {
	String m_server;
	String m_endpoint;
	String m_version;
	FileLogger m_fileLogger;

	Catalog m_catalog = null;
	Session m_session = null;
	Database m_ab = null;
	Document m_serverDoc = null;

	public ReportThread(String server, String endpoint, String version, FileLogger fileLogger) {
		m_server = server;
		m_endpoint = endpoint;
		m_version = version;
		m_fileLogger = fileLogger;
	}

	@Override
	public void runNotes() {
		try {
			logMessage("started");
			Date dateStart = new Date();

			m_session = NotesFactory.createSession();
			m_ab = m_session.getDatabase(m_server, "names.nsf");
			m_serverDoc = m_ab.getView("($ServersLookup)").getDocumentByKey(m_server, true);
			m_catalog = new Catalog(m_session);
			m_catalog.initialize();
			if (this.isInterrupted()) return;

			logMessage("endless loop: ON");
			while(!this.isInterrupted()) {
				// endless loop
			}
			logMessage("endless loop: OFF");

			String ndd = m_session.getEnvironmentString("Directory", true);
			String url = m_endpoint.concat("/report?openagent");

			// 1. initialize data for report
			Date stepStart = new Date();
			StringBuffer data = new StringBuffer();
			StringBuffer keyword = Keyword.getValue(m_endpoint, m_server, "all");
			data.append("numStep1=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 2. users
			stepStart = new Date();
			data.append(usersInfo(m_ab));
			data.append("&numStep2=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 3. databases
			stepStart = new Date();
			data.append(getDatabaseInfo());
			data.append("&numStep3=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 4. dir assistance
			stepStart = new Date();
			if (isDA()) {
				data.append("&da=1");
			}
			data.append("&numStep4=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 5. system data
			stepStart = new Date();
			data.append(getSystemInfo(m_ab, m_version));
			data.append("&numStep5=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 6. notes.ini, we could get variables using API call
			stepStart = new Date();
			data.append(getNotesINI(keyword));
			data.append("&numStep6=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 7. server document items
			stepStart = new Date();
			data.append(getServerItems(keyword));
			data.append("&numStep7=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 8. program documents
			stepStart = new Date();
			data.append("&programs=" + StringUtils.encodeValue(getProgram(m_ab)));
			data.append("&numStep8=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 9. id files on server
			stepStart = new Date();
			String idFiles = getIdFiles(ndd);
			if (!idFiles.isEmpty()) {
				data.append("&idfiles=" + idFiles);
			}
			data.append("&numStep9=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 10. services
			stepStart = new Date();
			String services = this.getServices();
			if (!services.isEmpty()) {
				data.append(services);
			}
			data.append("&numStep10=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 11. Linux specific data
			stepStart = new Date();
			if (System.getProperty("os.name").equalsIgnoreCase("Linux")) {
				data.append(this.getLinuxInfo());
			}
			data.append("&numStep11=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 12. Get 10 last NSD files from IBM_TECHNICAL_SUPPORT folder
			stepStart = new Date();
			String nsd = getNSD(ndd);
			if (!nsd.isEmpty()) {
				data.append(nsd);
			}
			data.append("&numStep12=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 13. In case if connection is done via HTTP we still need to check if HTTPS works
			stepStart = new Date();
			data.append(checkHTTPSConnection());
			data.append("&numStep13=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 14. Jedi
			stepStart = new Date();
			if (System.getProperty("os.name").equalsIgnoreCase("Linux")) {
				data.append(jedi());
			}
			data.append("&numStep14=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 100. to measure how long it takes to calculate needed data
			String numDuration = Long.toString(new Date().getTime() - dateStart.getTime());
			data.append("&numDuration=" + numDuration);
			if (this.isInterrupted()) return;

			StringBuffer res = RESTClient.sendPOST(url, data.toString());
			logMessage("Result: " + res.toString());
		}
		catch (NotesException e) {
			m_fileLogger.severe(e);
		}
		catch (Exception e) {
			m_fileLogger.severe(e);
		}
	}

	private String usersInfo(Database ab) {
		StringBuffer buf = new StringBuffer();

		UsersInfo ui = new UsersInfo(m_fileLogger);
		if (ui.process(m_session, m_catalog, ab, m_server, m_serverDoc)) {
			buf.append("&usersEditor=" + Long.toString(ui.getUsersEditor()));
			buf.append("&usersAuthor=" + Long.toString(ui.getUsersAuthor()));
			buf.append("&usersReader=" + Long.toString(ui.getUsersReader()));
			buf.append("&usersDepositor=" + Long.toString(ui.getUsersDepositor()));
			buf.append("&usersNoAccess=" + Long.toString(ui.getUsersNoAccess()));
			buf.append("&usersTotal=" + Long.toString(ui.getUsersTotal()));
			buf.append("&usersNotes=" + Long.toString(ui.getUsersNotes()));
			buf.append("&usersWeb=" + Long.toString(ui.getUsersWeb()));
			buf.append("&usersNotesWeb=" + Long.toString(ui.getUsersNotesWeb()));
			buf.append("&usersPNI=" + Long.toString(ui.getUsersPNI()));
			buf.append("&usersMail=" + Long.toString(ui.getUsersMail()));
			buf.append("&usersConflict=" + Long.toString(ui.getUsersConflict()));
			buf.append("&usersAllow=" + Long.toString(ui.getUsersAllow()));
			buf.append("&usersDeny=" + Long.toString(ui.getUsersDeny()));
			buf.append("&richtextUsersList=" + ui.getUsersList());
			buf.append("&UsersListHashCode=" + ui.getUsersList().toString().hashCode());
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
			res += "&FilePartitionsxml=" + StringUtils.encodeValue(partitionsxml.toString());
		}

		StringBuffer jdicfg = FileUtils.readFileContent("/opt/prominic/jedi/etc/jdi.cfg");
		if (jdicfg != null) {
			res += "&FileJdiCfg=" + StringUtils.encodeValue(jdicfg.toString());
			int start = jdicfg.indexOf("server.text.port=");
			if (start > 0) {
				start += "server.text.port=".length();
				int end = jdicfg.indexOf(System.getProperty("line.separator"), start);
				if (end > start && end - start < 10) {
					int port = Integer.parseInt(jdicfg.substring(start, end));

					EchoClient echoClient = new EchoClient();
					boolean connect = echoClient.startConnection("127.0.0.1", port);

					if (connect) {
						echoClient.sendMessage("Glogin admin pass");
						echoClient.sendMessage("Gstatus");
						echoClient.sendMessage("Glogout");
						echoClient.shutdownOutput();
						String jediInfo = echoClient.readBufferReader();
						res += "&JediInfo=" + StringUtils.encodeValue(jediInfo);

						echoClient.stopConnection();
					}
					else {
						Log.sendError(m_server, m_endpoint, echoClient.getParsedError());
					}
				}
			}
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

		buf.append("&server=" + StringUtils.encodeValue(m_server));
		buf.append("&ostimezone=" + StringUtils.encodeValue(TimeZone.getDefault().getDisplayName()));
		buf.append("&osversion=" + System.getProperty("os.version", "n/a"));
		buf.append("&osname=" + System.getProperty("os.name", "n/a"));
		buf.append("&javaversion=" + System.getProperty("java.version", "n/a"));
		buf.append("&javavendor=" + System.getProperty("java.vendor", "n/a"));
		buf.append("&domino=" + m_session.getNotesVersion());
		buf.append("&username=" + System.getProperty("user.name", "n/a"));
		buf.append("&version=" + version);
		buf.append("&endpoint=" + StringUtils.encodeValue(m_endpoint));
		buf.append("&templateVersion=" + getDatabaseVersionNumber(ab));

		String SSLcipher = "";
		String SupportedCipherSuites = "";
		try {
			SSLcipher = StringUtils.join(SSLContext.getDefault().getSupportedSSLParameters().getProtocols(), ";");
			SupportedCipherSuites = StringUtils.join(SSLContext.getDefault().getSocketFactory().getSupportedCipherSuites(), ";");
		} catch (NoSuchAlgorithmException e1) {
			SSLcipher = "n/a";
			SupportedCipherSuites = "n/a";
		}
		buf.append("&SSLcipher=" + SSLcipher);
		buf.append("&SupportedCipherSuites=" + SupportedCipherSuites);

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
		if (dbInfo.process(m_catalog, m_session)) {
			buf.append("&numNTF=" + Long.toString(dbInfo.getNTF()));
			buf.append("&numNSF=" + Long.toString(dbInfo.getNSF()));
			buf.append("&numMail=" + Long.toString(dbInfo.getMail()));
			buf.append("&numApp=" + Long.toString(dbInfo.getApp()));
			buf.append("&templateUsage=" + StringUtils.encodeValue(dbInfo.getTemplateUsage().toString()));
			buf.append("&dbReplica=" + StringUtils.encodeValue(dbInfo.getDbReplica().toString()));
			buf.append("&anonymousAccessDbList=" + StringUtils.encodeValue(StringUtils.join(dbInfo.getAnonymousAccess(), ";")));
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
				buf.append("&" + variable + "=" + StringUtils.encodeValue(iniValue));
			}
		}

		return buf.toString();
	}

	/*
	 * read variables from server document
	 */
	private String getServerItems(StringBuffer keyword) throws NotesException {
		if (m_serverDoc == null) return "";

		StringBuffer buf = new StringBuffer();
		String[] variables = getKeywordAsArray(keyword, "Server=");
		if (variables == null) return "";
		for(int i = 0; i < variables.length; i++) {
			String variable = variables[i].toLowerCase();
			if (m_serverDoc.hasItem(variable)) {
				String v = m_serverDoc.getFirstItem(variable).getText();
				buf.append("&" + variable + "=" + StringUtils.encodeValue(v));
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
			if (this.isInterrupted()) return "";
			String console = m_session.sendConsoleCommand("", "!sh server");
			buf.append("&sh_server=" + StringUtils.encodeValue(console));

			if (this.isInterrupted()) return "";
			console = m_session.sendConsoleCommand("", "!sh cluster");
			buf.append("&sh_cluster=" + StringUtils.encodeValue(console));

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
			if (this.isInterrupted()) return "";
			console = m_session.sendConsoleCommand("", "!sh tasks");
			buf.append("&sh_tasks=" + StringUtils.encodeValue(console));
			if (console.contains("Traveler")) {
				buf.append("&traveler=1");
			}
			if (console.contains("Sametime")) {
				buf.append("&sametime=1");
			}

			// SHOW HEARTBEAT
			if (this.isInterrupted()) return "";
			console = m_session.sendConsoleCommand("", "!sh heartbeat");
			buf.append("&sh_heartbeat=" + StringUtils.encodeValue(console));
			if (console.contains("seconds")) {
				String elapsed_time = console.substring(console.lastIndexOf(":") + 2, console.lastIndexOf("seconds") - 1);
				buf.append("&numElapsedTime=" + elapsed_time);
			}

		} catch (NotesException e) {
			m_fileLogger.severe(e);
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
	private boolean isDA() throws NotesException {
		// Names=Names1 [, Names2 [, Names3]]
		String names = m_session.getEnvironmentString("Names", true);
		if (names.length() > 5) {
			return true;
		}

		if (m_serverDoc != null) {
			String da = m_serverDoc.getItemValueString("MasterAddressBook");
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

	private void logMessage(Exception e) {
		e.printStackTrace();
		m_fileLogger.severe(e);
	}

	private void logMessage(String msg) {
		m_fileLogger.info("ReportThread: " + msg);
		System.out.println("ReportThread: " + msg);
	}

	/**
	 * This method is called by the Java runtime during garbage collection.
	 */
	@Override
	public void finalize() {
		logMessage("finalize");

		terminate();

		super.finalize();
	}

	@Override
	public void termThread() {
		logMessage("termThread");

		super.termThread();
	}

	/**
	 * Terminate all variables
	 */
	private void terminate() {
		try {
			if (m_catalog != null) {
				m_catalog.recycle();
			}

			if (m_serverDoc != null) {
				m_serverDoc.recycle();
			}

			if (this.m_ab != null) {
				this.m_ab.recycle();
			}
			if (this.m_session != null) {
				this.m_session.recycle();
			}

			logMessage("UNLOADED (OK)");
		} catch (NotesException e) {
			logMessage(e);
		}
	}
}
