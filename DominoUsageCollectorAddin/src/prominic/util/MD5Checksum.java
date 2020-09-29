package prominic.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public class MD5Checksum {
   private static byte[] createChecksum(File file) throws Exception {
       InputStream fis =  new FileInputStream(file);

       byte[] buffer = new byte[1024];
       MessageDigest complete = MessageDigest.getInstance("MD5");
       int numRead;

       do {
           numRead = fis.read(buffer);
           if (numRead > 0) {
               complete.update(buffer, 0, numRead);
           }
       } while (numRead != -1);

       fis.close();
       return complete.digest();
   }

   private static String toHexString(byte[] bytes) {
	    StringBuilder hexString = new StringBuilder();

	    for (int i = 0; i < bytes.length; i++) {
	        String hex = Integer.toHexString(0xFF & bytes[i]);
	        if (hex.length() == 1) {
	            hexString.append('0');
	        }
	        hexString.append(hex);
	    }

	    return hexString.toString();
	}
   
   public static String getMD5Checksum(File file) throws Exception {
       byte[] b = createChecksum(file);
       return toHexString(b);
   }
   
   public static String getMD5Checksum(String s) throws Exception {
	   byte[] bytesOfMessage = s.getBytes("UTF-8");
	   MessageDigest md = MessageDigest.getInstance("MD5");
	   byte[] b = md.digest(bytesOfMessage);
       return toHexString(b);
   }
}