package prominic.dm.report;

import java.util.ArrayList;
import java.util.Collections;

import lotus.domino.Database;
import lotus.domino.DbDirectory;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class DbList {
	private Session m_session = null;
	private ArrayList<Database> m_DbList = null;

	public DbList(Session session) {
		m_session = session;
	}

	public void initialize() throws NotesException {
		DbDirectory dir = m_session.getDbDirectory(null);
		m_DbList = new ArrayList<Database>();
		Database db = dir.getFirstDatabase(DbDirectory.DATABASE);
		while (db != null) {
			if (!db.isOpen()) {
				try {
					db.open();	
					this.m_DbList.add(db);
				}
				catch(Exception e) {}
			}
			db = dir.getNextDatabase();
		}

		dir.recycle();
	}

	public void recycle() throws NotesException {
		for(Database database : m_DbList) {
			database.recycle();
		}
	}

	public void promoteDb(int i) {
		if (i == 0) return;
		int prev = i > 1 ? i / 2 : 0;
		Collections.swap(m_DbList, prev, i);
	}

	public UserDbAccess getUserDbAccess(String fullName) throws NotesException {
		int access = -1;
		String replicaId = "";
		for (int i = 0; i < m_DbList.size(); i++) {
			Database database = m_DbList.get(i);

			int dbAccess = database.queryAccess(fullName);
			if (dbAccess > access) {
				access = dbAccess;
				replicaId = database.getReplicaID();

				// push database with higher access to top, so next users will get to it faster
				promoteDb(i);
			}

			if (access >= 4) {
				break;
			}
		}

		return new UserDbAccess(replicaId, access);
	}
}
