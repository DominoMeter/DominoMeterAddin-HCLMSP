package prominic.util;

import java.util.Vector;

public class StringUtils {
	public static String join(String[] data, String sep) {
		String res = "";
		for(int i = 0; i < data.length; i++) {
			if (i > 0) {
				res += sep;
			}
			res += data[i];
		}
		
		return res;
	}
	
	public static String join(Vector<?> data, String sep) {
		String res = "";
		for(int i = 0; i < data.size(); i++) {
			if (i > 0) {
				res += sep;
			}
			res += data.get(i);
		}
		
		return res;
	}
}
