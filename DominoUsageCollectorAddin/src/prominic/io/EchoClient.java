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
	private Socket clientSocket;
	private PrintWriter out;
	private BufferedReader in;
	private ParsedError m_pe = null;
	
	public boolean startConnection(String endpoint, int port) {
		try {
			m_pe = null;

			/*
			Socket clientSocket = new Socket("localhost", 10001);
	        DataOutputStream dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());
	        DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream());
	        dataOutputStream.writeUTF("ls -l");
	        System.out.println(dataInputStream.readUTF());
	        */
			
			clientSocket = new Socket();
			SocketAddress socketAddress = new InetSocketAddress(endpoint, port);
			clientSocket.connect(socketAddress, 5000);
			out = new PrintWriter(clientSocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			m_pe = new ParsedError(e);
		}
		return false;
	}

	public String readBufferReaderReady() {
		String res = "";
		try {
			int letter;
			while(in.ready() && (letter = in.read()) != -1) {
				char c = (char) letter;
				res += c;
			}
		} catch (IOException e) {
			e.printStackTrace();
			m_pe = new ParsedError(e);
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
			m_pe = new ParsedError(e);
		}

		return res;
	}

	public String sendMessage(String msg) {
		String res = null;
		try {
			out.println(msg);
			out.print(System.getProperty("line.separator"));
			out.flush();
			res = readBufferReaderReady();
		} catch (Exception e) {
			e.printStackTrace();
			m_pe = new ParsedError(e);
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
			m_pe = new ParsedError(e);
		}
	}
	
	public ParsedError getParsedError() {
		return m_pe;
	}
}