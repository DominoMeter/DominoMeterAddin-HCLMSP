import java.io.IOException;

public class Log {
	public static boolean sendError(String endpoint, Exception e) throws IOException {
		String subject = RESTClient.encodeValue(e.getLocalizedMessage());
		String body = RESTClient.encodeValue(e.getMessage());
		RESTClient.sendPOST(endpoint + "/log?openAgent", "Subject=" + subject + "&body" + body + "&LogLevel=4");
		return false;
	}
	
	public static boolean send(String endpoint, String subject, String body, int logLevel) throws IOException {
		subject = RESTClient.encodeValue(subject);
		body = RESTClient.encodeValue(body);
		RESTClient.sendPOST(endpoint + "/log?openAgent", "Subject=" + subject + "&body" + body + "&LogLevel=" + Integer.toString(logLevel));
		return false;
	}
}
