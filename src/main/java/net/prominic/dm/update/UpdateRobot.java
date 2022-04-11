package net.prominic.dm.update;

import java.io.File;


import lotus.domino.NotesException;
import lotus.domino.Session;
import net.prominic.io.RESTClient;
import net.prominic.util.FileLogger;
import net.prominic.util.ParsedError;
import net.prominic.util.StringUtils;

public class UpdateRobot {
	private static final String JAVA_USER_CLASSES = "JAVAUSERCLASSES";
	private ParsedError m_pe = null;
	private FileLogger m_fileLogger;

	public UpdateRobot(FileLogger fileLogger) {
		m_fileLogger = fileLogger;
	}

	public String applyNewVersion(Session session, String server, String endpoint, String fileURL, String activeVersion) {
		try {
			m_pe = null;

			if (fileURL == null || fileURL.isEmpty()) {
				return "";
			}

			String configVersion = new File(fileURL).getName();
			if (configVersion.equalsIgnoreCase(activeVersion)) {
				return "";
			}

			log("activeVersion = " + activeVersion + " | " + "configVersion = " + configVersion);
			log("New version has been detected: " + configVersion);

			// 2. check if current
			String folder = "DominoMeterAddin";

			File f = new File(folder);
			if (!f.exists()) {
				f.mkdir();
			}

			String filePath = folder + File.separator + configVersion;

			// 3. download new version if not already
			File tempFile = new File(filePath);
			if (tempFile.exists()) {
				log("File with same name already exists (download is not needed): " + filePath);
			}
			else {
				String fileUrl = endpoint + fileURL;
				log("Attempt to download file: " + fileUrl);
				log("New version will be downloaded to: " + filePath);
				boolean upload = RESTClient.saveURLTo(fileUrl, filePath);
				if (!upload) {
					log("File was NOT downloaded due to some error. Update aborted.");
					throw new Exception("File was NOT downloaded due to some error. Update aborted.");
				};
				log("File was downloaded to: " + filePath);
			}

			// 4. register new JAR in notes.ini
			// Example: JAVAUSERCLASSESEXT=.\DominoMeterAddin\DominoMeter-5.jar
			String userClasses = session.getEnvironmentString(JAVA_USER_CLASSES, true);
			log(JAVA_USER_CLASSES + " (current) = " + userClasses);
			String NotesIniLine = "." + File.separator + filePath;

			String platform = session.getPlatform();
			String notesIniSep = platform.contains("Windows") ? ";" : ":";

			if (userClasses.isEmpty()) {
				userClasses = NotesIniLine;
			}
			else {
				if (userClasses.indexOf("DominoMeter") > 0) {
					String[] userClassesArr = userClasses.split("\\" + notesIniSep);
					for (int i = 0; i < userClassesArr.length; i++) {
						if (userClassesArr[i].contains("DominoMeter")) {
							userClassesArr[i] = NotesIniLine;
							userClasses = StringUtils.join(userClassesArr, notesIniSep);
							i = userClassesArr.length;
						}
					}
				}
				else {
					userClasses = userClasses + notesIniSep + NotesIniLine;
				}
			}

			session.setEnvironmentVar(JAVA_USER_CLASSES, userClasses, true);
			log(JAVA_USER_CLASSES + " (new) set to " + userClasses);

			return configVersion;
		} catch (NotesException e) {
			m_pe = new ParsedError(e);
		} catch (Exception e) {
			m_pe = new ParsedError(e);
		}

		return "";
	}

	private void log(Object msg) {
		m_fileLogger.info(msg.toString());
		System.out.println("[UpdateRobot] " + msg.toString());
	}

	public ParsedError getParsedError() {
		return m_pe;
	}
}