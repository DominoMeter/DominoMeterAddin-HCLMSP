import java.io.BufferedInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import lotus.domino.NotesException;
import lotus.domino.Session;

public class UpdateRobot {
	private static final String JAVA_USER_CLASSES = "JAVAUSERCLASSES";

	public String applyNewVersion(Session session, String endpoint, String activeVersion) {
		try {
			String url = endpoint + "/version?openagent&server=" + RESTClient.encodeValue(session.getServerName());
			StringBuffer res = RESTClient.sendGET(url);

			// 1. read config
			String[] arr = res.toString().split("\\|");
			String fileURL = arr[1];

			String configVersion = new File(fileURL).getName();
			if (configVersion.equalsIgnoreCase(activeVersion)) {
				log("Version is up to date: " + activeVersion);
				return "";
			}

			log("activeVersion = " + activeVersion + " | " + "configVersion = " + configVersion);
			log("New version has been detected: " + configVersion);

			// 2. check if current
			String filePath = "ProminicAddin" + File.separator + configVersion;

			// 3. download new version if not already
			File tempFile = new File(filePath);
			if (tempFile.exists()) {
				log("File with same name already exists (download is not needed): " + filePath);
			}
			else {
				String fileUrl = endpoint + fileURL;
				log("Attempt to download file: " + fileUrl);
				log("New version will be downloaded to: " + filePath);
				boolean upload = saveURLTo(fileUrl, filePath);
				if (!upload) {
					log("File was NOT downloaded due to some error. Update aborted.");
					return "";
				};
				log("File was downloaded to: " + filePath);
			}

			// 4. register new JAR in notes.ini
			// Example: JAVAUSERCLASSESEXT=.\ProminicAddin\DominoMeter-5.jar
			String userClasses = session.getEnvironmentString(JAVA_USER_CLASSES, true);
			log(JAVA_USER_CLASSES + " (current) = " + userClasses);
			String NotesIniLine = "." + File.separator + filePath;

			String platform = session.getPlatform();
			String notesIniSep = platform.contains("Windows") ? ";" : ":";

			if (userClasses.isEmpty()) {
				userClasses = NotesIniLine;
			}
			else {
				if (userClasses.indexOf("DominoUsageCollector") > 0 || userClasses.indexOf("DominoMeter") > 0) {
					String[] userClassesArr = userClasses.split("\\" + notesIniSep);
					for (int i = 0; i < userClassesArr.length; i++) {
						if (userClassesArr[i].contains("DominoUsageCollector")) {
							userClassesArr[i] = NotesIniLine;
							userClasses = String.join(notesIniSep, userClassesArr);
							i = userClassesArr.length;
						}
						else if (userClassesArr[i].contains("DominoMeter")) {
							userClassesArr[i] = NotesIniLine;
							userClasses = String.join(notesIniSep, userClassesArr);
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
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NotesException e) {
			e.printStackTrace();
		}

		return "";
	}

	private boolean saveURLTo(String url, String filePath) {
		try (BufferedInputStream inputStream = new BufferedInputStream(new URL(url).openStream());
				FileOutputStream fileOS = new FileOutputStream(filePath)) {
			byte data[] = new byte[1024];
			int byteContent;
			while ((byteContent = inputStream.read(data, 0, 1024)) != -1) {
				fileOS.write(data, 0, byteContent);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	private void log(Object msg) {
		System.out.println("[UpdateRobot] " + msg.toString());
	}

}