package prominic.util;

import java.io.*;
import java.net.*;

public class EchoClient {

	private Socket clientSocket;
	private PrintWriter out;
	private BufferedReader in;

	public boolean startConnection(String endpoint, int port) {
		try {
			clientSocket = new Socket();
			SocketAddress socketAddress = new InetSocketAddress(endpoint, port);
			clientSocket.connect(socketAddress, 5000);
			out = new PrintWriter(clientSocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			return true;
		} catch (IOException e) {}
		return false;
	}

	public String readBufferReaderReady() {
		String res = "";
		try {
			int letter;
			boolean ready = in.ready();
			while(ready && (letter = in.read()) != -1) {
				char c = (char) letter;
				res += c;
				ready = in.ready();
			}	
		} catch (IOException e) {
			e.printStackTrace();
		}

		return res;
	}

	public String readBufferReader() {
		String res = "";
		try {
			int letter;
			while((letter = in.read()) != -1) {
				char c = (char) letter;
				res += c;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return res;
	}

	public String sendMessage(String msg) {
		String res = null;
		try {
			out.println(msg);
			res = readBufferReader();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}

	public void stopConnection() {
		try {
			in.close();
			out.close();
			clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}