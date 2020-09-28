package prominic.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;

public class SearchFiles {
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
	
	public static File[] sortFilesByNewest(File files[]) {
		Arrays.sort(files, new Comparator<File>(){
			public int compare(File f1, File f2) {
				return Long.valueOf(f2.lastModified()).compareTo(Long.valueOf(f1.lastModified()));
			} });
		
		return files;
	}
	
	
}
