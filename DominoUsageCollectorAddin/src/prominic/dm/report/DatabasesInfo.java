package prominic.dm.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import lotus.domino.Session;
import lotus.domino.Database;
import lotus.domino.DocumentCollection;
import lotus.domino.Document;
import lotus.domino.NotesException;

import prominic.util.ParsedError;

public class DatabasesInfo {
	private int m_ntf;
	private int m_app;
	private int m_mail;
	private HashMap<String, Integer> m_templatesUsage;
	private ArrayList<String> m_anonymousAccess;
	private ParsedError m_pe = null;

	private void reset() {
		m_ntf = 0;
		m_app = 0;
		m_mail = 0;
		m_templatesUsage = new HashMap<String, Integer>();
		m_anonymousAccess = new ArrayList<String>();
		m_pe = null;
	}

	/*
	 * count NSF, NTF, Mail and App for defined Application server
	 */
	public boolean process(Session session, String server) {
		boolean res = false;

		reset();

		try {
			Database catalogDb = session.getDatabase(server, "catalog.nsf");
			if (catalogDb == null || !catalogDb.isOpen()) {
				throw new Exception("Catalog Database - not initialized");
			};

			DocumentCollection col = catalogDb.search("@IsAvailable(ReplicaID) & @IsUnavailable(RepositoryType)");
			Document doc = col.getFirstDocument();
			while (doc != null) {
				Document nextDoc = col.getNextDocument(doc);
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
			
			col.recycle();
			catalogDb.recycle();
			
			res = true;
		} catch (NotesException e) {
			m_pe = new ParsedError(e);
		}
		catch (Exception e) {
			m_pe = new ParsedError(e);
		}

		return res;
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
	
	public ParsedError getParsedError() {
		return m_pe;
	}
}