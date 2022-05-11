import java.io.File;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.Vector;
import javax.net.ssl.SSLContext;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NoteCollection;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.NotesThread;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;
import net.prominic.dm.api.Keyword;
import net.prominic.dm.api.Log;
import net.prominic.dm.api.Ping;
import net.prominic.dm.report.NamesUtil;
import net.prominic.dm.report.UsersInfo;
import net.prominic.gja_v20220511.GLogger;
import net.prominic.io.Bash;
import net.prominic.io.EchoClient;
import net.prominic.io.RESTClient;
import net.prominic.util.FileUtils;
import net.prominic.util.MD5Checksum;
import net.prominic.util.StringUtils;

public class ReportThread extends NotesThread {
	private String m_server;
	private String m_endpoint;
	private String m_version;
	private GLogger m_fileLogger;
	private boolean m_manual = false;

	private Session m_session = null;
	private Database m_ab = null;
	private Database m_catalog = null;
	private ArrayList<Document> m_catalogList = null;
	private Document m_serverDoc = null;
	NamesUtil m_namesUtil = null;

	public ReportThread(String server, String endpoint, String version, GLogger fileLogger, boolean manual) {
		m_server = server;
		m_endpoint = endpoint;
		m_version = version;
		m_fileLogger = fileLogger;
		m_manual = manual;
	}

	@Override
	public void runNotes() {
		try {
			logMessage("started");

			Date dateStart = new Date();

			m_session = NotesFactory.createSession();
			m_ab = m_session.getDatabase(null, "names.nsf");
			initCatalog();
			if (this.isInterrupted()) return;
			m_serverDoc = m_ab.getView("($ServersLookup)").getDocumentByKey(m_server, true);

			if (this.isInterrupted()) return;

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

			// 15. installed utils (f.x. gdp)
			stepStart = new Date();
			if (System.getProperty("os.name").equalsIgnoreCase("Linux")) {
				data.append(installedUtils());
			}
			data.append("&numStep15=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 16. check if folder(s) exist (extend with other files/folder if needed)
			stepStart = new Date();
			data.append(checkFilesFolders(ndd));
			data.append("&numStep16=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 17. IDVault check
			stepStart = new Date();
			data.append(vault(ndd));
			data.append("&numStep17=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 18. Panagenda
			stepStart = new Date();
			data.append(panagenda(ndd));
			data.append("&numStep18=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 19. SAML
			stepStart = new Date();
			data.append(saml(ndd));
			data.append("&numStep19=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 20. Directory Profile
			stepStart = new Date();
			data.append(directoryProfile(keyword));
			data.append("&numStep20=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 21. MFA installed?
			stepStart = new Date();
			data.append(mfa());
			data.append("&numStep21" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 22. jvm/lib/ext file lists
			stepStart = new Date();
			data.append(jvmLibExt());
			data.append("&numStep22=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 23. java.policy, java.security
			stepStart = new Date();
			data.append(javaPolicy());
			data.append("&numStep23=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 99. error counter
			long total_exception = DominoMeter.getExceptionTotal();
			data.append("&numErrorCounter=" + String.valueOf(total_exception));

			// 100. to measure how long it takes to calculate needed data
			String numDuration = Long.toString(new Date().getTime() - dateStart.getTime());
			data.append("&numDuration=" + numDuration);
			if (this.isInterrupted()) return;

			StringBuffer res = RESTClient.sendPOST(url, data.toString());
			logMessage("finished (" + res.toString() + ")");
		}
		catch (NotesException e) {
			logSevere(e);
		} catch (IOException e) {
			logSevere(e);
		} catch (NoSuchAlgorithmException e) {
			logSevere(e);
		} catch (Exception e) {
			logSevere(e);
		}
	}

	private Object javaPolicy() {
		String res = "";

		try {
			String javaPolicyPath = "jvm/lib/security/java.policy";
			StringBuffer javaPolicy = FileUtils.readFileContentFilter(javaPolicyPath, new String[]{"//"}, true);
			if (javaPolicy != null) {
				res += "&richtextJavaPolicy=" + StringUtils.encodeValue(javaPolicy.toString());
				res += "&JavaPolicyMD5=" + MD5Checksum.getMD5Checksum(new File(javaPolicyPath));
			}

			String javaSecurityPath = "jvm/lib/security/java.security";
			StringBuffer javaSecurity = FileUtils.readFileContentFilter(javaSecurityPath, new String[]{"#"}, true);
			if (javaSecurity != null) {
				res += "&richtextJavaSecurity=" + StringUtils.encodeValue(javaSecurity.toString());
				res += "&JavaSecurityMD5=" + MD5Checksum.getMD5Checksum(new File(javaSecurityPath));
			}
		} catch (NoSuchAlgorithmException e) {
			logSevere(e);
		} catch (IOException e) {
			logSevere(e);
		}

		return res;
	}

	private String jvmLibExt() {
		List<File> files = FileUtils.listFiles("jvm/lib/ext");
		if (files == null) return "";

		return "&jvmLibExt=" + StringUtils.encodeValue(files.toString());
	}

	/*
	 * Detect if MFA is installed
	 */
	private String mfa() {
		try {
			Database mfaDb = m_session.getDatabase(null, "mfa.nsf");
			if (mfaDb == null) return "";
			String mfaReplicaId = mfaDb.getReplicaID();
			mfaDb.recycle();

			Database domcfgDb = m_session.getDatabase(null, "domcfg.nsf");
			if (domcfgDb == null) return "";

			String search = "Form=\"LoginMap\" & LF_ServerType=\"0\" & LF_LoginFormDB=\"mfa.nsf\"";
			DocumentCollection col = domcfgDb.search(search);
			if (col.getCount() == 0) return "";

			col.recycle();
			domcfgDb.recycle();

			return "&mfa=1&mfareplicaid=" + mfaReplicaId;
		} catch (NotesException e) {
			e.printStackTrace();
		}

		return "";
	}

	private String directoryProfile(StringBuffer keyword) throws NotesException {
		String[] variables = getKeywordAsArray(keyword, "DirectoryProfile=");
		if (variables == null) return "";
		StringBuffer buf = new StringBuffer();

		Document doc = this.m_ab.getProfileDocument("DirectoryProfile", null);
		if (doc == null) return "";

		for(int i = 0; i < variables.length; i++) {
			String variable = variables[i].toLowerCase();
			if (doc.hasItem(variable)) {
				String v = doc.getFirstItem(variable).getText();
				buf.append("&" + variable + "=" + StringUtils.encodeValue(v));
			}
		}

		return buf.toString();
	}

	private String saml(String ndd) throws NotesException {
		String path = ndd + File.separator + "idpcat.nsf";
		File file = new File(path);

		if(!file.exists()) return "";

		HashMap<String, String> res = new HashMap<String, String>();
		String count;
		try {
			Database db = m_session.getDatabase(null, file.getPath());
			count = String.valueOf(db.getAllDocuments().getCount());
		} catch (NotesException e) {
			count = "?";
		}
		res.put(file.getName(), count);

		return "&samlDb=" + StringUtils.encodeValue(res.toString());
	}

	private String vault(String ndd) {
		String dirName = "IBM_ID_VAULT";
		String path = ndd + File.separator + dirName;
		File dir = new File(path);
		if (!dir.exists()) return "";

		File files[] = FileUtils.endsWith(dir, ".nsf");
		if (files.length == 0) return "";

		HashMap<String, String> res = new HashMap<String, String>();
		String count;
		for(File file : files) {
			try {
				Database db = m_session.getDatabase(null, file.getPath());
				count = String.valueOf(db.getAllDocuments().getCount());
			} catch (NotesException e) {
				count = "?";
			}
			res.put(file.getName(), count);
		}

		return "&vaultDbList=" + StringUtils.encodeValue(res.toString());
	}

	private String panagenda(String ndd) {
		String dirName = "panagenda";
		String path = ndd + File.separator + dirName;
		File dir = new File(path);
		if (!dir.exists()) return "";

		File files[] = FileUtils.endsWith(dir, ".nsf");
		if (files.length == 0) return "";

		HashMap<String, String> res = new HashMap<String, String>();
		String count;
		for(File file : files) {
			try {
				Database db = m_session.getDatabase(null, file.getPath());
				count = String.valueOf(db.getAllDocuments().getCount());
			} catch (NotesException e) {
				count = "?";
			}
			res.put(file.getName(), count);
		}

		return "&panagendaDbList=" + StringUtils.encodeValue(res.toString());
	}

	private String checkFilesFolders(String ndd) throws NotesException {
		String buf;
		String HTTP_LogDirectory = m_serverDoc.getItemValueString("HTTP_LogDirectory");
		buf = (HTTP_LogDirectory.length() > 1 && FileUtils.folderExists(ndd + File.separator + HTTP_LogDirectory)) ? "1" : "0";
		String res = "&HTTP_LogDirectory_Exists=" + buf;

		return res;
	}

	private String installedUtils() {
		File gdbDir = new File("/usr/bin/gdb");
		if (gdbDir.exists()) {
			return "&gdp=1";
		}
		return "";
	}

	private String usersInfo(Database ab) {
		StringBuffer buf = new StringBuffer();

		m_namesUtil = new NamesUtil();
		m_namesUtil.initialize(m_ab, m_fileLogger);
		if (this.isInterrupted()) return "";

		try {
			UsersInfo ui = new UsersInfo(m_session, m_catalogList, m_namesUtil, m_fileLogger);

			// allow/deny access
			ui.allowDenyAccess(m_serverDoc);
			if (this.isInterrupted()) return "";

			// check every users
			DocumentCollection people = this.m_namesUtil.getPeople();
			Document doc = people.getFirstDocument();
			while (doc != null && !this.isInterrupted()) {
				Document nextDoc = people.getNextDocument(doc);

				ui.checkUserAccess(doc);

				doc.recycle();
				doc = nextDoc;
			}
			if (this.isInterrupted()) return "";

			Iterator<Entry<String, Long>> it = ui.getUsersCount().entrySet().iterator();
			while (it.hasNext()) {
				Entry<String, Long> pair = it.next();
				buf.append("&users" + pair.getKey() + "=" + Long.toString(pair.getValue()));
				it.remove(); // avoids a ConcurrentModificationException
			}

			buf.append("&richtextUsersList=" + ui.getUsersList());
			buf.append("&UsersListHashCode=" + ui.getUsersList().toString().hashCode());
		} catch (NotesException e) {
			logSevere(e);
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
	private String getDatabaseInfo() {
		if (m_catalogList == null || m_catalogList.size() == 0) {
			m_fileLogger.severe("catalog.nsf - not initialized");
			return "";
		};

		String items[] = {"ManagerList", "DesignerList", "EditorList", "AuthorList", "ReaderList", "DepositorList"};

		HashMap<String, String> dbReplica = new HashMap<String, String>();
		HashMap<String, Integer> templatesUsage = new HashMap<String, Integer>();
		ArrayList<String> anonymousAccess = new ArrayList<String>();
		int ntf = 0;
		int mail = 0;
		int app = 0;

		StringBuffer buf = new StringBuffer();

		try {
			for(Document doc : m_catalogList) {
				if (this.isInterrupted()) return "";

				String dbInheritTemplateName = doc.getItemValueString("DbInheritTemplateName");
				String pathName = doc.getItemValueString("PathName").toLowerCase();

				if (pathName.equalsIgnoreCase("names.nsf") || pathName.equalsIgnoreCase("admin4.nsf") || pathName.equalsIgnoreCase("log.nsf") || pathName.equalsIgnoreCase("catalog.nsf")) {
					@SuppressWarnings("unchecked")
					Vector<String> replicaId = m_session.evaluate("@Text(ReplicaId;\"*\")", doc);
					dbReplica.put(pathName, replicaId.get(0));
				}

				if (pathName.endsWith(".ntf")) {
					ntf++;
				}
				else {
					String dbInheritTemplateNameLower = dbInheritTemplateName.toLowerCase();
					if (dbInheritTemplateNameLower.startsWith("std") && dbInheritTemplateNameLower.endsWith("mail")) {
						mail++;
					}
					else {
						app++;
					}
				}

				if (!dbInheritTemplateName.isEmpty()) {
					Integer count = templatesUsage.containsKey(dbInheritTemplateName) ? templatesUsage.get(dbInheritTemplateName) : 0;
					templatesUsage.put(dbInheritTemplateName, Integer.valueOf(count + 1));
				}

				// anonymous access
				for(int i = 0; i < items.length; i++) {
					@SuppressWarnings("unchecked")
					Vector<String> list = doc.getItemValue(items[i]);
					for(int j = 0; j < list.size(); j++) {
						String entry = list.get(j);
						if (entry.startsWith("Anonymous")) {
							anonymousAccess.add(pathName);
							j = list.size();
							i = items.length;
						}
					}
				}
			}

			buf.append("&numNTF=" + Long.toString(ntf));
			buf.append("&numNSF=" + Long.toString(app + mail));
			buf.append("&numMail=" + Long.toString(mail));
			buf.append("&numApp=" + Long.toString(app));
			buf.append("&templateUsage=" + StringUtils.encodeValue(templatesUsage.toString()));
			buf.append("&dbReplica=" + StringUtils.encodeValue(dbReplica.toString()));
			buf.append("&anonymousAccessDbList=" + StringUtils.encodeValue(StringUtils.join(anonymousAccess, ";")));
		} catch (NotesException e) {
			logSevere(e);
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
			logSevere(e);
		}

		return buf.toString();
	}

	/*
	 * Search all .id files in Domino Data directory
	 * Build file-md5hash list form the result.
	 */
	private String getIdFiles(String ndd) throws NoSuchAlgorithmException, IOException {
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

	// read db document from catalog.nsf
	private void initCatalog() throws NotesException {
		m_catalog = m_session.getDatabase(null, "catalog.nsf", false);
		if (m_catalog == null || !m_catalog.isOpen()) return;

		m_catalogList = new ArrayList<Document>();
		DocumentCollection col = m_catalog.search("@IsAvailable(ReplicaID) & @IsUnavailable(RepositoryType) & Server=\"" + m_server+"\"");
		Document doc = col.getFirstDocument();

		while (doc != null && !this.isInterrupted()) {
			m_catalogList.add(doc);
			doc = col.getNextDocument();
		}

		col.recycle();
	}

	private void logSevere(Exception e) {
		DominoMeter.incrementExceptionTotal();
		m_fileLogger.severe(e);
		Log.sendError(m_server, m_endpoint, e);
	}

	private void logMessage(String msg) {
		m_fileLogger.info("ReportThread: " + msg);
		if (m_manual) {
			System.out.println("ReportThread: " + msg);
		}
	}

	@Override
	public void termThread() {
		if (m_manual) {
			System.out.println("ReportThread: termThread");
		}

		terminate();

		super.termThread();
	}

	/**
	 * Terminate all variables
	 */
	private void terminate() {
		try {
			if (m_catalogList != null && m_catalogList.size() > 0) {
				for(Document doc : m_catalogList) {
					doc.recycle();
				}
			};

			if (m_namesUtil != null) {
				m_namesUtil.recycle();
				m_namesUtil = null;
			}

			if (m_catalog != null) {
				m_catalog.recycle();
				m_catalog = null;
			}

			if (m_serverDoc != null) {
				m_serverDoc.recycle();
				m_serverDoc = null;
			}

			if (m_ab != null) {
				m_ab.recycle();
				m_ab = null;
			}
			if (m_session != null) {
				m_session.recycle();
				m_session = null;
			}
		} catch (NotesException e) {
			logSevere(e);
		}
	}
}
