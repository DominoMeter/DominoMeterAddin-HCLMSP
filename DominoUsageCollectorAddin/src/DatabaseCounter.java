import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class DatabaseCounter {
	private Session m_session;
	private int m_ntf;
	private int m_nsf;
	private int m_mail;

	public DatabaseCounter(Session session) {
		m_session = session;
		resetCounters();
	}
	
	private void resetCounters() {
		m_ntf = 0;
		m_nsf = 0;
		m_mail = 0;
	}
	
	public boolean count() throws NotesException {
		Database catalogDb = m_session.getDatabase(m_session.getServerName(), "catalog.nsf");
		View replicaId = catalogDb.getView("($ReplicaId)");
		
		resetCounters();
		
		// File
		Document doc = replicaId.getFirstDocument();
		while (doc != null) {
			String pathName = doc.getItemValueString("PathName").toLowerCase();
			if (pathName.endsWith(".ntf")) {
				m_ntf++;
			}
			else {
				m_nsf++;
				
				String dbInheritTemplateName = doc.getItemValueString("DbInheritTemplateName").toLowerCase();
				if (dbInheritTemplateName.startsWith("std") && dbInheritTemplateName.endsWith("mail")) {
					m_mail++;
				}
			}
			
			doc = replicaId.getNextDocument(doc);
		}
		
		return true;
	}

	public int getNTF() {
		return m_ntf;
	}

	public int getNSF() {
		return m_nsf;
	}
	
	public int getMail() {
		return m_mail;
	}
	
	public int getApp() {
		return m_nsf - m_mail;
	}

}
