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

public class UsersInfo {
	private final String GROUPS = "groups";
	private final String USERS = "users";

	@SuppressWarnings("unchecked")
	public String accessDeniedCount(Database database, Document serverDoc) throws NotesException {
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

		HashMap<String, List<String>> allowData = new HashMap<String, List<String>>();
		allowData.put(GROUPS, new ArrayList<String>());
		allowData.put(USERS, new ArrayList<String>());

		Vector<String> members = serverDoc.getItemValue("AllowAccess");
		allowData = resolveGroupToMembers(viewGroups, vimGroups, servers, members, allowData);
		
		HashMap<String, List<String>> denyData = new HashMap<String, List<String>>();
		denyData.put(GROUPS, new ArrayList<String>());
		denyData.put(USERS, new ArrayList<String>());
		members = serverDoc.getItemValue("DenyAccess");
		denyData = resolveGroupToMembers(viewGroups, vimGroups, servers, members, denyData);

		entries.recycle();
		viewServers.recycle();
		viewGroups.recycle();

		String res = "&usersAllow=" + Integer.toString(allowData.get(USERS).size()) + "&usersDeny=" + Integer.toString(denyData.get(USERS).size());

		return res;
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

	public String usersCount(Database ab, String server) throws NotesException {
		StringBuffer buf = new StringBuffer();

		long users = 0;
		long usersNotes = 0;
		long usersWeb = 0;
		long usersNotesWeb = 0;
		long usersPNI = 0;
		long usersMail = 0;
		long usersConflict = 0;

		View view = ab.getView("People");
		Document doc = view.getFirstDocument();
		while (doc != null) {
			Document nextDoc = view.getNextDocument(doc);

			boolean isNotes = doc.hasItem("Certificate") && !doc.getItemValueString("Certificate").isEmpty();
			boolean isWeb = doc.hasItem("HTTPPassword") && !doc.getItemValueString("HTTPPassword").isEmpty();
			String mailSystem = doc.getItemValueString("MailSystem");
			boolean isMail = (mailSystem.equals("1") || mailSystem.equals("6")) && doc.getItemValueString("MailServer").equalsIgnoreCase(server) && !doc.getItemValueString("MailFile").isEmpty();

			users++;

			if (isNotes && !isWeb) {
				usersNotes++;
			}
			if (!isNotes && isWeb) {
				usersWeb++;
			}			
			if (isNotes && isWeb) {
				usersNotesWeb++;
			}			
			if (doc.getItemValueString("FullName").contains("/O=PNI")) {
				usersPNI++;
			}			
			if (isMail) {
				usersMail++;
			}			
			if (doc.hasItem("$Conflict")) {
				usersConflict++;
			}

			doc.recycle();
			doc = nextDoc;
		}

		buf.append("&users=" + Long.toString(users));
		buf.append("&usersNotes=" + Long.toString(usersNotes));
		buf.append("&usersWeb=" + Long.toString(usersWeb));
		buf.append("&usersNotesWeb=" + Long.toString(usersNotesWeb));
		buf.append("&usersPNI=" + Long.toString(usersPNI));
		buf.append("&usersMail=" + Long.toString(usersMail));
		buf.append("&usersConflict=" + Long.toString(usersConflict));

		return buf.toString();
	}

}
