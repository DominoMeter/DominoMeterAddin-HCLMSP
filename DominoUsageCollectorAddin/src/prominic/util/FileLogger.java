package prominic.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileLogger {
	int m_level;	// 0 - debug; 1 - info; 2 - exception; otherwise - off
	SimpleDateFormat m_formatter = new SimpleDateFormat("MM/dd/yyyy, HH:mm:ss");

	public FileLogger() {
		initialize(2);
	}

	public FileLogger(int level) {
		initialize(level);
	}

	private void initialize(int level) {
		setLevel(level);
	}

	public void setLevel(int level) {
		m_level = level;
	}

	public int getLevel() {
		return m_level;
	}

	public String getLevelLabel() {
		if (m_level == 0) return "debug";
		if (m_level == 1) return "info";
		if (m_level == 2) return "exception";
		return "off";
	}

	private void writeToFile(String msg, Throwable thrown, int level, String c) {
		if (level < getLevel()) return;

		try {
			SimpleDateFormat formatterFileName = new SimpleDateFormat("yyyy-MM");
			String fileName = "dominometer-" + formatterFileName.format(new Date()) + ".log";

			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("DominoMeterAddin/" + fileName, true)));

			String logLine = c + m_formatter.format(new Date()) + " " + msg;
			out.println(logLine);

			if (thrown != null) {
				thrown.printStackTrace(out);
			}

			out.close();
		} catch (IOException e) {}
	}

	public void severe(Exception e) {
		String msg = e.getLocalizedMessage();
		if (msg == null || msg.isEmpty()) {
			msg = "an undefined exception was thrown";
		}
		writeToFile(msg, e, 2, "!");
	}

	public void severe(String msg) {
		writeToFile(msg, null, 2, "!");
	}

	public void info(String msg) {
		writeToFile(msg, null, 1, " ");
	}

	public void debug(String msg) {
		writeToFile(msg, null, 0, "@");
	}
}