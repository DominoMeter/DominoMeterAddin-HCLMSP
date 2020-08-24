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
	
	public DataCollector(Session session, String endpoint, String server, String version) {
		m_session = session;
		m_endpoint = endpoint;
		m_server = server;
		m_version = version;
	}

	// TODO: session.getAddressBooks()
	private Database getAddressBook() throws NotesException {
		if (m_database == null) {
			m_database = m_session.getDatabase(m_session.getServerName(), "names.nsf");
		}
		return m_database;
	}
	
	public boolean send() throws Exception {
		View view = this.getAddressBook().getView("($People)");
		long count = view.getAllEntries().getCount();
		
		String url = m_endpoint + "/config?openagent&server="+m_server+"&version=" + m_version + "&usercount=" + Long.toString(count);
		System.out.println("DataCollector " + url);

		return RESTClient.sendPOST(url);
	}
}
