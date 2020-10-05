package prominic.dm.api;

import prominic.io.RESTClient;

public class Ping {
	String m_lastError = "";
	
	public boolean check(String endpoint, String server) {
		m_lastError = "";
		String url = endpoint + "/ping?openagent&server=" + RESTClient.encodeValue(server);
		try {
			StringBuffer buf = RESTClient.sendGET(url);
			return buf.toString().equals("OK");
		} catch (Exception e) {
			m_lastError = e.getMessage();
		}
		return false;
	}

	public String getLastError() {
		return m_lastError;
	}
}
