package prominic.dm.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.View;

public class NamesUtil {
	Database m_database = null;
	List<String> m_persons = null;
	HashMap<String, Vector<String>> m_groupOrig = null;
	HashMap<String, Set<String>> m_elResolved = null;
	Vector<String> m_processedEl = null;

	public void initialize(Database database) throws NotesException {
		m_database = database;
		m_elResolved = new HashMap<String, Set<String>>();
		m_groupOrig = new HashMap<String, Vector<String>>();

		// 1. group origin
		View view1 = database.getView("($VIMGroups)");
		view1.setAutoUpdate(false);
		
		Document doc = view1.getFirstDocument();
		while (doc != null) {
			Document docNext = view1.getNextDocument(doc);
			
			String listName = doc.getItemValueString("ListName").toLowerCase();
			@SuppressWarnings("unchecked")
			Vector<String> members = doc.getItemValue("Members");
			m_groupOrig.put(listName, members);
			
			doc.recycle();
			doc = docNext;
		}
		
		m_persons = new ArrayList<String>();
		View view2 = database.getView("People");
		view2.setAutoUpdate(false);
		doc = view2.getFirstDocument();
		while (doc != null) {
			Document nextDoc = view2.getNextDocument(doc);
			
			if (!doc.isDeleted() && doc.isValid()) {
				String fullName = doc.getItemValueString("FullName").toLowerCase();
				
				if (!fullName.isEmpty()) {
					m_persons.add(fullName);
				}
			}
			
			doc.recycle();
			doc = nextDoc;
		}
		
		view1.setAutoUpdate(true);
		view1.recycle();
		view2.setAutoUpdate(true);
		view2.recycle();
	}
	
	public boolean isGroup(String el) {
		return m_groupOrig.containsKey(el.toLowerCase()) ;
	}
	
	public boolean isPerson(String el) {
		return m_persons.contains(el.toLowerCase()) ;
	}
	
	/*
	 * Resolve list with mixed entries: person, groups, servers etc
	 */
	public Set<String> resolveMixedList(Vector<String> members) throws NotesException {
		Set<String> list = new HashSet<String>();
		
		for(String member : members) {
			String memberL = member.toLowerCase();
			// group ?
			if (m_groupOrig.containsKey(memberL)) {
				Set<String> groupMembers = this.resolveGroup(member);
				list.addAll(groupMembers);
			}
			else if (m_persons.contains(memberL)) {
				list.add(memberL);
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
			else if (m_persons.contains(memberL)) {
				memberResolved.add(memberL);
			}
		}
		m_elResolved.put(elName, memberResolved);
		
		return memberResolved;
	}
	
	public List<String> getAllPersons() {
		return m_persons;
	}
}
