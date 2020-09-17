package prominic.dm.api;

import prominic.io.RESTClient;

public class Keyword {
	public static StringBuffer getValue(String endpoint, String server, String id) {
		String url = endpoint + "/keyword?openagent&server=" + RESTClient.encodeValue(server) + "&id=" + id;
		try {
			return RESTClient.sendGET(url);
		} catch (Exception e) {
			return null;
		}
	}
}
