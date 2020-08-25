import java.io.IOException;
import java.util.Date;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class DataCollector {
	private Session m_session = null;
	private Database m_database = null;
	private String m_endpoint = null;
	private String m_server = "";
	private String m_version = "";
	private Date m_startDate = null;
	
	public DataCollector(Session session, String endpoint, String server, String version) {
		m_session = session;
		m_endpoint = endpoint;
		m_server = server;
		m_version = version;
		m_startDate = new Date();
	}

	// TODO: session.getAddressBooks()
	private Database getAddressBook() throws NotesException {
		if (m_database == null) {
			m_database = m_session.getDatabase(m_session.getServerName(), "names.nsf");
		}
		return m_database;
	}
	
	public boolean send() throws NotesException {
		Database database = getAddressBook();
		if (database == null) {
			return false;
		}
		
		View view = database.getView("($People)");
		long count = view.getAllEntries().getCount();
		
		String statOS = System.getProperty("os.version", "n/a") + " (" + System.getProperty("os.name", "n/a") + ")";
		String statJavaVersion = System.getProperty("java.version", "n/a") + " (" + System.getProperty("java.vendor", "n/a") + ")";
		
		StringBuffer url = new StringBuffer(m_endpoint);
		url.append("/config?openagent");
		url.append("&server=" + m_server);	// key
		
		url.append("&addinVersion=" + m_version);
		url.append("&addinStartDate=" + Long.toString(m_startDate.getTime()));

		url.append("&usercount=" + Long.toString(count));
		url.append("&os=" + RESTClient.encodeValue(statOS));
		url.append("&java=" + RESTClient.encodeValue(statJavaVersion));
		
		try {
			return RESTClient.sendPOST(url.toString());
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
}