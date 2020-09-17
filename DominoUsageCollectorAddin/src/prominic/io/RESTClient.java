package prominic.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class RESTClient {
	private static final String USER_AGENT = "Mozilla/5.0";
	private static final String ACCESS_TOKEN = "f21f20afae6b4d99c1258551002002fa";
	
	public static StringBuffer sendPOST(String endpoint, String data) throws IOException {
		URL url = new URL(endpoint);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setDoOutput(true);
		con.setRequestMethod("POST");
		con.setRequestProperty("User-Agent", USER_AGENT);
		con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		con.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);

		con.getOutputStream().write(data.getBytes(), 0, data.length());;

		int responseCode = con.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) {
			return getResponse(con);
		}
		else {
			throw new IOException("POST failed: " + endpoint);
		}
	}

	public static StringBuffer sendGET(String endpoint) throws IOException {
		URL url = new URL(endpoint);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("User-Agent", USER_AGENT);
		con.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
		
		int responseCode = con.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) {
			return getResponse(con);
		} else {
			throw new IOException("GET failed: " + endpoint);
		}
	}
	
	static StringBuffer getResponse(HttpURLConnection con) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		StringBuffer response = new StringBuffer();
		String inputLine;

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		
		in.close();
		
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
