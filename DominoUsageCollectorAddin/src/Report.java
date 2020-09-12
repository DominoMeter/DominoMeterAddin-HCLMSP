import java.util.Date;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

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

			String server = m_session.getServerName();
			StringBuffer urlParameters = new StringBuffer("server=" + RESTClient.encodeValue(server));

			// info about databases setup
			DatabasesInfo dbInfo = new DatabasesInfo(m_session);
			if (dbInfo.process(server)) {
				urlParameters.append("&numNTF=" + Long.toString(dbInfo.getNTF()));
				urlParameters.append("&numNSF=" + Long.toString(dbInfo.getNSF()));
				urlParameters.append("&numMail=" + Long.toString(dbInfo.getMail()));
				urlParameters.append("&numApp=" + Long.toString(dbInfo.getApp()));
				urlParameters.append("&templateUsage=" + RESTClient.encodeValue(dbInfo.getTemplateUsage().toString()));
			}

			// user license
			View view = database.getView("People");
			long count = view.getAllEntries().getCount();

			// dir assistance
			boolean da = isDA();

			String statOS = System.getProperty("os.version", "n/a") + " (" + System.getProperty("os.name", "n/a") + ")";
			String statJavaVersion = System.getProperty("java.version", "n/a") + " (" + System.getProperty("java.vendor", "n/a") + ")";
			String statDomino = m_session.getNotesVersion();

			urlParameters.append("&addinVersion=" + m_version);

			urlParameters.append("&usercount=" + Long.toString(count));
			if (da) {
				urlParameters.append("&da=1");
			}

			// system data
			urlParameters.append("&os=" + RESTClient.encodeValue(statOS));
			urlParameters.append("&java=" + RESTClient.encodeValue(statJavaVersion));
			urlParameters.append("&domino=" + RESTClient.encodeValue(statDomino));

			// notes.ini
			StringBuffer keyword = Keyword.getValue(m_endpoint, server, "Notes.ini");
			if (keyword != null && !keyword.toString().isEmpty()) {
				String[] iniVariables = keyword.toString().split(";");
				for(int i = 0; i < iniVariables.length; i++) {
					String variable = iniVariables[i].toLowerCase();
					String iniValue = m_session.getEnvironmentString(variable, true);
					urlParameters.append("&ni_" + variable + "=" + RESTClient.encodeValue(iniValue));
				}
			}	

			// to measure how long it takes to calculate needed data
			urlParameters.append("&timeStart=" + dateStart.getTime());
			urlParameters.append("&timeEnd=" + new Date().getTime());

			StringBuffer res = RESTClient.sendPOST(url, urlParameters.toString());
			return res.toString().equals("OK");
		} catch (Exception e) {
			return false;
		}
	}
}