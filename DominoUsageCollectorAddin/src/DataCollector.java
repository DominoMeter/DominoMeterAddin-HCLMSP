import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

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
	
	public boolean send() throws Exception {
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
		url.append("&addinStartDate=" + toISODateUTC(m_startDate));

		url.append("&usercount=" + Long.toString(count));
		url.append("&os=" + RESTClient.encodeValue(statOS));
		url.append("&java=" + RESTClient.encodeValue(statJavaVersion));
		
		return RESTClient.sendPOST(url.toString());
	}
	
	
	/**
	 * Convert Java Date to ISO 8601 UTC string
	 *  
	 * Note: This method is also called by the JAddinThread and the user add-in
	 * 
	 * @param date Java Date object
	 * @return Formatted date in ISO format ("yyyy-mm-ddThh:mm:ssZ")
	 */
	static synchronized final String toISODateUTC(Date date) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		return (dateFormat.format(date));	
	}
}