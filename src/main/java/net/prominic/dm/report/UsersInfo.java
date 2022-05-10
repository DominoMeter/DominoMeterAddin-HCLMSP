package net.prominic.dm.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;
import lotus.domino.ACL;
import lotus.domino.Document;
import lotus.domino.Item;
import lotus.domino.NotesException;
import lotus.domino.Session;
import net.prominic.gja_v20220510.GLogger;
import net.prominic.util.StringUtils;

public class UsersInfo {
	private Session m_session;
	private ArrayList<Document> m_catalogList;
	private NamesUtil m_namesUtil;
	private HashMap<String, Vector<String>> m_dbACL;
	private StringBuffer m_usersList;
	private HashMap<String, Long> m_usersCount;
	private GLogger m_fileLogger;

	public final static String USERS_EDITOR = "Editors";
	public final static String USERS_AUTHOR = "Author";
	public final static String USERS_READER = "Reader";
	public final static String USERS_DEPOSITOR = "Depositor";
	public final static String USERS_NOACCESS = "NoAccess";
	public final static String USERS_TOTAL = "Total";
	public final static String USERS_NOTES = "Notes";
	public final static String USERS_WEB = "Web";
	public final static String USERS_NOTESWEB = "NotesWeb";
	public final static String USERS_PNI = "PNI";
	public final static String USERS_MAIL = "Mail";
	public final static String USERS_CONFLICT = "Conflict";
	public final static String USERS_ALLOW = "Allow";
	public final static String USERS_DENY = "Deny";

	private final String m_accessItems[] = {"ManagerList", "DesignerList", "EditorList", "AuthorList", "ReaderList", "DepositorList"};
	private final String FLAG_COMMENT = "##DominoMeter--FlagAs:";

	public UsersInfo(Session session, ArrayList<Document> catalogList, NamesUtil namesUtil, GLogger fileLogger) {
		m_session = session;
		m_namesUtil = namesUtil;
		m_catalogList = catalogList;
		m_fileLogger = fileLogger;

		m_dbACL = new HashMap<String, Vector<String>>();
		m_usersList = new StringBuffer();
		createUsersCount();	// initialize default keys for m_usersCount
	}

	private void createUsersCount() {
		m_usersCount = new HashMap<String, Long>();
		m_usersCount.put(USERS_EDITOR, (long) 0);
		m_usersCount.put(USERS_READER, (long) 0);
		m_usersCount.put(USERS_DEPOSITOR, (long) 0);
		m_usersCount.put(USERS_NOACCESS, (long) 0);
		m_usersCount.put(USERS_TOTAL, (long) 0);
		m_usersCount.put(USERS_NOTES, (long) 0);
		m_usersCount.put(USERS_WEB, (long) 0);
		m_usersCount.put(USERS_NOTESWEB, (long) 0);
		m_usersCount.put(USERS_PNI, (long) 0);
		m_usersCount.put(USERS_MAIL, (long) 0);
		m_usersCount.put(USERS_CONFLICT, (long) 0);
		m_usersCount.put(USERS_ALLOW, (long) 0);
		m_usersCount.put(USERS_DENY, (long) 0);
	}

	private void incrementCount(String name) {
		if (m_usersCount.containsKey(name)) {
			m_usersCount.put(name, m_usersCount.get(name).longValue() + 1);
		}
		else {
			m_usersCount.put(name, (long) 1);
		}
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
				incrementCount(USERS_EDITOR);
			}
			else if (userAccess.getAccessLevel() == ACL.LEVEL_AUTHOR) {
				incrementCount(USERS_AUTHOR);
			}
			else if (userAccess.getAccessLevel() == ACL.LEVEL_READER) {
				incrementCount(USERS_READER);
			}
			else if (userAccess.getAccessLevel() == ACL.LEVEL_DEPOSITOR) {
				incrementCount(USERS_DEPOSITOR);
			}
			else if (userAccess.getAccessLevel() == ACL.LEVEL_NOACCESS) {
				incrementCount(USERS_NOACCESS);
			}

			incrementCount(USERS_TOTAL);
			if (isNotes && !isWeb) incrementCount(USERS_NOTES);
			if (!isNotes && isWeb) incrementCount(USERS_WEB);
			if (isNotes && isWeb) incrementCount(USERS_NOTESWEB);
			if (fullName.contains("/O=PNI")) incrementCount(USERS_PNI);
			if (isMail) incrementCount(USERS_MAIL);
			if (doc.hasItem("$Conflict")) incrementCount(USERS_CONFLICT);

			// parse Comment for ##DominoMeter--FlagAs:<keyword>
			verifyFlagsInComment(doc);

			String userLine = doc.getUniversalID() + "|" + StringUtils.encodeValue(doc.getItemValueString("LastName")) + "|" + StringUtils.encodeValue(doc.getItemValueString("Suffix")) + "|" + StringUtils.encodeValue(doc.getItemValueString("FirstName")) + "|" + StringUtils.encodeValue(doc.getItemValueString("MiddleInitial")) + "|" + userAccess.getDbReplicaID() + "|" + Integer.toString(userAccess.getAccessLevel());
			if (m_usersList.length() > 0) {
				m_usersList.append("~");
			}
			m_usersList.append(userLine);
		} catch (NotesException e) {
			m_fileLogger.severe(e);
		}
	}

	private void verifyFlagsInComment(Document doc) {
		try {
			if (!doc.hasItem("Comment")) return;

			Item item = doc.getFirstItem("Comment");
			String comment = item.getText();
			item.recycle();

			int indexStart = comment.indexOf(FLAG_COMMENT);
			if (indexStart < 0) return;
			indexStart += FLAG_COMMENT.length();

			int indexEnd = comment.indexOf(" ", indexStart);
			if (indexEnd < 0) {
				indexEnd = comment.indexOf(";", indexStart);
			}

			String flag = indexEnd < 0 ? comment.substring(indexStart) : comment.substring(indexStart, indexEnd);
			if (flag.isEmpty()) return;

			this.incrementCount(flag);
			this.incrementCount("Custom");
		} catch (NotesException e) {
			m_fileLogger.severe(e);
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public void allowDenyAccess(Document serverDoc) {
		try {
			// allow access
			Vector<String> members = serverDoc.getItemValue("AllowAccess");
			Set<String> resolvedMembers = m_namesUtil.resolveMixedList(members);
			m_usersCount.put(USERS_ALLOW, (long) resolvedMembers.size());

			// deny access
			members = serverDoc.getItemValue("DenyAccess");
			resolvedMembers = m_namesUtil.resolveMixedList(members);
			m_usersCount.put(USERS_DENY, (long) resolvedMembers.size());

		} catch (NotesException e) {
			m_fileLogger.severe(e);
		}
	}

	public UserDbAccess getUserDbAccess(String fullName) throws NotesException {
		int access = -1;
		String replicaId = "";

		// in case if catalog does not exists
		if (m_catalogList == null) {
			return new UserDbAccess(replicaId, access);
		}			

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

	public HashMap<String, Long> getUsersCount() {
		return m_usersCount;
	}

	public long getUsersCount(String name) {
		return m_usersCount.containsKey(name) ? m_usersCount.get(name).longValue() : 0;
	}

	public StringBuffer getUsersList() {
		return this.m_usersList;
	}
}
