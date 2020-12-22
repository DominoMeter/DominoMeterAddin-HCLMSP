package prominic.dm.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;
import prominic.util.ParsedError;
import prominic.util.StringUtils;

public class UsersInfo {
	private final String GROUPS = "groups";
	private final String USERS = "users";

	private HashMap<String, List<String>> m_allowData;
	private HashMap<String, List<String>> m_denyData;

	private StringBuffer m_usersList;

	private long m_usersTotal;
	private long m_usersNotes;
	private long m_usersWeb;
	private long m_usersNotesWeb;
	private long m_usersPNI;
	private long m_usersMail;
	private long m_usersConflict;

	private ParsedError m_pe;

	private void reset( ) {
		m_usersTotal = 0;
		m_usersNotes = 0;
		m_usersWeb = 0;
		m_usersNotesWeb = 0;
		m_usersPNI = 0;
		m_usersMail = 0;
		m_usersConflict = 0;

		m_usersList = new StringBuffer();

		m_allowData = new HashMap<String, List<String>>();
		m_allowData.put(GROUPS, new ArrayList<String>());
		m_allowData.put(USERS, new ArrayList<String>());

		m_denyData = new HashMap<String, List<String>>();
		m_denyData.put(GROUPS, new ArrayList<String>());
		m_denyData.put(USERS, new ArrayList<String>());

		m_pe = null;
	}

	public boolean process(Database ab, String server, Document serverDoc) {
		boolean res = false;

		reset();

		try {
			usersList(ab, server);
			accessDeniedCount(ab, serverDoc);
			res = true;
		} catch (NotesException e) {
			m_pe = new ParsedError(e);
		}

		return res;
	}

	@SuppressWarnings("unchecked")
	private void accessDeniedCount(Database database, Document serverDoc) throws NotesException {
		View viewGroups = database.getView("($VIMGroups)");
		Vector<String> vimGroups = new Vector<String>();
		Document doc = viewGroups.getFirstDocument();
		while (doc != null) {
			Document docNext = viewGroups.getNextDocument(doc);
			vimGroups.add(doc.getItemValueString("ListName"));

			doc.recycle();
			doc = docNext;
		}

		View viewServers = database.getView("($Servers)");
		Vector<String> servers = new Vector<String>();
		ViewEntryCollection entries = viewServers.getAllEntries();
		ViewEntry entry = entries.getFirstEntry();
		while(entry != null) {
			ViewEntry nextEntry = entries.getNextEntry();
			servers.add((String) entry.getColumnValues().get(0));

			entry.recycle();
			entry = nextEntry;
		}

		Vector<String> members = serverDoc.getItemValue("AllowAccess");
		m_allowData = resolveGroupToMembers(viewGroups, vimGroups, servers, members, m_allowData);

		members = serverDoc.getItemValue("DenyAccess");
		m_denyData = resolveGroupToMembers(viewGroups, vimGroups, servers, members, m_denyData);

		entries.recycle();
		viewServers.recycle();
		viewGroups.recycle();
	}

	private Document getGroupDocument(View view, String key) {
		try {
			Document doc = view.getDocumentByKey(key, true);
			return doc;
		} catch (NotesException e) {}
		return null;
	}

	@SuppressWarnings("unchecked")
	private HashMap<String, List<String>> resolveGroupToMembers(View viewGroups, Vector<String> vimGroups, Vector<String> servers, Vector<String> members, HashMap<String, List<String>> data) throws NotesException {
		List<String> groups = data.get(GROUPS);
		List<String> users = data.get(USERS);

		for(int i = 0; i < members.size(); i++) {
			String item = members.get(i);

			// it's a group that we have not processed yet
			if (vimGroups.contains(item)) {
				if (!groups.contains(item)) {
					groups.add(item);
					Document doc = getGroupDocument(viewGroups, item);
					if (doc != null) {
						Vector<String> groupMembers = doc.getItemValue("Members");
						data.put(GROUPS, groups);
						data.put(USERS, users);
						data = resolveGroupToMembers(viewGroups, vimGroups, servers, groupMembers, data);
						doc.recycle();
					}
				}
			}
			else if (!servers.contains(item) && !users.contains(item)) {
				users.add(item);
			}
		}

		data.put(USERS, users);

		return data;
	}

	private void usersList(Database ab, String server) throws NotesException {
		View view = ab.getView("People");
		view.setAutoUpdate(false);

		for (int i = 0; i <= 5000; i++) {
			Document doc = view.getFirstDocument();
			while (doc != null) {
				Document nextDoc = view.getNextDocument(doc);

				if (!doc.isDeleted() && doc.isValid()) {
					boolean isNotes = doc.hasItem("Certificate") && !doc.getItemValueString("Certificate").isEmpty();
					boolean isWeb = doc.hasItem("HTTPPassword") && !doc.getItemValueString("HTTPPassword").isEmpty();
					String mailSystem = doc.getItemValueString("MailSystem");
					boolean isMail = (mailSystem.equals("1") || mailSystem.equals("6")) && doc.getItemValueString("MailServer").equalsIgnoreCase(server) && !doc.getItemValueString("MailFile").isEmpty();

					m_usersTotal++;
					if (isNotes && !isWeb) m_usersNotes++;
					if (!isNotes && isWeb) m_usersWeb++;
					if (isNotes && isWeb) m_usersNotesWeb++;
					if (doc.getItemValueString("FullName").contains("/O=PNI")) m_usersPNI++;
					if (isMail) m_usersMail++;
					if (doc.hasItem("$Conflict")) m_usersConflict++;

					String userLine = doc.getUniversalID() + "|" + StringUtils.encodeValue(doc.getItemValueString("LastName")) + "|" + StringUtils.encodeValue(doc.getItemValueString("Suffix")) + "|" + StringUtils.encodeValue(doc.getItemValueString("FirstName")) + "|" + StringUtils.encodeValue(doc.getItemValueString("MiddleInitial"));
					userLine += (nextDoc != null) ? "~" : "";
					m_usersList.append(userLine);
				}

				doc.recycle();
				doc = nextDoc;
			}
		}

		view.setAutoUpdate(true);
		view.recycle();
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
	public StringBuffer getUsersList() {
		return m_usersList;
	}
	public long getAllowCount() {
		return m_allowData.get(USERS).size();
	}
	public long getDenyCount() {
		return m_denyData.get(USERS).size();
	}
	public ParsedError getParsedError() {
		return m_pe;
	}
}
