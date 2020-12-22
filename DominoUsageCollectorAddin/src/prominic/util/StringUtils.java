package prominic.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
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
	
	public static String join(ArrayList<?> data, String sep) {
		String res = "";
		for(int i = 0; i < data.size(); i++) {
			if (i > 0) {
				res += sep;
			}
			res += data.get(i);
		}
		
		return res;
	}
	
	public static String encodeValue(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException(ex.getCause());
		}
	}
}
