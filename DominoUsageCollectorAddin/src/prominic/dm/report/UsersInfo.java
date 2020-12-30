package prominic.dm.report;

import java.util.Set;
import java.util.Vector;

import lotus.domino.ACL;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import prominic.util.ParsedError;
import prominic.util.StringUtils;

public class UsersInfo {
	private StringBuffer m_usersList;
	private NamesUtil m_namesUtil;

	private long m_usersAllow;
	private long m_usersDeny;

	private long m_usersEditor;
	private long m_usersReader;
	private long m_usersAuthor;
	private long m_usersDepositor;
	private long m_usersNoAccess;

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
		m_usersAllow = 0;
		m_usersDeny = 0;
		m_usersEditor = 0;
		m_usersAuthor = 0;
		m_usersReader = 0;
		m_usersDepositor = 0;
		m_usersNoAccess = 0;

		m_usersList = new StringBuffer();
		m_namesUtil = new NamesUtil();

		m_pe = null;
	}

	public boolean process(Session session, Catalog catalog, Database ab, String server, Document serverDoc) {
		boolean res = false;

		reset();

		try {
			m_namesUtil.initialize(ab);

			accessDeniedCount(serverDoc);

			usersList(session, catalog, ab, server);
			res = true;
		} catch (NotesException e) {
			m_pe = new ParsedError(e);
		}

		return res;
	}

	@SuppressWarnings("unchecked")
	private void accessDeniedCount(Document serverDoc) throws NotesException {
		Vector<String> members = serverDoc.getItemValue("AllowAccess");
		Set<String> resolvedMembers = m_namesUtil.resolveMixedList(members);
		m_usersAllow = resolvedMembers.size();

		members = serverDoc.getItemValue("DenyAccess");
		resolvedMembers = m_namesUtil.resolveMixedList(members);
		m_usersDeny = resolvedMembers.size();
	}

	private void usersList(Session session, Catalog catalog, Database ab, String server) throws NotesException {
		View view = ab.getView("People");
		view.setAutoUpdate(false);

		// use DbDirectory if Catalong is not defined for some reason
		DbList dbList = null;
		if (!catalog.valid()) {
			dbList = new DbList(session);
			dbList.initialize();
		}

		Document doc = view.getFirstDocument();
		while (doc != null) {
			Document nextDoc = view.getNextDocument(doc);

			if (!doc.isDeleted() && doc.isValid()) {
				boolean isNotes = doc.hasItem("Certificate") && !doc.getItemValueString("Certificate").isEmpty();
				boolean isWeb = doc.hasItem("HTTPPassword") && !doc.getItemValueString("HTTPPassword").isEmpty();
				String mailSystem = doc.getItemValueString("MailSystem");
				boolean isMail = (mailSystem.equals("1") || mailSystem.equals("6")) && doc.getItemValueString("MailServer").equalsIgnoreCase(server) && !doc.getItemValueString("MailFile").isEmpty();

				String fullName = doc.getItemValueString("FullName");
				UserDbAccess userAccess = null;
				if (catalog.valid()) {
					userAccess = catalog.getUserDbAccess(fullName, m_namesUtil);
				}
				else {
					userAccess = dbList.getUserDbAccess(fullName);	
				}

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
				userLine += (nextDoc != null) ? "~" : "";
				m_usersList.append(userLine);
			}

			doc.recycle();
			doc = nextDoc;
		}

		if (dbList != null) {
			dbList.recycle();	
		}

		view.setAutoUpdate(true);
		view.recycle();
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
	public StringBuffer getUsersList() {
		return m_usersList;
	}
	public long getUsersAllow() {
		return m_usersAllow;
	}
	public long getUsersDeny() {
		return m_usersDeny;
	}
	public ParsedError getParsedError() {
		return m_pe;
	}
}
