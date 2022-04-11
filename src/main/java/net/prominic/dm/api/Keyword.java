package prominic.dm.api;

import prominic.io.RESTClient;
import prominic.util.StringUtils;

public class Keyword {
	public static StringBuffer getValue(String endpoint, String server, String id) {
		String url = endpoint + "/keyword?openagent&server=" + StringUtils.encodeValue(server) + "&id=" + id;
		try {
			return RESTClient.sendGET(url);
		} catch (Exception e) {}
		return null;
	}
	
	public static StringBuffer getAll(String endpoint, String server) {
		return getValue(endpoint, server, "all");
	}
}
