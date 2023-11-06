package net.prominic.util;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.HttpsURLConnection;

import java.io.IOException;
import java.net.URL;

public class HostUtils {
	private static int timeoutMillis = 3000;

	public static Date ExpireDate(String host) throws IOException, KeyManagementException, NoSuchAlgorithmException {
		X509TrustManager customTrustManager = new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}
			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		};

		TrustManager[] trustAllCertificates = {customTrustManager};

		// Initialize SSLContext with custom TrustManager
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustAllCertificates, null);

		URL url = new URL("https://" + host);
		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
		connection.setSSLSocketFactory(sslContext.getSocketFactory());
		connection.setConnectTimeout(timeoutMillis);
		connection.connect();

		Certificate[] certs = connection.getServerCertificates();
		if (certs.length > 0 && certs[0] instanceof X509Certificate) {
			X509Certificate certificate = (X509Certificate) certs[0];
			return certificate.getNotAfter();
		}
		
		return null;
	}
	
	public static boolean isIPAddress(String value) {
		String ipAddressPattern = "^([0-9]{1,3}\\.){3}[0-9]{1,3}$";
		Pattern pattern = Pattern.compile(ipAddressPattern);
		Matcher matcher = pattern.matcher(value);
		return matcher.matches();
	}
}
