package net.prominic.dm.api;

import java.io.IOException;

import net.prominic.io.RESTClient;
import net.prominic.util.ParsedError;
import net.prominic.util.StringUtils;

public class Log {
	public static boolean sendError(String server, String endpoint, ParsedError pe) {
		if (pe == null) return true;
		String subject = pe.getMessage();
		String body = pe.getStack();
		
		return send(server, endpoint, subject, body, 4);
	}
	
	public static boolean sendError(String server, String endpoint, Exception e) {
		if (e == null) return true;
		ParsedError pe = new ParsedError(e);

		return sendError(server, endpoint, pe);
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
			
			server = StringUtils.encodeValue(server);
			subject = StringUtils.encodeValue(subject);
			body = StringUtils.encodeValue(body);
			StringBuffer res = RESTClient.sendPOST(endpoint + "/log?openAgent&server=" + server, "application/x-www-form-urlencoded", "subject=" + subject + "&body=" + body + "&logLevel=" + Integer.toString(logLevel));
			return res.toString().equalsIgnoreCase("OK");
		} catch (IOException e) {
			e.printStackTrace();
		}	
		return false;
	}	
}
