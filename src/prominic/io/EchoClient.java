package prominic.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import prominic.util.ParsedError;

public class EchoClient {
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private ParsedError m_pe = null;

	public boolean startConnection(String endpoint, int port) {
		try {
			m_pe = null;

			socket = new Socket();
			SocketAddress socketAddress = new InetSocketAddress(endpoint, port);
			socket.connect(socketAddress, 10000);

			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream(), true);
			
			return true;
		} catch (IOException e) {
			m_pe = new ParsedError(e);
		}
		return false;
	}
	
	public String readBufferReader() {
		String answer = "";
		try {
			String lineSep = System.getProperty("line.separator");
			String res;
			while((res = in.readLine()) != null) {
			    answer += res;
			    answer += lineSep;
			}
		} catch (IOException e) {
			m_pe = new ParsedError(e);
		}

		return answer;
	}
	
	public void sendMessage(String msg) {
		try {
			out.println(msg);
			out.flush();
		} catch (Exception e) {
			m_pe = new ParsedError(e);
		}
	}

	public void shutdownOutput() {
		try {
			socket.shutdownOutput();
		} catch (IOException e) {
			m_pe = new ParsedError(e);
		}
	}
	
	public void stopConnection() {
		try {
			if (in != null) in.close();
			if (out != null) out.close();
			if (socket != null) socket.close();
		} catch (IOException e) {
			m_pe = new ParsedError(e);
		}
	}

	public ParsedError getParsedError() {
		return m_pe;
	}
}