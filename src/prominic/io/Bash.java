package prominic.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Bash {
	public static String exec(String cmd) throws IOException {
		ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
		Process shell = pb.start();
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(shell.getInputStream()));

		String res = "";
		String s = null;
		while ((s = stdInput.readLine()) != null) {
			res += s;
		}

		return res;
	}
}
