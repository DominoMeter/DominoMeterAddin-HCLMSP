package prominic.dm.api;

import prominic.io.RESTClient;

public class Ping {
	public static boolean isLive(String endpoint, String server) {
		String url = endpoint + "/ping?openagent&server=" + RESTClient.encodeValue(server);
		try {
			StringBuffer buf = RESTClient.sendGET(url);
			return buf.toString().equals("OK");
		} catch (Exception e) {}
		return false;
	}
}
