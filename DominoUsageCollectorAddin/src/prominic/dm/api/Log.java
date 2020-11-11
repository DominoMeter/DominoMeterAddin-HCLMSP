package prominic.dm.api;

import java.io.IOException;
import prominic.io.RESTClient;
import prominic.util.ParsedError;

public class Log {
	public static boolean sendError(String server, String endpoint, ParsedError pe) {
		if (pe == null) return true;
		String subject = pe.getMessage();
		String body = pe.getStack();
		
		return send(server, endpoint, subject, body, 4);
	}
	
	public static boolean sendError(String server, String endpoint, String subject, String body) {
		return send(server, endpoint, subject, body, 4);
	}
	
	public static boolean sendLog(String server, String endpoint, String subject, String body) {
		return send(server, endpoint, subject, body, 2);
	}

	public static boolean send(String server, String endpoint, String subject, String body, int logLevel) {
		try {
			if (subject == null) subject = "-";
			if (body == null) body = "-";
			
			server = RESTClient.encodeValue(server);
			subject = RESTClient.encodeValue(subject);
			body = RESTClient.encodeValue(body);
			StringBuffer res = RESTClient.sendPOST(endpoint + "/log?openAgent&server=" + server, "subject=" + subject + "&body=" + body + "&logLevel=" + Integer.toString(logLevel));
			return res.toString().equalsIgnoreCase("OK");
		} catch (IOException e) {
			e.printStackTrace();
		}	
		return false;
	}	
}
