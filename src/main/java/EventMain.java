import java.io.File;
import java.util.HashMap;

import net.prominic.gja_v20220510.Event;
import net.prominic.gja_v20220510.GLogger;
import net.prominic.util.FileUtils;

public class EventMain extends Event {
	private String m_version = "";
	
	public EventMain(String name, long seconds, boolean fireOnStart, HashMap<String, Object> params, GLogger logger) {
		super(name, seconds, fireOnStart, params, logger);
		
//		m_version = (String) params.get("version");
	}

	@Override
	public void run() {
		System.out.println("Hello");
		cleanOutdatedFiles(".log");
		
		/*
		if (checkConnection()) {
			loadConfig();
			sendReport(false);
			updateVersion();
		}
		*/
	}

	/*
	 * Clean old jar and log files
	 * We keep last 5 jar files and last 5 log files
	 */
	public void cleanOutdatedFiles(String ext) {
		try {
			File dir = new File("DominoMeterAddin");
			if (!dir.isDirectory()) return;

			File files[] = FileUtils.endsWith(dir, ext);
			if (files.length <= 5) return;

			int count = 0;
			StringBuffer deletedFiles = new StringBuffer();
			files = FileUtils.sortFilesByModified(files, false);
			for (int i = 5; i < files.length; i++) {
				File file = files[i];
				if (!file.getName().equalsIgnoreCase(m_version)) {
					file.delete();
					if (count > 0) deletedFiles.append(", ");
					deletedFiles.append(file.getName());
					count++;
				}
			}

			if (count>0) {
				getLogger().info("Removed files (" + Integer.toString(count) + "): " + deletedFiles.toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
