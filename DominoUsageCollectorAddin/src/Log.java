import java.io.IOException;
import lotus.domino.NotesException;
import lotus.domino.Session;
import prominic.io.RESTClient;

public class Log {
	public static boolean sendError(Session session, String endpoint, String subject, String body) {
		try {
			String server = RESTClient.encodeValue(session.getServerName());
			subject = RESTClient.encodeValue(subject);
			body = RESTClient.encodeValue(body);
			StringBuffer res = RESTClient.sendPOST(endpoint + "/log?openAgent&server=" + server, "subject=" + subject + "&body=" + body + "&logLevel=4");
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
			String server = RESTClient.encodeValue(session.getServerName());
			subject = RESTClient.encodeValue(subject);
			body = RESTClient.encodeValue(body);
			StringBuffer res = RESTClient.sendPOST(endpoint + "/log?openAgent&server=" + server, "subject=" + subject + "&body=" + body + "&logLevel=" + Integer.toString(logLevel));
			return res.toString().equalsIgnoreCase("OK");
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (NotesException e1) {
			e1.printStackTrace();
		}
		return false;
	}
	
}
