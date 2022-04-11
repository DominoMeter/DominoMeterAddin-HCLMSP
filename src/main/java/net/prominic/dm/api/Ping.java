package net.prominic.dm.api;

import java.io.IOException;


import net.prominic.io.RESTClient;
import net.prominic.util.ParsedError;
import net.prominic.util.StringUtils;

public class Ping {
	private ParsedError m_pe = null;

	public boolean check(String endpoint, String server) {
		m_pe = null;
		String url = endpoint + "/ping?openagent&server=" + StringUtils.encodeValue(server);
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
