import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import lotus.domino.NotesException;
import lotus.domino.Session;

public class UpdateRobot {
	private static final String JAVA_USER_CLASSES = "JAVAUSERCLASSES";

	public String applyNewVersion(Session session, String endpoint, String activeVersion) {
		try {
			String url = endpoint + "/version?openagent&server=" + RESTClient.encodeValue(session.getServerName()) + "&endpoint=" + RESTClient.encodeValue(endpoint);
			String fileURL = RESTClient.sendGET(url).toString();

			String configVersion = new File(fileURL).getName();
			if (configVersion.equalsIgnoreCase(activeVersion)) {
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
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NotesException e) {
			e.printStackTrace();
		}

		return "";
	}

	private boolean saveURLTo(String fileURL, String filePath) throws IOException {
		boolean res = false;
		URL url = new URL(fileURL);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		int responseCode = con.getResponseCode();

		if (responseCode == HttpURLConnection.HTTP_OK) {
			InputStream is = con.getInputStream();

			FileOutputStream os = new FileOutputStream(filePath);

			int bytesRead = -1;
			byte[] buffer = new byte[4096];
			while ((bytesRead = is.read(buffer)) != -1) {
				os.write(buffer, 0, bytesRead);
			}

			os.close();
			is.close();

			res = true;
		}
		con.disconnect();

		return res;
	}

	private void log(Object msg) {
		System.out.println("[UpdateRobot] " + msg.toString());
	}

}