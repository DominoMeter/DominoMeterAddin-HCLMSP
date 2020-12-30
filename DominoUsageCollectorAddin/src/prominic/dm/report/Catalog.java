package prominic.dm.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class Catalog {
	private Session m_session = null;
	private Database m_database = null;
	private ArrayList<Document> m_noteList = null;
	private final String[] m_accessList = {"ManagerList", "DesignerList", "EditorList", "AuthorList", "ReaderList", "DepositorList", "NoAccessList"};
	private HashMap<String, Vector<String>> m_dbACL = null;

	public Catalog(Session session) {
		m_session = session;
	}

	public boolean initialize() throws NotesException {
		m_database = m_session.getDatabase(null, "catalog.nsf", false);

		if (!this.valid()) {
			return false;
		}

		m_dbACL = new HashMap<String, Vector<String>>();

		m_noteList = new ArrayList<Document>();
		DocumentCollection col = m_database.search("@IsAvailable(ReplicaID) & @IsUnavailable(RepositoryType) & Server=\"" + m_session.getServerName()+"\"");
		Document doc = col.getFirstDocument();
		while (doc != null) {
			m_noteList.add(doc);
			doc = col.getNextDocument();
		}

		col.recycle();

		return true;
	}

	public ArrayList<Document> getNoteFiles() {
		return this.m_noteList;
	}

	// push document up on 50%, so next users will get to it faster
	public void promoteNoteFile(int i) {
		if (i == 0) return;

		int prev = i > 1 ? i / 2 : 0;
		Collections.swap(m_noteList, prev, i);
	}

	public boolean valid() {
		try {
			return m_database != null && m_database.isOpen();
		} catch (NotesException e) {};
		return false;
	}

	private int getAccessLevel(String fullName, Document doc, NamesUtil namesUtil) throws NotesException {
		fullName = fullName.toLowerCase();
		int accessLevel;

		if (!fullName.isEmpty()) {
			// 1. username
			accessLevel = 6;
			for(String field : this.m_accessList) {
				Vector<String> acl = getDbACL(doc, field);

				for(String entry : acl) {
					if (entry.equalsIgnoreCase(fullName) && namesUtil.isPerson(entry)) {
						return accessLevel;
					}			
				}

				accessLevel--;
			}

			// 2. group
			accessLevel = 6;
			for(String field : this.m_accessList) {
				Vector<String> acl = getDbACL(doc, field);

				for(String entry : acl) {
					if (namesUtil.isGroup(entry)) {
						Set<String> members = namesUtil.resolveGroup(entry);
						if (members != null && members.contains(fullName)) {
							return accessLevel;
						}
					}
				}

				accessLevel--;
			}

			// 3. wild card (f.x. */PNI)
			accessLevel = 6;
			for(String field : this.m_accessList) {
				Vector<String> acl = getDbACL(doc, field);

				for(String entry : acl) {
					if (entry.startsWith("*")) {
						if (fullName.endsWith(entry.toLowerCase())) {
							return accessLevel;
						}
					}
				}

				accessLevel--;
			}
		}

		// 4. Default
		accessLevel = 6;
		for(String field : this.m_accessList) {
			Vector<String> acl = getDbACL(doc, field);
			for(String entry : acl) {
				if (entry.equalsIgnoreCase("-Default-")) {
					return accessLevel;
				}				
			}

			accessLevel--;
		}

		return 0;
	}

	@SuppressWarnings("unchecked")
	private Vector<String> getDbACL(Document doc, String field) throws NotesException {
		Vector<String> v;

		String key = field + doc.getNoteID();
		if (m_dbACL.containsKey(key)) {
			v = m_dbACL.get(key);
		}
		else {
			v = doc.getItemValue(field);

			for(int i = 0; i < v.size(); i++) {
				String el = v.get(i);

				int sep = el.indexOf("$%^");
				if (sep > 0) {
					el = el.substring(0, el.indexOf("$%^"));
					v.set(i, el);
				}
			}
			m_dbACL.put(key, v);
		}

		return v;
	}

	public UserDbAccess getUserDbAccess(String fullName, NamesUtil namesUtil) throws NotesException {
		int access = -1;
		String replicaId = "";
		for (int i = 0; i < m_noteList.size(); i++) {
			Document doc = m_noteList.get(i);

			int dbAccess = this.getAccessLevel(fullName, doc, namesUtil);
			if (dbAccess > access) {
				access = dbAccess;
				@SuppressWarnings("unchecked")
				Vector<String> vReplicaId = m_session.evaluate("@Text(ReplicaId;\"*\")", doc);
				replicaId = vReplicaId.get(0);

				// push database with higher access to top, so next users will get to it faster
				promoteNoteFile(i);
			}

			if (access >= 4) {
				break;
			}
		}

		return new UserDbAccess(replicaId, access);
	}

	public void recycle() throws NotesException {
		if (m_noteList == null) return;
		for(Document doc : m_noteList) {
			doc.recycle();
		}

		this.m_database.recycle();
	}
}
