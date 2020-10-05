package prominic.dm.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.View;

public class UsersInfo {
	private final String GROUPS = "groups";
	private final String USERS = "users";

	@SuppressWarnings("unchecked")
	public String accessDeniedCount(Database database, Document serverDoc) throws NotesException {
		View vimGroups = database.getView("($VIMGroups)");
		if (vimGroups == null) {
			return "";
		};
		
		View viewPeople = database.getView("People");

		Vector<String> usersFullName = new Vector<String>();
		Document doc = viewPeople.getFirstDocument();
		while (doc != null) {
			Document docNext = viewPeople.getNextDocument(doc);

			if (doc.hasItem("FullName")) {
				Vector<String> fullName = doc.getItemValue("FullName");
				if (fullName.size() > 0) {
					usersFullName.add(fullName.get(0));	
				}
			}

			doc.recycle();
			doc = docNext;
		}

		HashMap<String, List<String>> allowData = new HashMap<String, List<String>>();
		allowData.put(GROUPS, new ArrayList<String>());
		allowData.put(USERS, new ArrayList<String>());

		Vector<String> groups = serverDoc.getItemValue("AllowAccess");
		for(int i = 0; i < groups.size(); i++) {
			String groupName = groups.get(i);
			allowData = resolveGroupToMembers(vimGroups, usersFullName, groupName, allowData);
		}

		HashMap<String, List<String>> denyData = new HashMap<String, List<String>>();
		denyData.put(GROUPS, new ArrayList<String>());
		denyData.put(USERS, new ArrayList<String>());

		groups = serverDoc.getItemValue("DenyAccess");
		for(int i = 0; i < groups.size(); i++) {
			String groupName = groups.get(i);
			denyData = resolveGroupToMembers(vimGroups, usersFullName, groupName, denyData);
		}

		viewPeople.recycle();
		vimGroups.recycle();

		String res = "&usersAllow=" + Integer.toString(allowData.get(USERS).size()) + "&usersDeny=" + Integer.toString(denyData.get(USERS).size());

		return res;
	}

	private HashMap<String, List<String>> resolveGroupToMembers(View vimGroups, Vector<String> usersFullName, String groupName, HashMap<String, List<String>> data) throws NotesException {
		List<String> groups = data.get(GROUPS);
		if (groups.contains(groupName)) {
			return data;
		}

		Document doc = vimGroups.getDocumentByKey(groupName, true);
		if (doc == null) {
			return data;
		}

		List<String> users = data.get(USERS);

		@SuppressWarnings("unchecked")
		Vector<String> members = doc.getItemValue("Members");
		for(int i = 0; i < members.size(); i++) {
			String item = members.get(i);

			Document groupDoc = vimGroups.getDocumentByKey(item, true);
			if (groupDoc != null) {
				data = resolveGroupToMembers(vimGroups, usersFullName, groupDoc.getItemValueString("ListName"), data);
				groupDoc.recycle();
			}
			else {
				if (!users.contains(item) && usersFullName.contains(item)) {
					users.add(item);
				}
			}
		}

		data.put(USERS, users);

		doc.recycle();

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
