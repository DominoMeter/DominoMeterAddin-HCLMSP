package prominic.dm.update;

import java.io.File;

import java.io.IOException;

import lotus.domino.Session;
import lotus.domino.NotesException;

import prominic.dm.api.Log;
import prominic.io.RESTClient;
import prominic.util.FileUtils;
import prominic.util.StringUtils;

public class UpdateRobot {
	private static final String JAVA_USER_CLASSES = "JAVAUSERCLASSES";

	public String applyNewVersion(Session session, String server, String endpoint, String activeVersion) {
		try {
			String url = endpoint + "/version?openagent&server=" + RESTClient.encodeValue(server) + "&endpoint=" + RESTClient.encodeValue(endpoint);
			String fileURL = RESTClient.sendGET(url).toString();

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
					return "";
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
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NotesException e) {
			e.printStackTrace();
		}

		return "";
	}
	
	/*
	 * Clean out old versions
	 */
	public void cleanOldVersions(String server, String endpoint, String curVersion) {
		try {
			File dir = new File("DominoMeterAddin");
			if (!dir.isDirectory()) return;

			File files[] = FileUtils.startsWith(dir, "DominoMeter");
			if (files.length <= 5) return;

			int count = 0;
			StringBuffer deletedFiles = new StringBuffer();
			files = FileUtils.sortFilesByModified(files, false);
			for (int i = 5; i < files.length; i++) {
				File file = files[i];
				if (!file.getName().equalsIgnoreCase(curVersion)) {
					file.delete();
					if (count > 0) {
						deletedFiles.append(", ");
					}
					deletedFiles.append(file.getName());
					count++;
				}
			}
			
			if (count>0)
				Log.sendLog(server, endpoint, "removed outdates versions (" + Integer.toString(count) + ")", deletedFiles.toString());
			
		} catch (Exception e) {
			Log.sendError(server, endpoint, "error during deleting files", e.getLocalizedMessage());
			e.printStackTrace();
		}
	}

	private void log(Object msg) {
		System.out.println("[UpdateRobot] " + msg.toString());
	}

}