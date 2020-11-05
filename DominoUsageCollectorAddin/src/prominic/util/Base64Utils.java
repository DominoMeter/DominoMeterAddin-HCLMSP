package prominic.util;

import javax.xml.bind.DatatypeConverter;

public class Base64Utils {

	private Base64Utils() {}

	public static String encode(String value) throws Exception {
		return DatatypeConverter.printBase64Binary(value.getBytes("utf-8"));
	}

	public static String decode(String value) throws Exception {
		byte[] decodedValue = DatatypeConverter.parseBase64Binary(value);
		return new String(decodedValue, "utf-8");
	}
}