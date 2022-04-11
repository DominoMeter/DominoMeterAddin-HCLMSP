package prominic.util;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ParsedError {
	String m_message = "";
	String m_stack = "";
	
	public ParsedError(Exception e) {
		m_message = e.getLocalizedMessage();
		if (m_message == null) {
			m_message = "undefined";
		}

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		m_stack = sw.toString();
	}
	
	public String getMessage() {
		return m_message;
	}
	
	public String getStack() {
		return m_stack;
	}
}
