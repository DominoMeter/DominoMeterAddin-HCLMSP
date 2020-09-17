package prominic.dominometer.report;

import java.io.File;
import java.io.FilenameFilter;

public class SearchFiles {
	public static File[] endsWith(String dir, final String endsWith) {
		File f = new File(dir);
		return f.listFiles(new FilenameFilter() {
		    public boolean accept(File dir, String name) {
		        return name.endsWith(endsWith);
		    }
		});
	}
}
