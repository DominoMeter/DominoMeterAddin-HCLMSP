import java.io.BufferedInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import lotus.domino.NotesException;
import lotus.domino.Session;

public class UpdateRobot {
	private static final String JAVA_USER_CLASSES = "JAVAUSERCLASSES";

	public boolean applyNewVersion(Session session, String endpoint, String server, String activeVersion) throws NotesException {
		String url = endpoint + "/config?openagent&server=" + server;
		StringBuffer res = null;
		try {
			res = RESTClient.sendGET(url);
		} catch (Exception e) {
			System.out.println("GET failed " + url);
			return false;
		}

		// 1. read config
		String[] arr = res.toString().split("\\|");
		String configVersion = arr[0];
		String fileURL = arr[1];

		if (activeVersion.equals(configVersion)) {
			System.out.println("Version is up to date");
			return false;
		}

		System.out.println("activeVersion = " + activeVersion + " | " + "configVersion = " + configVersion);
		System.out.println("New version has been detected: " + configVersion);

		// 2. check if current
		String fileName = "DominoUsageCollectorAddin-" + configVersion + ".jar";
		String filePath = "ProminicAddin" + File.separator + fileName;
		System.out.println("fileName = " + fileName);
		System.out.println("filePath = " + filePath);

		// 3. download new version if not already
		File tempFile = new File(filePath);
		if (tempFile.exists()) {
			System.out.println("File with same name already exists (download is not needed): " + filePath);
		}
		else {
			String fileUrl = endpoint + "/0/" + fileURL;
			System.out.println("fileUrl = " + fileUrl);
			System.out.println("New version will be downloaded to: " + filePath);
			boolean upload = saveURLTo(fileUrl, filePath);
			if (!upload) {
				System.out.println("File was NOT downloaded due to some error. Update aborted.");
				return false;
			};
			System.out.println("File was downloaded to: " + filePath);
		}

		// 4. register new JAR in notes.ini
		// Example: JAVAUSERCLASSESEXT=.\ProminicAddin\DominoUsageCollectorAddin5.jar
		String userClasses = session.getEnvironmentString(JAVA_USER_CLASSES, true);
		System.out.println(JAVA_USER_CLASSES + " (current) = " + userClasses);
		String NotesIniLine = "." + File.separator + filePath;

		String platform = session.getPlatform();
		String notesIniSep = platform.contains("Windows") ? ";" : ":";
		System.out.println(platform);
		System.out.println("separator = " + notesIniSep);

		if (userClasses.isEmpty()) {
			userClasses = NotesIniLine;
		}
		else {
			if (userClasses.indexOf("DominoUsageCollectorAddin") > 0) {
				String[] userClassesArr = userClasses.split("\\" + notesIniSep);
				for (int i = 0; i < userClassesArr.length; i++) {
					if (userClassesArr[i].contains("DominoUsageCollectorAddin")) {
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

		System.out.println(JAVA_USER_CLASSES + " (new) set to " + userClasses);
		session.setEnvironmentVar(JAVA_USER_CLASSES, userClasses, true);

		return true;
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

}