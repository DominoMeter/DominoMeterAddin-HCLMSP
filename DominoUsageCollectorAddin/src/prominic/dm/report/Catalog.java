package prominic.dm.report;

import java.util.ArrayList;
import java.util.Collections;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class Catalog {
	private Session m_session = null;
	private Database m_database = null;
	private ArrayList<Document> m_noteList = null;

	public Catalog(Session session) {
		m_session = session;
	}

	public boolean initialize() throws NotesException {
		m_database = m_session.getDatabase(null, "catalog.nsf", false);

		if (!this.valid()) {
			return false;
		}

		m_noteList = new ArrayList<Document>();
		DocumentCollection col = m_database.search("@IsAvailable(ReplicaID) & @IsUnavailable(RepositoryType)");
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

		int prev = i > 1 ? i / 2 : 1;
		Collections.swap(m_noteList, prev, i);
	}

	public boolean valid() {
		try {
			return m_database != null && m_database.isOpen();
		} catch (NotesException e) {};
		return false;
	}
	
	public void recycle() throws NotesException {
		for(Document doc : m_noteList) {
			doc.recycle();
		}
		
		this.m_database.recycle();
	}
}
