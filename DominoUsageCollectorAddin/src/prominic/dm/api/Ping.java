package prominic.dm.api;

import java.io.IOException;

import prominic.io.RESTClient;
import prominic.util.ParsedError;

public class Ping {
	private ParsedError m_pe = null;
	
	public boolean check(String endpoint, String server) {
		m_pe = null;
		String url = endpoint + "/ping?openagent&server=" + RESTClient.encodeValue(server);
		try {
			StringBuffer buf = RESTClient.sendGET(url);
			return buf.toString().equals("OK");
		} catch (IOException e) {
			m_pe = new ParsedError(e);
		}
		return false;
	}

	public ParsedError getParsedError() {
		return m_pe;
	}
}
