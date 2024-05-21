package binancebot.utilities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import binancebot.FindBooster;

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
	
	/** add new symbol into pairs file */
	public static synchronized void addSymbolsToPairsFile(String symbol, String filePath) throws Exception {
		if(!fileContains(filePath, symbol)) {
			try {
				FileWriter fw = new FileWriter(filePath, true); // the true will append the new data
				fw.write(System.lineSeparator() + symbol);
				fw.close();
			} catch (Exception e) {
				System.err.println("Exception writing into file: " + filePath + " Exception: " + e.toString());
			}
			
			// create the updated object and merge/add it into the symbolsObj list and symbolsList
			FindBooster.restAPI.getSingleSymbols24hTicker(symbol).forEach((key, value) -> FindBooster.symbolsObj.put(key, value));
			FindBooster.symbolsList.add(symbol);
		}
	}
    
	/** remove symbol from pairs properties list */
    public static synchronized void removeSymbolFromPairsFile(String symbol, String filePath) {
          {
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(filePath + ".tmp"))) {

                List<String> filteredLines = new ArrayList<>();
                String line;

                while ((line = reader.readLine()) != null) {
                    if (!line.contains(symbol)) {
                        filteredLines.add(line);
                    }
                }

                // Write the filtered lines back to the original file
                try (BufferedWriter originalWriter = new BufferedWriter(new FileWriter(filePath))) {
                    for (String filteredLine : filteredLines) {
                        originalWriter.write(filteredLine);
                        originalWriter.newLine();
                    }
                }
                
                // remove symbol from symbolsObj list and symbols list
				FindBooster.symbolsObj.remove(symbol);
				FindBooster.symbolsList.remove(symbol);

                System.out.println("Unlisted symbol was found! Removed " + symbol + " from pairs.properties!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
	

	/** remove log files */
	public static void cleanupAllLogFiles() {
		if (Boolean.valueOf(Env.getProperty("cleanupAllLogFiles"))) {
			try {
//				findings are always kept for now!
//				File file1 = new File("./findings.properties"); 
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