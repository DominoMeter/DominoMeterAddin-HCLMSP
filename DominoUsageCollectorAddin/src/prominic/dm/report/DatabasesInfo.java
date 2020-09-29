package prominic.dm.report;

import java.util.HashMap;

import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.Document;
import lotus.domino.NotesException;

public class DatabasesInfo {
	private Session m_session;
	private int m_ntf;
	private int m_app;
	private int m_mail;
	private HashMap<String, Integer> m_templatesUsage;

	public DatabasesInfo(Session session) {
		m_session = session;
	}

	private void resetCounters() {
		m_ntf = 0;
		m_app = 0;
		m_mail = 0;
		m_templatesUsage = new HashMap<String, Integer>();
	}

	/*
	 * count NSF, NTF, Mail and App for defined Application server
	 */
	public boolean process(String serverName) throws NotesException {
		Database catalogDb = m_session.getDatabase(serverName, "catalog.nsf");
		View replicaId = catalogDb.getView("($ReplicaId)");

		resetCounters();

		// File
		Document doc = replicaId.getFirstDocument();
		while (doc != null) {
			String server = doc.getItemValueString("Server").toLowerCase();
			String dbInheritTemplateName = doc.getItemValueString("DbInheritTemplateName");
			
			if (serverName.equalsIgnoreCase(server)) {
				String pathName = doc.getItemValueString("PathName").toLowerCase();
				if (pathName.endsWith(".ntf")) {
					m_ntf++;
				}
				else {
					String dbInheritTemplateNameLower = dbInheritTemplateName.toLowerCase();
					if (dbInheritTemplateNameLower.startsWith("std") && dbInheritTemplateNameLower.endsWith("mail")) {
						m_mail++;
					}
					else {
						m_app++;
					}
				}
			}

			if (!dbInheritTemplateName.isEmpty()) {
				Integer count = m_templatesUsage.containsKey(dbInheritTemplateName) ? m_templatesUsage.get(dbInheritTemplateName) : 0;
				m_templatesUsage.put(dbInheritTemplateName, Integer.valueOf(count + 1));	
			}

			doc = replicaId.getNextDocument(doc);
		}

		replicaId.recycle();
		catalogDb.recycle();
		
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
	
	public HashMap<String, Integer> getTemplateUsage() {
		return m_templatesUsage;
	}
}
