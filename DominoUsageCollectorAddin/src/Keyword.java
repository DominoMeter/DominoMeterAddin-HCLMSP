import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.View;

public class Keyword {
	private Database m_database;
		
	public Keyword(Database database) {
		m_database = database;
	}
	
	@SuppressWarnings("unchecked")
	public Vector<String> getValue(String key) {
		try {
			View view = m_database.getView("Keyword");
			Document doc = view.getDocumentByKey(key, true);
			if (doc == null) {
				return null;
			}
			return doc.getItemValue("Data");
		} catch (NotesException e) {
			e.printStackTrace();
		}
		return null;
	}
}
