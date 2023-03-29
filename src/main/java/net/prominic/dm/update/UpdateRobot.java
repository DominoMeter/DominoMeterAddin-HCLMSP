package net.prominic.dm.update;

import java.io.File;

import lotus.domino.NotesException;
import lotus.domino.Session;
import net.prominic.gja_v084.GConfig;
import net.prominic.gja_v084.GLogger;
import net.prominic.io.RESTClient;
import net.prominic.util.ParsedError;

public class UpdateRobot {
	private ParsedError m_pe = null;
	private GLogger m_fileLogger;

	public UpdateRobot(GLogger fileLogger) {
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
			String folder = "JavaAddin" + File.separator + "DominoMeter";
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
			// Example: GJA_DominoMeter=JavaAddin\DominoMeter\DominoMeter-118.jar
			session.setEnvironmentVar("GJA_DominoMeter", filePath, true);

			// 5. GConfig
			String configV = configVersion.substring(configVersion.indexOf("-")+1, configVersion.indexOf("."));
			GConfig.set(folder + File.separator + "config.txt", "version", "1.0." + configV);
			
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