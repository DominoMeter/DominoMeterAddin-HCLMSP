import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class DatabaseCounter {
	private Session m_session;
	private int m_ntf;
	private int m_app;
	private int m_mail;

	public DatabaseCounter(Session session) {
		m_session = session;
	}

	private void resetCounters() {
		m_ntf = 0;
		m_app = 0;
		m_mail = 0;
	}

	/*
	 * count NSF, NTF, Mail and App for defined Application server
	 */
	public boolean count(String serverName) throws NotesException {
		Database catalogDb = m_session.getDatabase(serverName, "catalog.nsf");
		View replicaId = catalogDb.getView("($ReplicaId)");

		resetCounters();

		// File
		Document doc = replicaId.getFirstDocument();
		while (doc != null) {
			String server = doc.getItemValueString("Server").toLowerCase();
			if (serverName.equalsIgnoreCase(server)) {
				String pathName = doc.getItemValueString("PathName").toLowerCase();
				if (pathName.endsWith(".ntf")) {
					m_ntf++;
				}
				else {
					String dbInheritTemplateName = doc.getItemValueString("DbInheritTemplateName").toLowerCase();
					if (dbInheritTemplateName.startsWith("std") && dbInheritTemplateName.endsWith("mail")) {
						m_mail++;
					}
					else {
						m_app++;
					}
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
		return m_app + m_mail;
	}

	public int getMail() {
		return m_mail;
	}

	public int getApp() {
		return m_app;
	}
}
