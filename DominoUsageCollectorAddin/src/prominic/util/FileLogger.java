package prominic.util;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class FileLogger {
	public static void log(String msg) {
		try {
			System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] %5$s %n");

			Logger logger = Logger.getLogger(FileLogger.class.getName());
			FileHandler fh = new FileHandler("DominoMeterAddin/log.txt", 500000, 1, true);
			logger.addHandler(fh);
			//			logger.setUseParentHandlers(false);

			SimpleFormatter formatter = new SimpleFormatter();
			fh.setFormatter(formatter);

			logger.info(msg);
			fh.close();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}