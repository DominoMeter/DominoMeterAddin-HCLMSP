package prominic.dm.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import lotus.domino.ACL;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import prominic.util.StringUtils;

public class UsersInfo {
	private Session m_session;
	private ArrayList<Document> m_catalogList;
	private NamesUtil m_namesUtil;
	private HashMap<String, Vector<String>> m_dbACL;
	private StringBuffer m_usersList;

	private long m_usersAllow = 0;
	private long m_usersDeny = 0;

	private long m_usersEditor = 0;
	private long m_usersReader = 0;
	private long m_usersAuthor = 0;
	private long m_usersDepositor = 0;
	private long m_usersNoAccess = 0;

	private long m_usersTotal = 0;
	private long m_usersNotes = 0;
	private long m_usersWeb = 0;
	private long m_usersNotesWeb = 0;
	private long m_usersPNI = 0;
	private long m_usersMail = 0;
	private long m_usersConflict = 0;

	private final String m_accessItems[] = {"ManagerList", "DesignerList", "EditorList", "AuthorList", "ReaderList", "DepositorList"};

	public UsersInfo(Session session, ArrayList<Document> catalogList, NamesUtil namesUtil) {
		m_session = session;
		m_namesUtil = namesUtil;
		m_catalogList = catalogList;

		m_dbACL = new HashMap<String, Vector<String>>();
		m_usersList = new StringBuffer();
	}

	public void checkUserAccess(Document doc) {
		try {
			if (doc.isDeleted() || !doc.isValid()) return;

			boolean isNotes = doc.hasItem("Certificate") && !doc.getItemValueString("Certificate").isEmpty();
			boolean isWeb = doc.hasItem("HTTPPassword") && !doc.getItemValueString("HTTPPassword").isEmpty();
			String mailSystem = doc.getItemValueString("MailSystem");
			boolean isMail = (mailSystem.equals("1") || mailSystem.equals("6")) && doc.getItemValueString("MailServer").equalsIgnoreCase(m_session.getServerName()) && !doc.getItemValueString("MailFile").isEmpty();

			String fullName = doc.getItemValueString("FullName");
			UserDbAccess userAccess = getUserDbAccess(fullName);

			if (userAccess.getAccessLevel() >= ACL.LEVEL_EDITOR) {
				m_usersEditor++;
			}
			else if (userAccess.getAccessLevel() == ACL.LEVEL_AUTHOR) {
				m_usersAuthor++;
			}
			else if (userAccess.getAccessLevel() == ACL.LEVEL_READER) {
				m_usersReader++;
			}
			else if (userAccess.getAccessLevel() == ACL.LEVEL_DEPOSITOR) {
				m_usersDepositor++;
			}
			else if (userAccess.getAccessLevel() == ACL.LEVEL_NOACCESS) {
				m_usersNoAccess++;
			}

			m_usersTotal++;
			if (isNotes && !isWeb) m_usersNotes++;
			if (!isNotes && isWeb) m_usersWeb++;
			if (isNotes && isWeb) m_usersNotesWeb++;
			if (fullName.contains("/O=PNI")) m_usersPNI++;
			if (isMail) m_usersMail++;
			if (doc.hasItem("$Conflict")) m_usersConflict++;

			String userLine = doc.getUniversalID() + "|" + StringUtils.encodeValue(doc.getItemValueString("LastName")) + "|" + StringUtils.encodeValue(doc.getItemValueString("Suffix")) + "|" + StringUtils.encodeValue(doc.getItemValueString("FirstName")) + "|" + StringUtils.encodeValue(doc.getItemValueString("MiddleInitial")) + "|" + userAccess.getDbReplicaID() + "|" + Integer.toString(userAccess.getAccessLevel());
			if (m_usersList.length() > 0) {
				m_usersList.append("~");
			}
			m_usersList.append(userLine);
		} catch (NotesException e) {}
	}

	@SuppressWarnings("unchecked")
	public void allowDenyAccess(Document serverDoc) {
		try {
			// allow access
			Vector<String> members = serverDoc.getItemValue("AllowAccess");
			Set<String> resolvedMembers = m_namesUtil.resolveMixedList(members);
			m_usersAllow = resolvedMembers.size();

			// deny access
			members = serverDoc.getItemValue("DenyAccess");
			resolvedMembers = m_namesUtil.resolveMixedList(members);
			m_usersDeny = resolvedMembers.size();

		} catch (NotesException e) {}
	}

	public UserDbAccess getUserDbAccess(String fullName) throws NotesException {
		int access = -1;
		String replicaId = "";
		for (int i = 0; i < m_catalogList.size(); i++) {
			Document catalogDoc = m_catalogList.get(i);

			int dbAccess = getAccessLevel(fullName, catalogDoc);
			if (dbAccess > access) {
				access = dbAccess;
				@SuppressWarnings("unchecked")
				Vector<String> vReplicaId = m_session.evaluate("@Text(ReplicaId;\"*\")", catalogDoc);
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

	// push document up on 50%, so next users will get to it faster
	private void promoteNoteFile(int i) {
		if (i == 0) return;

		int prev = i > 1 ? i / 2 : 0;
		Collections.swap(m_catalogList, prev, i);
	}

	private int getAccessLevel(String fullName, Document catalogDoc) throws NotesException {
		fullName = fullName.toLowerCase();
		int accessLevel;

		if (!fullName.isEmpty()) {
			// 1. username
			accessLevel = 6;
			for(String field : this.m_accessItems) {
				Vector<String> acl = getDbACL(catalogDoc, field);

				for(String entry : acl) {
					if (entry.equalsIgnoreCase(fullName) && m_namesUtil.isPerson(entry)) {
						return accessLevel;
					}
				}

				accessLevel--;
			}

			// 2. group
			accessLevel = 6;
			for(String field : this.m_accessItems) {
				Vector<String> acl = getDbACL(catalogDoc, field);

				for(String entry : acl) {
					if (m_namesUtil.isGroup(entry)) {
						Set<String> members = m_namesUtil.resolveGroup(entry);
						if (members != null && members.contains(fullName)) {
							return accessLevel;
						}
					}
				}

				accessLevel--;
			}

			// 3. wild card (f.x. */PNI)
			accessLevel = 6;
			for(String field : this.m_accessItems) {
				Vector<String> acl = getDbACL(catalogDoc, field);

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
		for(String field : this.m_accessItems) {
			Vector<String> acl = getDbACL(catalogDoc, field);
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

	public long getUsersEditor() {
		return m_usersEditor;
	}
	public long getUsersAuthor() {
		return m_usersAuthor;
	}
	public long getUsersReader() {
		return m_usersReader;
	}
	public long getUsersDepositor() {
		return m_usersDepositor;
	}
	public long getUsersNoAccess() {
		return m_usersNoAccess;
	}
	public long getUsersTotal() {
		return m_usersTotal;
	}
	public long getUsersNotes() {
		return m_usersNotes;
	}
	public long getUsersWeb() {
		return m_usersWeb;
	}
	public long getUsersNotesWeb() {
		return m_usersNotesWeb;
	}
	public long getUsersPNI() {
		return m_usersPNI;
	}
	public long getUsersMail() {
		return m_usersMail;
	}
	public long getUsersConflict() {
		return m_usersConflict;
	}
	public long getUsersAllow() {
		return m_usersAllow;
	}
	public long getUsersDeny() {
		return m_usersDeny;
	}
	public StringBuffer getUsersList() {
		return this.m_usersList;
	}
}
