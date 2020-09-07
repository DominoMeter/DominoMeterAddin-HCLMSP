import java.io.IOException;
import java.util.Date;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class DataCollector {
	private Session m_session = null;
	private Database m_database = null;
	private String m_endpoint = null;
	private String m_server = "";
	private String m_version = "";
	
	public DataCollector(Session session, String endpoint, String server, String version) {
		m_session = session;
		m_endpoint = endpoint;
		m_server = server;
		m_version = version;
	}

	private Database getAddressBook() throws NotesException {
		if (m_database == null) {
			m_database = m_session.getDatabase(m_session.getServerName(), "names.nsf");
		}
		return m_database;
	}
	
	public boolean send() throws NotesException {
		Date dateStart = new Date();
		
		Database database = getAddressBook();
		if (database == null) {
			return false;
		}
		
		StringBuffer urlParameters = new StringBuffer("&server=" + m_server);
		
		// counters: nsf, ntf, mail, app
		DatabaseCounter dbCounter = new DatabaseCounter(m_session);
		if (dbCounter.count()) {
			urlParameters.append("&numNTF=" + Long.toString(dbCounter.getNTF()));
			urlParameters.append("&numNSF=" + Long.toString(dbCounter.getNSF()));
			urlParameters.append("&numMail=" + Long.toString(dbCounter.getMail()));
			urlParameters.append("&numApp=" + Long.toString(dbCounter.getApp()));
		}
		
		// user license
		View view = database.getView("($People)");
		long count = view.getAllEntries().getCount();

		// dir assistance
		String da = "";
		Document serverDoc = database.getView("($ServersLookup)").getDocumentByKey(m_server, true);
		if (serverDoc != null) {
			da = serverDoc.getItemValueString("MasterAddressBook");
		}

		String statOS = System.getProperty("os.version", "n/a") + " (" + System.getProperty("os.name", "n/a") + ")";
		String statJavaVersion = System.getProperty("java.version", "n/a") + " (" + System.getProperty("java.vendor", "n/a") + ")";
		
		StringBuffer url = new StringBuffer(m_endpoint);
		url.append("/config?openagent");
		
		urlParameters.append("&addinVersion=" + m_version);

		urlParameters.append("&usercount=" + Long.toString(count));
		urlParameters.append("&da=" + da);
		
		// system data
		urlParameters.append("&os=" + RESTClient.encodeValue(statOS));
		urlParameters.append("&java=" + RESTClient.encodeValue(statJavaVersion));

		// to measure how long it takes to calculate needed data
		urlParameters.append("&timeStart=" + dateStart.getTime());
		urlParameters.append("&timeEnd=" + new Date().getTime());
		
		try {
			RESTClient.sendPOST(url.toString(), urlParameters.toString());
			return true;
		} catch (IOException e) {
			System.out.println("POST failed " + url);
			return false;
		}
	}
	
}