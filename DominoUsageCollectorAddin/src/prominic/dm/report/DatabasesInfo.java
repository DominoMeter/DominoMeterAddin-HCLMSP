package prominic.dm.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

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
	private ArrayList<String> m_anonymousAccess;

	private void resetCounters() {
		m_ntf = 0;
		m_app = 0;
		m_mail = 0;
		m_templatesUsage = new HashMap<String, Integer>();
		m_anonymousAccess = new ArrayList<String>();
	}

	/*
	 * count NSF, NTF, Mail and App for defined Application server
	 */
	public boolean process(Session session, String server) throws NotesException {
		Database catalogDb = session.getDatabase(server, "catalog.nsf");
		View replicaId = catalogDb.getView("($ReplicaId)");

		resetCounters();

		// File
		Document doc = replicaId.getFirstDocument();
		while (doc != null) {
			Document nextDoc = replicaId.getNextDocument(doc);
			String serverDoc = doc.getItemValueString("Server");
			String dbInheritTemplateName = doc.getItemValueString("DbInheritTemplateName");
			
			if (server.equalsIgnoreCase(serverDoc)) {
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
				
				if (!dbInheritTemplateName.isEmpty()) {
					Integer count = m_templatesUsage.containsKey(dbInheritTemplateName) ? m_templatesUsage.get(dbInheritTemplateName) : 0;
					m_templatesUsage.put(dbInheritTemplateName, Integer.valueOf(count + 1));	
				}
				
				if (hasAnonymous(doc)) {
					m_anonymousAccess.add(pathName);
				}
			}

			doc.recycle();
			doc = nextDoc;
		}

		replicaId.recycle();
		catalogDb.recycle();
		
		return true;
	}
	
	private boolean hasAnonymous(Document doc) {
		try {
			String items[] = {"ManagerList", "DesignerList", "EditorList", "AuthorList", "ReaderList", "DepositorList"};
			for(int i = 0; i < items.length; i++) {
				@SuppressWarnings("unchecked")
				Vector<String> list = doc.getItemValue(items[i]);
				for(int j = 0; j < list.size(); j++) {
					String entry = list.get(j);
					if (entry.startsWith("Anonymous")) {
						return true;
					}
				}
			}

		} catch (NotesException e) {}

		return false;
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
	
	public ArrayList<String> getAnonymousAccess() {
		return m_anonymousAccess;
	}
}