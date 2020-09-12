import java.io.IOException;

import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class Log {
	public static boolean sendError(Session session, String endpoint, String subject, String body) {
		try {
			String server = RESTClient.encodeValue(session.getServerName());
			subject = RESTClient.encodeValue(subject);
			body = RESTClient.encodeValue(body);
			StringBuffer res = RESTClient.sendPOST(endpoint + "/log?openAgent&server=" + server, "Subject=" + subject + "&body" + body + "&LogLevel=4");
			return res.toString().equalsIgnoreCase("OK");
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (NotesException e1) {
			e1.printStackTrace();
		}

		return false;
	}

	public static boolean send(Session session, String endpoint, String subject, String body, int logLevel) {
		try {
			Name nameServer = session.createName(session.getServerName());
			subject = RESTClient.encodeValue(subject);
			body = RESTClient.encodeValue(body);
			StringBuffer res = RESTClient.sendPOST(endpoint + "/log?openAgent&server=" + nameServer.getAbbreviated(), "Subject=" + subject + "&body" + body + "&LogLevel=" + Integer.toString(logLevel));
			return res.toString().equalsIgnoreCase("OK");
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (NotesException e1) {
			e1.printStackTrace();
		}
		return false;
	}
}
