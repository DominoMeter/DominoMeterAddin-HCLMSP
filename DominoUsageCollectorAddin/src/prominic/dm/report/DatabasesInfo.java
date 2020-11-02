package prominic.dm.report;

import java.util.ArrayList;
import java.util.HashMap;

import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class DatabasesInfo {
	private int m_ntf;
	private int m_app;
	private int m_mail;
	private HashMap<String, Integer> m_templatesUsage;
	private ArrayList<String> m_disableTransactionLogging;
	private ArrayList<String> m_openFailedDatabase;;

	private void resetCounters() {
		m_ntf = 0;
		m_app = 0;
		m_mail = 0;
		m_templatesUsage = new HashMap<String, Integer>();
		m_disableTransactionLogging = new ArrayList<String>();
		m_openFailedDatabase = new ArrayList<String>();
	}

	/*
	 * count NSF, NTF, Mail and App for defined Application server
	 */
	public boolean process(Session session, String serverName) throws NotesException {
		Database catalogDb = session.getDatabase("", "catalog.nsf");
		View replicaId = catalogDb.getView("($ReplicaId)");
		replicaId.refresh();
		
		resetCounters();

		// File
		Document doc = replicaId.getFirstDocument();
		while (doc != null) {
			Document nextDoc = replicaId.getNextDocument(doc);
			String server = doc.getItemValueString("Server");
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

				Database database = session.getDatabase("", pathName);
				if (database.isOpen()) {
					if (database.getOption(Database.DBOPT_NOTRANSACTIONLOGGING)) {
						m_disableTransactionLogging.add(pathName);
					}
				}
				else {
					m_openFailedDatabase.add(pathName);
				};
				
				// catalog.nsf will be recycled later (we use a view from it).
				if (!database.getFilePath().toLowerCase().equals("catalog.nsf")) {
					database.recycle();
				}
			}

			if (!dbInheritTemplateName.isEmpty()) {
				Integer count = m_templatesUsage.containsKey(dbInheritTemplateName) ? m_templatesUsage.get(dbInheritTemplateName) : 0;
				m_templatesUsage.put(dbInheritTemplateName, Integer.valueOf(count + 1));	
			}

			doc.recycle();
			doc = nextDoc;
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

	public ArrayList<String> getDisableTransactionLogging() {
		return this.m_disableTransactionLogging;
	}

	public ArrayList<String> getOpenFailedDatabase() {
		return this.m_openFailedDatabase;
	}
}
