package net.prominic.dm.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;
import net.prominic.gja_v083.GLogger;

public class NamesUtil {
	DocumentCollection m_people = null;
	List<String> m_fullNameList = null;
	HashMap<String, Vector<String>> m_groupOrig = null;
	HashMap<String, Set<String>> m_elResolved = null;
	Vector<String> m_processedEl = null;
	private boolean wildCardBuf = false;
	private GLogger m_fileLogger;
	
	public NamesUtil(GLogger fileLogger) {
		m_elResolved = new HashMap<String, Set<String>>();
		m_groupOrig = new HashMap<String, Vector<String>>();
		m_fullNameList = new ArrayList<String>();
		m_fileLogger = fileLogger;
	}
	
	public void addDatabase(Database database) {
		try {
			// 1. group origin
			DocumentCollection groups = database.search("Type=\"Group\"");
			Document doc = groups.getFirstDocument();
			while (doc != null) {
				String listName = doc.getItemValueString("ListName").toLowerCase();
				@SuppressWarnings("unchecked")
				Vector<String> members = doc.getItemValue("Members");
				m_groupOrig.put(listName, members);

				doc = groups.getNextDocument(doc);
			}

			// 2. users
			m_people = database.search("Type = \"Person\"", null, 0);
			doc = m_people.getFirstDocument();
			while (doc != null) {
				Document nextDoc = m_people.getNextDocument(doc);

				if (!doc.isDeleted() && doc.isValid()) {
					String fullName = doc.getItemValueString("FullName").toLowerCase();
					if (!fullName.isEmpty()) {
						m_fullNameList.add(fullName);
					}
				}

				doc.recycle();
				doc = nextDoc;
			}
		} catch (NotesException e) {
			m_fileLogger.severe(e);
		}
	}

	public boolean isGroup(String el) {
		return m_groupOrig.containsKey(el.toLowerCase()) ;
	}

	public boolean isPerson(String el) {
		return m_fullNameList.contains(el.toLowerCase()) ;
	}

	/*
	 * Resolve list with mixed entries: person, groups, servers etc
	 */
	public Set<String> resolveMixedList(Vector<String> members) throws NotesException {
		wildCardBuf = false;
		Set<String> list = new HashSet<String>();

		for(String member : members) {
			String memberL = member.toLowerCase();
			
			// group ?
			if (m_groupOrig.containsKey(memberL)) {
				Set<String> groupMembers = this.resolveGroup(member);
				list.addAll(groupMembers);
			}
			else if (m_fullNameList.contains(memberL)) {
				list.add(memberL);
			}
			else if (memberL.contains("*")) {
				wildCardBuf = true;
			}
		}

		return list;
	}

	/*
	 * Resolve group by name
	 */
	public Set<String> resolveGroup(String groupName) throws NotesException {
		m_processedEl = new Vector<String>();
		Set<String> members = resolveGroupWalk(groupName);
		return members;
	}

	/*
	 * Resolve group
	 */
	private Set<String> resolveGroupWalk(String elName) throws NotesException {
		// 1. skip already processed element (avoid constant loop)
		elName = elName.toLowerCase();

		if (m_processedEl.contains(elName)) {
			return null;
		}
		m_processedEl.add(elName);

		// 2. already resolved element
		if (m_elResolved.containsKey(elName)) {
			return m_elResolved.get(elName);
		}

		// 3. not a group element (can't resolve)
		if (!m_groupOrig.containsKey(elName)) {
			m_elResolved.put(elName, null);
			return null;
		};

		// 4. resolve a group
		Set<String> memberResolved = new HashSet<String>();
		Vector<String> members = m_groupOrig.get(elName);
		for(String member : members) {
			// group and not processed yet
			String memberL = member.toLowerCase();
			
			if (m_groupOrig.containsKey(memberL) && !m_processedEl.contains(memberL)) {
				Set<String> subGroupResolved = resolveGroupWalk(member);
				memberResolved.addAll(subGroupResolved);
			}
			else if (m_fullNameList.contains(memberL)) {
				memberResolved.add(memberL);
			}
			else if (!wildCardBuf && memberL.contains("*")) {
				wildCardBuf = true;
			}
		}
		m_elResolved.put(elName, memberResolved);

		return memberResolved;
	}

	public DocumentCollection getPeople() {
		return m_people;
	}

	public boolean getWildCardBuf() {
		return wildCardBuf;
	}

	public void recycle() throws NotesException {
		if (m_people != null) {
			m_people.recycle();
		}
	}
}
