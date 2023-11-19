import java.io.BufferedReader;
import java.io.File;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.Vector;

import javax.net.ssl.SSLContext;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.Item;
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
import net.prominic.gja_v084.GLogger;
import net.prominic.io.Bash;
import net.prominic.io.EchoClient;
import net.prominic.io.RESTClient;
import net.prominic.util.FileUtils;
import net.prominic.util.HostUtils;
import net.prominic.util.MD5Checksum;
import net.prominic.util.StringUtils;

public class ReportThread extends NotesThread {
	private String m_server;
	private String m_endpoint;
	private String m_version;
	private GLogger m_fileLogger;
	private boolean m_manual = false;
	private boolean m_firstRun = false;
	
	private Session m_session = null;
	private Database m_ab = null;
	private Database m_catalog = null;
	private ArrayList<Document> m_catalogList = null;
	private Document m_serverDoc = null;

	public ReportThread(String server, String endpoint, String version, GLogger fileLogger) {
		m_server = server;
		m_endpoint = endpoint;
		m_version = version;
		m_fileLogger = fileLogger;
	}
	
	@SuppressWarnings("unchecked")
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

			boolean isLinux = System.getProperty("os.name").equalsIgnoreCase("Linux");
			String ndd = m_session.getEnvironmentString("Directory", true);

			// pre: trace connection
			ArrayList<String> connection = traceConnection();
			if (this.isInterrupted()) return;

			// 1. initialize data for report
			Date stepStart = new Date();
			StringBuffer data = new StringBuffer();
			StringBuffer keyword = Keyword.getValue(m_endpoint, m_server, "all");
			data.append("numStep1=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 2. users
			stepStart = new Date();
			data.append(usersInfo());
			data.append("&numStep2=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 3. $conflict
			stepStart = new Date();
			data.append(conflicts());
			data.append("&numStep3=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 4. databases
			stepStart = new Date();
			data.append(getDatabaseInfo());
			data.append("&numStep4=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 5. dir assistance
			stepStart = new Date();
			data.append(getDA());
			data.append("&numStep5=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 6. system data
			stepStart = new Date();
			if (firstRun()) {
				data.append(getSystemInfoOnFirstRun(isLinux));	
			}
			data.append(getSystemInfo());
			data.append("&numStep6=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 7. notes.ini, we could get variables using API call
			stepStart = new Date();
			data.append(NotesINI(keyword));
			data.append("&numStep7=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 8. server document items
			stepStart = new Date();
			data.append(getServerItems(keyword));
			data.append("&numStep8=" + Long.toString(new Date().getTime() - stepStart.getTime()));
			if (this.isInterrupted()) return;

			// 9. config document
			stepStart = new Date();
			data.append(Config(keyword));
			data.append("&numStep9=" + Long.toString(new Date().getTime() - stepStart.getTime()));

			// 10. program documents
			data.append("&programs=" + StringUtils.encodeValue(getProgram(m_ab)));
			if (this.isInterrupted()) return;

			// 11. id files on server
			String idFiles = getIdFiles(ndd);
			if (!idFiles.isEmpty()) {
				data.append("&idfiles=" + idFiles);
			}
			if (this.isInterrupted()) return;

			// 12. services
			String services = this.getServices();
			if (!services.isEmpty()) {
				data.append(services);
			}
			if (this.isInterrupted()) return;

			// 13. Linux specific data
			if (isLinux) {
				data.append(this.getLinuxInfo());
			}
			if (this.isInterrupted()) return;

			// 14. Get 10 last NSD files from IBM_TECHNICAL_SUPPORT folder
			String nsd = getNSD(ndd);
			if (!nsd.isEmpty()) {
				data.append(nsd);
			}
			if (this.isInterrupted()) return;

			// 15. In case if connection is done via HTTP we still need to check if HTTPS works
			data.append(checkHTTPSConnection());
			if (this.isInterrupted()) return;

			// 16. Jedi
			if (isLinux) {
				data.append(jedi());
			}
			if (this.isInterrupted()) return;

			// 17. installed utils (f.x. gdp)
			if (isLinux) {
				data.append(installedUtils());
			}
			if (this.isInterrupted()) return;

			// 18. check if folder(s) exist (extend with other files/folder if needed)
			data.append(checkFilesFolders(ndd));
			if (this.isInterrupted()) return;

			// 19. IDVault check
			data.append(vault(ndd));
			if (this.isInterrupted()) return;

			// 20. Panagenda
			data.append(panagenda(ndd));
			if (this.isInterrupted()) return;

			// 21. SAML
			data.append(saml(ndd));
			if (this.isInterrupted()) return;

			// 22. Directory Profile
			data.append(directoryProfile(keyword));
			if (this.isInterrupted()) return;

			// 23. MFA installed?
			data.append(mfa());
			if (this.isInterrupted()) return;

			// 24. jvm/lib/ext file lists
			data.append(jvmLibExt());
			if (this.isInterrupted()) return;

			// 25. file content: java.policy, java.security, /etc/hosts, /etc/resolv.conf
			data.append(filesContent(isLinux));
			if (this.isInterrupted()) return;

			// 26. parse trace result from noteslong (or console.log)
			data.append(parseTraceOutput(ndd, connection, isLinux));
			if (this.isInterrupted()) return;

			// 27. entitlementtrack.ncf
			data.append(entitlement(ndd));
			if (this.isInterrupted()) return;

			// 99. error counter and last error
			long exception_total = DominoMeter.getExceptionTotal();
			data.append("&numErrorCounter=" + String.valueOf(exception_total));
			String exception_last = DominoMeter.getExceptionLast();
			if (exception_last != null) {
				data.append("&exceptionLast=" + exception_last);
			}

			// 100. to measure how long it takes to calculate needed data
			String numDuration = Long.toString(new Date().getTime() - dateStart.getTime());
			data.append("&numDuration=" + numDuration);
			if (this.isInterrupted()) return;

			// report
			String url = m_endpoint.concat("/report?openagent");
			StringBuffer res = RESTClient.sendPOST(url, "application/x-www-form-urlencoded", data.toString());
			logMessage("finished report (" + res.toString() + ")");

			// 1. JSON: Documents
			JSONObject json = new JSONObject();
			json.put("server", m_server);
			JSONArray docs = documents(keyword);
			json.put("docs", docs);

			// documents
			String urldocs = m_endpoint.concat("/docs?openagent");
			res = RESTClient.sendPOST(urldocs, "application/json", json.toString());
			logMessage("finished docs (" + res.toString() + ")");
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
	
	public boolean firstRun() {
		return m_firstRun;
	}

	public void firstRun(boolean flag) {
		this.m_firstRun = flag;
	}
	
	public boolean manual() {
		return m_manual;
	}

	public void manual(boolean flag) {
		this.m_manual = flag;
	}

	// to avoid extra lookups
	@SuppressWarnings("unchecked")
	private void extendWebSite(JSONObject obj) {
		JSONArray sslExpireDate = new JSONArray();
		JSONArray sslMessage = new JSONArray();

		if (!obj.containsKey("isiteadrs")) return;
		ArrayList<String> isiteadrs = (ArrayList<String>) obj.get("isiteadrs");
		if (isiteadrs==null) return;

		for(String host : isiteadrs) {
			int indexOfDoubleSlash = host.indexOf("://");
			if (indexOfDoubleSlash != -1) {
				host = host.substring(indexOfDoubleSlash + 3);
			}

			String message = "";
			Date date = null;
			try {
				date = HostUtils.ExpireDate(host);
			} catch (Exception e) {
				message = e.getMessage();
				if (message == null || message.isEmpty()) {
					message = "an undefined exception was thrown";
				}
			}

			if (date != null) {
				sslExpireDate.add(date.getTime());				
			}
			else {
				sslExpireDate.add(0);
			}
			sslMessage.add(message);
		}

		obj.put("sslExpire", sslExpireDate);
		obj.put("sslMessage", sslMessage);
	}

	// entitlementtrack.ncf
	private String entitlement(String ndd) {
		String path = ndd + File.separator + "entitlementtrack.ncf";
		File file = new File(path);

		if(!file.exists()) return "";
		return "&entitlementtrack=1";
	}

	@SuppressWarnings("unchecked")
	private JSONArray documents(StringBuffer keyword) {
		JSONArray array = new JSONArray();
		try {
			String docs[] = getKeywordAsArray(keyword, "Docs136");
			for (int i = 0; i < docs.length; i++) {
				String keys = docs[i];
				String[] parts = keys.split("\\~");
				String variables[] = getKeywordAsArray(keyword, parts[0]);

				String search = parts[1];
				search = search.replace("$server", m_server);	
				DocumentCollection col = m_ab.search(search);

				Document doc = col.getFirstDocument();
				while (doc != null) {
					JSONObject obj = DocItemsJSON(doc, variables);

					// add expire ssl data to website documents
					if (parts[0].equalsIgnoreCase("WebSite")) {
						extendWebSite(obj);
					}
					obj.put("unid", doc.getUniversalID());
					array.add(obj);	
					doc = col.getNextDocument();
				}
			}
		} catch (NotesException e) {
			logSevere(e);
		}

		return array;
	}

	// send trace <server> command to console
	private ArrayList<String> traceConnection() {
		ArrayList<String> res = new ArrayList<String>();
		try {
			String search = "Type=\"Connection\" & Source=\""+m_server+"\" & ConnectionType=\"0\":\"2\" & !RoutingTask=\"SMTP Mail Routing\" & RepTask=\"1\"";
			DocumentCollection col = m_ab.search(search);
			Document doc = col.getFirstDocument();
			while (doc!=null) {
				Document nextDoc = col.getNextDocument();
				String destination = doc.getItemValueString("Destination");

				res.add(destination);
				this.m_session.sendConsoleCommand("", "!trace " + destination);

				doc.recycle();
				doc = nextDoc;
			}

			col.recycle();
		} catch (Exception e) {
			logSevere(e);
		}

		return res;
	}

	private String getLogFilePath(String ndd, boolean isLinux) {
		String res = null;

		// check if noteslog folder exists (must be present on all linux servers)
		File dir = new File(ndd + File.separator + "noteslog");
		if (dir.exists() && dir.isDirectory()) {
			File files[] = FileUtils.startsWith(dir, "notes.log");			
			if (files.length>0) {
				files = FileUtils.sortFilesByModified(files, false);
				res = files[0].getPath();
			}
		}

		if (res == null) {
			res = ndd + File.separator + "IBM_TECHNICAL_SUPPORT" + File.separator + "console.log";
		}

		return res;
	}

	private String parseTraceOutput(String ndd, ArrayList<String> traceList, boolean isLinux) {
		StringBuilder res = new StringBuilder();

		try {
			if (traceList.size() == 0) return "";

			String filePath = getLogFilePath(ndd, isLinux);
			if (filePath==null || filePath.isEmpty()) return "";

			File file = new File(filePath);
			if (!file.exists()) return "";

			String year = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
			// read up to 1000 lines from bottom to top
			int maxLineCounter = 1000;
			// we want to find first N trace commands only
			int maxTrace = traceList.size();
			// console output (up to 1000 lines)
			LinkedList<String> fifo = new LinkedList<String>();

			RandomAccessFile raf = new RandomAccessFile(file, "r");
			long fileLength = file.length();
			StringBuilder bufLine = new StringBuilder();
			for (long filePointer = fileLength - 1; filePointer >= 0 && maxLineCounter>0 && maxTrace>0; filePointer--) {
				raf.seek(filePointer);
				int readByte = raf.readByte();
				if (readByte=='\r' || readByte=='\n') {
					if (bufLine.length() > 10) {
						bufLine.reverse();

						String line = bufLine.toString();

						// PROCESS: [10B8:0009-1900] Encryption is Disabled
						// SKIP: [1930:0008-0ED8] 23-03-2023 12:11:30   ...text...
						if (!line.contains(year)) {
							int bracketIndex = line.indexOf("]");
							if (bracketIndex != -1 && bracketIndex + 2 < line.length()) {
								line = line.substring(bracketIndex+2);
							}
							fifo.push(line);

							if (line.contains("Determining path to server")) {
								maxTrace--;
							}
						}

						maxLineCounter--;
					}
					bufLine = new StringBuilder();
				}
				else {
					bufLine.append((char) readByte);
				}
			}
			raf.close();

			maxTrace = traceList.size();
			for(int i=0; i<fifo.size() && maxTrace>0; i++) {
				String line = fifo.get(i);
				if (line.toString().contains("Determining path to server")) {
					i++;
					String server = line.substring(line.lastIndexOf(" ")+1);

					int maxLineResponse = 50;	// avoid sending big reports for 1 server
					maxTrace--;
					boolean traceFlag = true;
					StringBuilder traceRes = new StringBuilder();
					while (i<fifo.size() && traceFlag && maxLineResponse>0) {
						line = fifo.get(i);

						traceRes.append(line);
						maxLineResponse--;

						if (line.contains("Encryption is") || line.contains("Unable to find") || line.contains("This server is not permitted to passthru to the specified server")) {
							traceFlag = false;
						}
						else {
							traceRes.append(";");
						}
						i++;
					}

					res.append(server);
					res.append("~");
					res.append(traceRes.toString());

					if (maxTrace>0) {
						res.append("|");
					}
				}
			}
		} catch (FileNotFoundException e) {
			this.logSevere(e);
		} catch (IOException e) {
			this.logSevere(e);
		} catch (Exception e) {
			this.logSevere(e);
		}

		return "&traceConnection=" + StringUtils.encodeValue(res.toString());
	}

	private String conflicts() {
		String res = "";

		try {
			DocumentCollection col = this.m_ab.search("@IsAvailable($Conflict)");

			int conflictPerson = 0;
			int conflictServer = 0;
			int conflictGroup = 0;
			int conflictProgram = 0;
			int conflictConnection = 0;
			int conflictServerConfig = 0;
			int conflictWebSite = 0;
			int conflictOther = 0;
			int conflictAll = col.getCount();

			Document doc = col.getFirstDocument();
			while(doc != null) {
				String type = doc.getItemValueString("Type");

				if ("Person".equalsIgnoreCase(type)) {
					conflictPerson++;
				}
				else if("Server".equalsIgnoreCase(type)) {
					conflictServer++;
				}
				else if("Group".equalsIgnoreCase(type)) {
					conflictGroup++;
				}
				else if("Program".equalsIgnoreCase(type)) {
					conflictProgram++;
				}
				else if("Connection".equalsIgnoreCase(type)) {
					conflictConnection++;
				}
				else if("ServerConfig".equalsIgnoreCase(type)) {
					conflictServerConfig++;
				}
				else if("WebSite".equalsIgnoreCase(type)) {
					conflictWebSite++;
				}
				else {
					conflictOther++;
				}

				doc = col.getNextDocument();
			}

			res = String.format("&numConflictPerson=%d&numConflictServer=%d&numConflictGroup=%d&numConflictProgram=%d&numConflictConnection=%d&numConflictServerConfig=%d&numConflictWebSite=%d&numConflictOther=%d&numConflictAll=%d", conflictPerson, conflictServer, conflictGroup, conflictProgram, conflictConnection, conflictServerConfig, conflictWebSite, conflictOther, conflictAll);
		} catch (NotesException e) {
			logSevere(e);
		}

		return res;
	}

	private String filesContent(boolean isLinux) {
		String res = "";

		try {
			String javaPolicyPath = "jvm/lib/security/java.policy";
			StringBuffer buf = FileUtils.readFileContentFilter(javaPolicyPath, new String[]{"//"}, true);
			if (buf != null) {
				res += "&richtextJavaPolicy=" + StringUtils.encodeValue(buf.toString());
				res += "&JavaPolicyMD5=" + MD5Checksum.getMD5Checksum(new File(javaPolicyPath));
			}

			String javaSecurityPath = "jvm/lib/security/java.security";
			buf = FileUtils.readFileContentFilter(javaSecurityPath, new String[]{"#"}, true);
			if (buf != null) {
				res += "&richtextJavaSecurity=" + StringUtils.encodeValue(buf.toString());
				res += "&JavaSecurityMD5=" + MD5Checksum.getMD5Checksum(new File(javaSecurityPath));
			}

			if (isLinux) {
				buf = FileUtils.readFileContent("/etc/resolv.conf");
				if (buf != null) {
					res += "&FileEtcResolvConf=" + StringUtils.encodeValue(buf.toString());
				}

				buf = FileUtils.readFileContent("/etc/hosts");
				if (buf != null) {
					res += "&FileEtcHosts=" + StringUtils.encodeValue(buf.toString());
				}

				buf = FileUtils.readFileContent("/etc/fstab");
				if (buf != null) {
					res += "&FileEtcFstab=" + StringUtils.encodeValue(buf.toString());
				}
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
			if (mfaDb == null || !mfaDb.isOpen()) return "";

			String mfaReplicaId = mfaDb.getReplicaID();
			mfaDb.recycle();

			Database domcfgDb = m_session.getDatabase(null, "domcfg.nsf");
			if (domcfgDb == null || !domcfgDb.isOpen()) return "";

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

	private String saml(String ndd) {
		try {
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

		} catch (Exception e) {
			logSevere(e);
		}

		return "";
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

	private String checkFilesFolders(String ndd) {
		String res = "";
		try {
			String HTTP_LogDirectory = m_serverDoc.getItemValueString("HTTP_LogDirectory");
			String buf = (HTTP_LogDirectory.length() > 1 && FileUtils.folderExists(ndd + File.separator + HTTP_LogDirectory)) ? "1" : "0";
			res = "&HTTP_LogDirectory_Exists=" + buf;
		} catch (Exception e) {
			logSevere(e);
		}
		return res;
	}

	private String installedUtils() {
		File gdbDir = new File("/usr/bin/gdb");
		if (gdbDir.exists()) {
			return "&gdp=1";
		}
		return "";
	}

	private String usersInfo() {
		StringBuffer buf = new StringBuffer();

		try {
			// process all Directory Catalogs
			NamesUtil namesUtil = new NamesUtil(m_fileLogger);

			@SuppressWarnings("unchecked")
			Vector<Database> databases = m_session.getAddressBooks();

			// if there are more than 1 dir registered, let's scan it as well
			for(int i=0; i<databases.size(); i++) {
				Database database = databases.get(i);
				if (database.getServer().equalsIgnoreCase(m_server)) {
					if (!database.isOpen()) database.open();

					// process additional address book
					namesUtil.addAddressBook(database);
					if (this.isInterrupted()) return "";
				}
			}

			UsersInfo ui = new UsersInfo(m_session, m_catalogList, namesUtil, m_fileLogger);

			// allow/deny access
			ui.allowDenyAccess(m_serverDoc);
			if (this.isInterrupted()) return "";

			// check every users
			HashMap<String, DocumentCollection> people = namesUtil.getPeople();
			for (Entry<String, DocumentCollection> set : people.entrySet()) {
				DocumentCollection col = set.getValue();
				Document doc = col.getFirstDocument();

				while (doc != null && !this.isInterrupted()) {
					Document nextDoc = col.getNextDocument(doc);

					ui.checkUserAccess(doc);

					doc.recycle();
					doc = nextDoc;
				}	
			}
			if (this.isInterrupted()) return "";

			// total users in every address book
			for (Entry<String, DocumentCollection> set : people.entrySet()) {
				DocumentCollection col = set.getValue();
				String key = set.getKey();
				key = key.contains(".") ? key.substring(0, key.indexOf(".")) : key;
				ui.usersCount().put(key, (long) col.getCount());
			}

			// create data
			Iterator<Entry<String, Long>> it = ui.usersCount().entrySet().iterator();
			while (it.hasNext()) {
				Entry<String, Long> pair = it.next();
				buf.append("&numUsers" + pair.getKey() + "=" + Long.toString(pair.getValue()));
				it.remove(); // avoids a ConcurrentModificationException
			}

			buf.append("&richtextUsersList=" + ui.getUsersList());
			buf.append("&UsersListHashCode=" + ui.getUsersList().toString().hashCode());

			if (ui.getAllowAccessWildCard()) {
				buf.append("&AllowAccessWildCard=1");
			}

			if (ui.getDenyAccessWildCard()) {
				buf.append("&DenyAccessWildCard=1");
			}

			// RECYCLE

			// recycle databases as we already processed them
			for(int i=0; i<databases.size(); i++) {
				Database database = databases.get(i);
				database.recycle();
			}

			// recycle DocumentCollections
			namesUtil.recycle();
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
	private String getNSD(String ndd) {
		try {
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
		} catch (Exception e) {
			logSevere(e);
		}

		return "";
	}

	/*
	 * Linux data
	 */
	private String getLinuxInfo() throws IOException {
		String ulimit = Bash.exec("ulimit -n");
		if (ulimit.isEmpty()) return "";

		return "&numUlimit=" + ulimit;
	}
	
	private String getSystemInfoOnFirstRun(boolean isLinux) {
		StringBuffer buf = new StringBuffer();

		// windows specific
		if (!isLinux) {
			try {
				String systeminfo = "";
				Process process = Runtime.getRuntime().exec("systeminfo /fo csv /nh");
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String line;
				while ((line = reader.readLine()) != null) {
					if (!line.isEmpty()) {
						systeminfo += line;
					}
				}
				reader.close();
				buf.append("&systeminfo=" + StringUtils.encodeValue(systeminfo));
			} catch (IOException e) {
				logSevere(e);
			}
		}

		try {
			buf.append("&osversion=" + System.getProperty("os.version", "n/a"));
			buf.append("&osname=" + StringUtils.encodeValue(System.getProperty("os.name", "n/a")));
			buf.append("&javaversion=" + System.getProperty("java.version", "n/a"));
			buf.append("&javavendor=" + System.getProperty("java.vendor", "n/a"));
			buf.append("&platform=" + StringUtils.encodeValue(m_session.getPlatform()));
			buf.append("&domino=" + m_session.getNotesVersion().trim());
			buf.append("&ostimezone=" + StringUtils.encodeValue(TimeZone.getDefault().getDisplayName()));
			buf.append("&username=" + System.getProperty("user.name", "n/a"));
			buf.append("&version=" + m_version);

			String host = "";
			try {
				InetAddress local = InetAddress.getLocalHost();
				host = local.getHostName();
			} catch (UnknownHostException e) {
				host = "n/a";
			}
			buf.append("&hostname=" + host);
		} catch (Exception e) {
			logSevere(e);
		}

		return buf.toString();
	}
	
	/*
	 * OS data
	 */
	private String getSystemInfo() {
		StringBuffer buf = new StringBuffer();

		try {
			buf.append("&server=" + StringUtils.encodeValue(m_server));
			buf.append("&endpoint=" + StringUtils.encodeValue(m_endpoint));
			buf.append("&templateVersion=" + getDatabaseVersionNumber(m_ab));

			SimpleDateFormat formatter = new SimpleDateFormat("z");
			String serverTimezone = formatter.format(new Date());
			buf.append("&serverTimezone=" + serverTimezone);

			String SSLcipher = "";
			String SupportedCipherSuites = "";
			try {
				SSLcipher = StringUtils.join(SSLContext.getDefault().getSupportedSSLParameters().getProtocols(), ";");
				SupportedCipherSuites = StringUtils.join(SSLContext.getDefault().getSocketFactory().getSupportedCipherSuites(), ";");
			} catch (NoSuchAlgorithmException e) {
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
		} catch (Exception e) {
			logSevere(e);
		}

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
				String pathName = doc.getItemValueString("PathName");

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

		if (!id.endsWith("=")) {
			id = id + "=";
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
	private String NotesINI(StringBuffer keyword) {
		StringBuffer buf = new StringBuffer();

		try {
			String[] variables = getKeywordAsArray(keyword, "Notes.ini");
			if (variables == null) return "";

			for(int i = 0; i < variables.length; i++) {
				String variable = variables[i].toLowerCase();
				String v = m_session.getEnvironmentString(variable, true);
				if (v.length() > 0) {
					buf.append("&" + variable + "=" + StringUtils.encodeValue(v));
				}
			}
		} catch (Exception e) {
			logSevere(e);
		}

		return buf.toString();
	}

	/*
	 * read variables from server document
	 */
	private String getServerItems(StringBuffer keyword) {
		String res = "";

		try {
			if (m_serverDoc == null) return "";
			res = DocItems(m_serverDoc, keyword, "Server");
		} catch (Exception e) {
			logSevere(e);
		}

		return res;
	}


	private String Config(StringBuffer keyword) {
		String res = "";
		try {
			View view = m_ab.getView("($ServerConfig)");
			Document doc = view.getDocumentByKey(m_server, true);
			if (doc==null) {
				doc = view.getDocumentByKey("*", true);
			}

			if (doc!=null) {
				res = DocItems(doc, keyword, "Config");
				doc.recycle();
			};

			view.recycle();
		} catch (Exception e) {
			logSevere(e);
		}

		return res;
	}

	private String directoryProfile(StringBuffer keyword) {
		String res = "";
		try {
			Document doc = this.m_ab.getProfileDocument("DirectoryProfile", null);
			if (doc == null) return "";

			if (doc != null) {
				res = DocItems(doc, keyword, "DirectoryProfile");
				doc.recycle();
			}
		} catch (Exception e) {
			logSevere(e);
		}

		return res;
	}

	private String DocItems(Document doc, StringBuffer keyword, String keywordId) {
		StringBuffer buf = new StringBuffer();
		try {
			String[] variables = getKeywordAsArray(keyword, keywordId);
			if (variables == null) return "";

			for(int i = 0; i < variables.length; i++) {
				String variable = variables[i].toLowerCase();
				if (doc.hasItem(variable)) {
					String v = doc.getFirstItem(variable).getText();
					buf.append("&" + variable + "=" + StringUtils.encodeValue(v));
				}
			}
		} catch (Exception e) {
			logSevere(e);
		}

		return buf.toString();
	}

	@SuppressWarnings("unchecked")
	private JSONObject DocItemsJSON(Document doc, String[] variables) {
		JSONObject obj = null;
		try {
			if (variables == null) return null;

			obj = new JSONObject();
			for(int i = 0; i < variables.length; i++) {
				String variable = variables[i].toLowerCase();
				if (doc.hasItem(variable)) {
					Item item = doc.getFirstItem(variable);
					JSONArray values = new JSONArray();

					int type = item.getType();
					if (type==Item.TEXT || type==Item.NUMBERS || type==Item.NAMES || type==Item.AUTHORS || type==Item.READERS) {
						Vector<?> itemValues = item.getValues();
						if (itemValues == null || itemValues.isEmpty()) {
							values.add("");
						}
						else {
							values.addAll(itemValues);
						}
					}
					else {
						values.add(item.getText());
					}

					obj.put(variable, values);
				}
			}
		} catch (Exception e) {
			logSevere(e);
		}

		return obj;
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

		try {
			// 1. show server
			if (this.isInterrupted()) return "";
			String console = m_session.sendConsoleCommand("", "!sh server");
			buf.append("&sh_server=" + StringUtils.encodeValue(console));

			// 2. show cluster
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

			// 3. show tasks
			if (this.isInterrupted()) return "";
			console = m_session.sendConsoleCommand("", "!sh tasks");
			buf.append("&sh_tasks=" + StringUtils.encodeValue(console));
			if (console.contains("Traveler")) {
				buf.append("&traveler=1");
			}
			if (console.contains("Sametime")) {
				buf.append("&sametime=1");
			}

			// 4. show stat Platform.LogicalDisk.*
			if (this.isInterrupted()) return "";
			console = m_session.sendConsoleCommand("", "!sh stat Platform.LogicalDisk.*");
			buf.append("&sh_stat=" + StringUtils.encodeValue(console));

			// 5. show heartbeat
			if (this.isInterrupted()) return "";
			console = m_session.sendConsoleCommand("", "!sh heartbeat");
			buf.append("&sh_heartbeat=" + StringUtils.encodeValue(console));
			if (console.contains("seconds")) {
				String elapsed_time = console.substring(console.lastIndexOf(":") + 2, console.lastIndexOf("seconds") - 1);
				buf.append("&numElapsedTime=" + elapsed_time);
			}

			//6. repair list missing
			console = m_session.sendConsoleCommand("", "!repair list missing");
			if (console.contains("[Missing]")) {
				StringBuffer data = new StringBuffer();

				String[] lines = console.split(System.getProperty("line.separator"));
				for (String line : lines) {
					if (line.contains("[Missing]")) {
						String filepath = line.substring(0, line.indexOf(","));
						if (data.length()>0) {
							data.append(";");
						}
						data.append(filepath);
					}
				}

				buf.append("&repairmissing=" + StringUtils.encodeValue(data.toString()));
			};
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
	 * check for DA and for trusted records there
	 */
	private String getDA() {
		String res = "";

		try {
			String da = m_serverDoc.getItemValueString("MasterAddressBook");
			if (da.isEmpty()) return "";
			res = "&daPath=1";

			Database dirDb = m_session.getDatabase(null, da);
			if (dirDb == null || !dirDb.isOpen()) return res;

			res += "&da=1";	// da defined

			DocumentCollection col = dirDb.search("Type=\"DirectoryAssistance\" & TrustedList=\"Yes\"", null, 1);
			res += col.getCount() > 0 ? "&daTrusted=1" : "";
		} catch (NotesException e) {
			e.printStackTrace();
		}

		return res;
	}

	private String getProgram(Database database) {
		StringBuffer buf = new StringBuffer();

		try {
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
		} catch (Exception e) {
			logSevere(e);
		}

		return buf.toString();
	}

	// read db document from catalog.nsf
	private void initCatalog() {
		try {
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
		} catch (Exception e) {
			logSevere(e);
		}
	}

	private void logSevere(Exception e) {
		DominoMeter.incrementExceptionTotal();
		DominoMeter.setExceptionLast(e);
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
