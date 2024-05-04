package binancebot.utilities;

import java.io.File;
import java.io.FileWriter;
import java.util.Scanner;

public class Log {
	
	public static void log(String message) {
		System.out.println(GlobalHelper.getCurrentTimeStamp() + ": " + message);
	}
	
	public static void error(String message) {
		System.err.println(message);
	}
	
	public static void debug(String message) {
		if(Boolean.valueOf(Env.getProperty("debug"))) {
			System.out.println(GlobalHelper.getCurrentTimeStamp() + ": " + message);
		}
	}
	
	/** log message and print into file at the same time by specifying a target file */
	public static void logAndFile(String message, String filePath) {
		log(message);
		intoFile(message, filePath);
	}
	
	/** log message and print into file at the same time by specifying a target file */
	public static void debugAndFile(String message, String filePath) {
		debug(message);
		intoFile(message, filePath);
	}
	
	/** log message and print into file at the same time by specifying a target file */
	public static void logAndMail(String subject) {
		log(subject);
		Mail.send(subject, "");
	}
	
	/** log message and print into file at the same time by specifying a target file */
	public static void logAndMail(String subject, String message) {
		log(subject + ": " + message);
		Mail.send(subject, message);
	}
	
	/** append log messages into a file only */
	public static void intoFile(String message, String filePath) {
		try {
			FileWriter fw = new FileWriter(filePath, true); // the true will append the new data
			fw.write(GlobalHelper.getCurrentTimeStamp() + ";" + message + System.lineSeparator());
			fw.close();
		} catch (Exception e) {
			System.err.println("Exception writing file: " + e.toString());
		}
	}
	
	public static void newSymbolsIntoPairsFile(String message, String filePath) {
		if(!fileContains(filePath,message)) {
			try {
				FileWriter fw = new FileWriter(filePath, true); // the true will append the new data
				fw.write(System.lineSeparator() + message);
				fw.close();
			} catch (Exception e) {
				System.err.println("Exception writing file: " + e.toString());
			}
		}
	}

	/** remove log files */
	public static void cleanupAllLogFiles() {
		if (Boolean.valueOf(Env.getProperty("cleanupAllLogFiles"))) {
			try {
//				File file1 = new File("./findings.properties"); TODO enable findings deletion when bot is stable enough
//				if(file1.exists()) {
//					file1.delete();
//				}
				File file2 = new File("./result.log");
				if(file2.exists()) {
					file2.delete();
				}
				File file3 = new File("./alive.log");
				if(file3.exists()) {
					file3.delete();
				}
				Log.log("Successfully removed all *.log files!");
				Log.log("-----------------------------------------------");
			} catch (Exception e) {
				Log.log("Could not cleanup log files: " + e.toString());
			}
		}
	}
	
	/** verify if a file contains a certain string */
	private static boolean fileContains(String filepath, String text) {
		
		File file = new File(filepath);
		if(file.exists()) {
			Scanner scanner = null;
			try {
				scanner = new Scanner(file);
				while (scanner.hasNextLine()) {
					String val = scanner.nextLine();
					if (val.toString().contains(text)) {
						return true;
					}
				}
				scanner.close();
			} catch (Exception e) {
				Log.log(e.toString());
			} finally {
				scanner.close();
			}
		}
		return false;
		
	}

}