package prominic.dm.api;

import prominic.io.RESTClient;

public class Config {
	private String jar;
	private int interval;
	
	public boolean load(String endpoint, String server) {
		String url = endpoint + "/config?openagent&server=" + RESTClient.encodeValue(server) + "&endpoint=" + RESTClient.encodeValue(endpoint);
		try {
			StringBuffer buf = RESTClient.sendGET(url);

			if (buf == null || buf.length() == 0) {
				return false;
			}

			String[] variables = buf.toString().split("\\|");
			for(int i = 0; i < variables.length; i++) {
				if (variables[i].startsWith("jar=")) {
					jar = variables[i].substring("jar=".length());
				}
				else if (variables[i].startsWith("interval=")) {
					interval = Integer.parseInt(variables[i].substring("interval=".length()));
				}
			}
			
			return true;
		} catch (Exception e) {}
		return false;
	}
	
	public String getJAR() {
		return jar;
	}
	
	public int getInterval() {
		return interval;
	}
}
