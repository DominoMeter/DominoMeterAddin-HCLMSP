package prominic.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import javax.net.ssl.HttpsURLConnection;

public class RESTClient {
	private static final String USER_AGENT = "DominoMeter";
	private static final String ACCESS_TOKEN = "f21f20afae6b4d99c1258551002002fa";

	public static StringBuffer sendPOST(String endpoint, String data) throws IOException {
		HttpURLConnection con = open(endpoint);
		con.setDoOutput(true);
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		con.getOutputStream().write(data.getBytes(), 0, data.length());

		return response(con);
	}

	public static StringBuffer sendGET(String endpoint) throws IOException {
		HttpURLConnection con = open(endpoint);
		con.setRequestMethod("GET");
		return response(con);
	}

	private static HttpURLConnection open(String endpoint) throws IOException {
		URL url = new URL(endpoint);

		HttpURLConnection con = null;
		String protocol = url.getProtocol();
		if (protocol.equals("https")) {
			con = (HttpsURLConnection) url.openConnection();
		}
		else if(protocol.equals("http")) {
			con = (HttpURLConnection) url.openConnection();
		}
		
		if (con == null) {
			throw new IllegalArgumentException("Unexpected protocol: " + protocol);
		}
		
		con.setRequestProperty("User-Agent", USER_AGENT);
		con.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);

		return con;
	}

	private static StringBuffer response(HttpURLConnection con) throws IOException {
		int responseCode = con.getResponseCode();
		if (responseCode != HttpURLConnection.HTTP_OK) {
			con.disconnect();
			throw new IOException("GET failed: " + con.getURL());
		}

		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		StringBuffer response = new StringBuffer();
		String inputLine;

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}

		in.close();
		con.disconnect();
		
		return response;
	}

	public static String encodeValue(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException(ex.getCause());
		}
	}
}
