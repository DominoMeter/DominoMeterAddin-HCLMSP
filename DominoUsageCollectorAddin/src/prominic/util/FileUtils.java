package prominic.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;

public class FileUtils {
	public static File[] endsWith(File dir, final String endsWith) {
		return dir.listFiles(new FilenameFilter() {
		    public boolean accept(File dir, String name) {
		        return name.endsWith(endsWith);
		    }
		});
	}
	
	public static File[] startsWith(File dir, final String startsWith) {
		return dir.listFiles(new FilenameFilter() {
		    public boolean accept(File dir, String name) {
		        return name.startsWith(startsWith);
		    }
		});
	}
	
	public static File[] sortFilesByModified(File files[], final boolean asc) {
		Arrays.sort(files, new Comparator<File>(){
			public int compare(File f1, File f2) {
				Long l1 = Long.valueOf(f1.lastModified());
				Long l2 = Long.valueOf(f2.lastModified());

				return asc ? l1.compareTo(l2) : l2.compareTo(l1);
			} });
		
		return files;
	}
	
	
}
