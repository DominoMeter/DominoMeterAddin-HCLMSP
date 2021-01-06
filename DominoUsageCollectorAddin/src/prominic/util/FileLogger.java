package prominic.util;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class FileLogger {
	private Logger m_logger;
	private SimpleFormatter m_formatter;
	private Throwable m_throw = null;

	public FileLogger() {
		initialize(Level.WARNING);
	}

	public FileLogger(Level level) {
		initialize(level);
	}

	private void initialize(Level level) {
		this.m_logger = Logger.getLogger(FileLogger.class.getName());
		this.m_logger.setLevel(level);

		this.m_formatter = new SimpleFormatter();
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] %5$s %n");
	}

	public void setLevel(Level level) {
		m_logger.setLevel(level);
	}

	public Level getLevel() {
		return m_logger.getLevel();
	}

	private void write(String msg, String fileName, Level level) {
		m_throw = null;
		writeToFile(msg, fileName, level);
	}

	private void write(Throwable thrown, String fileName, Level level) {
		m_throw = thrown;
		String msg = thrown.getLocalizedMessage();
		if (msg == null || msg.isEmpty()) {
			msg = "an undefined exception was thrown";
		}
		writeToFile(msg, fileName, level);
	}

	private void writeToFile(String msg, String fileName, Level level) {
		try {
			if (level.intValue() < m_logger.getLevel().intValue() || level.intValue() == Level.OFF.intValue()) {
				return;
			}

			FileHandler fh = new FileHandler("DominoMeterAddin/" + fileName, 500000, 1, true);
			m_logger.addHandler(fh);
			m_logger.setUseParentHandlers(false);

			fh.setFormatter(m_formatter);

			if (m_throw != null) {
				m_logger.log(level, msg, m_throw);
			}
			else {
				m_logger.log(level, msg);
			}

			fh.close();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void severe(Exception e) {
		write(e, "severe.log", Level.SEVERE);
	}

	public void severe(String msg) {
		write(msg, "severe.log", Level.SEVERE);
	}

	public void warning(String msg) {
		write(msg, "warning.log", Level.WARNING);
	}

	public void info(String msg) {
		write(msg, "info.log", Level.INFO);
	}

	public void fine(String msg) {
		write(msg, "fine.log", Level.FINE);
	}
}