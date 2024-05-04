package binancebot.utilities;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import binancebot.marketdata.Symbol;  
public abstract class DF {

	private static final DecimalFormat f0 = new DecimalFormat("0", new DecimalFormatSymbols(Locale.ENGLISH));
	private static final DecimalFormat f1 = new DecimalFormat("0.#", new DecimalFormatSymbols(Locale.ENGLISH));
	private static final DecimalFormat f2 = new DecimalFormat("0.##", new DecimalFormatSymbols(Locale.ENGLISH));
	private static final DecimalFormat f3 = new DecimalFormat("0.###", new DecimalFormatSymbols(Locale.ENGLISH));
	private static final DecimalFormat f4 = new DecimalFormat("0.####", new DecimalFormatSymbols(Locale.ENGLISH));
	private static final DecimalFormat f5 = new DecimalFormat("0.#####", new DecimalFormatSymbols(Locale.ENGLISH));
	private static final DecimalFormat f6 = new DecimalFormat("0.######", new DecimalFormatSymbols(Locale.ENGLISH));
	private static final DecimalFormat f7 = new DecimalFormat("0.#######", new DecimalFormatSymbols(Locale.ENGLISH));
	private static final DecimalFormat f8 = new DecimalFormat("0.########", new DecimalFormatSymbols(Locale.ENGLISH));

	/**
	 * format double to string in readable format (defaults to 8 commas for BTC)
	 */
	public static String format(double value) {
		return format(value, 8);
	}
	
	/** format double to string in readable format with given places after comma */
	public static String format(double value, int placesAfterComma) {
		switch (placesAfterComma) {
		case 0:
			return f0.format(value);
		case 1:
			return f1.format(value);
		case 2:
			return f2.format(value);
		case 3:
			return f3.format(value);
		case 4:
			return f4.format(value);
		case 5:
			return f5.format(value);
		case 6:
			return f6.format(value);
		case 7:
			return f7.format(value);
		case 8:
			return f8.format(value);
		default:
			return f8.format(value);
		}

	}
	
	/** format double to string in readable format with given places after comma */
	public static String format(double value, int placesAfterComma, RoundingMode roundingMode) {
		switch (placesAfterComma) {
		case 0:
			f0.setRoundingMode(roundingMode);
			return f0.format(value);
		case 1:
			f1.setRoundingMode(roundingMode);
			return f1.format(value);
		case 2:
			f2.setRoundingMode(roundingMode);
			return f2.format(value);
		case 3:
			f3.setRoundingMode(roundingMode);
			return f3.format(value);
		case 4:
			f4.setRoundingMode(roundingMode);
			return f4.format(value);
		case 5:
			f5.setRoundingMode(roundingMode);
			return f5.format(value);
		case 6:
			f6.setRoundingMode(roundingMode);
			return f6.format(value);
		case 7:
			f7.setRoundingMode(roundingMode);
			return f7.format(value);
		case 8:
			f8.setRoundingMode(roundingMode);
			return f8.format(value);
		default:
			return f8.format(value);
		}

	}

	/** converts a double to string and removed all 0000 after the comma */
	public static String symbolsDouble2Str(Symbol symbol, double value) {
		switch (symbol.getPlacesAfterComma()) {
		case 0:
			return f0.format(value);
		case 1:
			return f1.format(value);
		case 2:
			return f2.format(value);
		case 3:
			return f3.format(value);
		case 4:
			return f4.format(value);
		case 5:
			return f5.format(value);
		case 6:
			return f6.format(value);
		case 7:
			return f7.format(value);
		case 8:
			return f8.format(value);
		default:
			return String.valueOf(value);
		}
	}

	/** converts a double to string and removed all 0000 after the comma */
	public static String doubleToString(double value, Symbol symbol) {
		switch (symbol.getPlacesAfterComma()) {
		case 0:
			return f0.format(value);
		case 1:
			return f1.format(value);
		case 2:
			return f2.format(value);
		case 3:
			return f3.format(value);
		case 4:
			return f4.format(value);
		case 5:
			return f5.format(value);
		case 6:
			return f6.format(value);
		case 7:
			return f7.format(value);
		case 8:
			return f8.format(value);
		default:
			return String.valueOf(value);
		}
	}
	
}
