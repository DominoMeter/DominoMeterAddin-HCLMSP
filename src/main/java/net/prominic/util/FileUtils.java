package net.prominic.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FileUtils {
	public static File[] endsWith(File dir, final String endsWith) {
		return dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(endsWith);
			}
		});
	}

	public static boolean folderExists(String path) {
		File dir = new File(path);
		return dir.exists() && dir.isDirectory();
	}

	public static File[] startsWith(File dir, final String startsWith) {
		return dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(startsWith);
			}
		});
	}

	public static File[] sortFilesByModified(File files[], final boolean asc) {
		Arrays.sort(files, new Comparator<File>(){
			@Override
			public int compare(File f1, File f2) {
				Long l1 = Long.valueOf(f1.lastModified());
				Long l2 = Long.valueOf(f2.lastModified());

				return asc ? l1.compareTo(l2) : l2.compareTo(l1);
			} });

		return files;
	}

	public static StringBuffer readFileContentFilter(String filePath, String[] filterStartWith, boolean removeEmptyString) {
		File file = new File(filePath);
		if (!file.exists()) return null;

		StringBuffer sb = null;
		String lineSeparator = System.getProperty("line.separator");
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			sb = new StringBuffer();
			String st;
			while ((st = br.readLine()) != null) {
				String stTrimmer = st.trim();
				boolean append = true;

				// apply Filter
				for(int i=0; i<filterStartWith.length && append; i++) {
					if (stTrimmer.startsWith(filterStartWith[i])) {
						append = false;
					}
				}

				// remove empty lines?
				if (append && removeEmptyString && stTrimmer.isEmpty()) {
					append = false;
				}

				if (append) {
					sb.append(st);
					sb.append(lineSeparator);	
				}
			}
			br.close();
		}
		catch (IOException e) {}

		return sb;
	}

	public static StringBuffer readFileContent(String filePath) {
		File file = new File(filePath);
		if (!file.exists()) return null;

		StringBuffer sb = null;
		String lineSeparator = System.getProperty("line.separator");
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			sb = new StringBuffer();
			String st;
			while ((st = br.readLine()) != null) {
				sb.append(st);
				sb.append(lineSeparator);
			}
			br.close();
		}
		catch (IOException e) {}

		return sb;
	}

	/*
	 * All files in folder and in all sub-folders
	 */
	public static List<File> listFiles(String directoryName) {
		File directory = new File(directoryName);

		// Get all files from a directory.
		File[] fList = directory.listFiles();
		if(fList == null) return null;

		List<File> files = new ArrayList<File>();
		for (File file : fList) {      
			if (file.isFile()) {
				files.add(file);
			} else if (file.isDirectory()) {
				List<File> dirFiles = listFiles(file.getAbsolutePath());
				if (dirFiles!=null && dirFiles.size() > 0) {
					files.addAll(dirFiles);	
				}
			}
		}

		return files;
	}
}
