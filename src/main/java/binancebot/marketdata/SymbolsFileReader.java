package binancebot.marketdata;

import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

import binancebot.utilities.Log;

public class SymbolsFileReader {

	public static boolean allSymbolsLoaded = false;

	/**
	 * load all pairs from file BUT ignore if commented out with "#"
	 */
	public static ArrayList<String> loadPairsFromFile(String filepath) {
		ArrayList<String> allSymbols = new ArrayList<String>();
		Scanner scanner;
		try {
			scanner = new Scanner(new File(filepath));
			while (scanner.hasNextLine()) {
				String val = scanner.nextLine();
				if (!val.startsWith("#") && !val.equals("")) {
					allSymbols.add(val);
				}
			}
			scanner.close();
		} catch (Exception e) {
			Log.log(e.toString());
		}

		return allSymbols;
	}

}
