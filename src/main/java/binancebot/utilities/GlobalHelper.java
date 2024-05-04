package binancebot.utilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.binance.connector.client.WebSocketStreamClient;
import com.binance.connector.client.impl.WebSocketStreamClientImpl;

import binancebot.marketdata.Symbol;

public abstract class GlobalHelper {

	// btc 24h min daily settings
	public static volatile boolean btc_DailyValue_active = Boolean.valueOf(Env.getProperty("btcDaily.active", "true"));
	public static volatile double btc_min_DailyValue = Double.valueOf(Env.getProperty("btcDaily.min", "0.0"));
	public static volatile double btc_current_24hChange = 0.0;
	public static volatile boolean is24hDailyGood = true;

	// skip days settings
	public static volatile boolean skipDaysActive = Boolean.valueOf(Env.getProperty("skipDays.active", "true"));
	public static volatile List<String> skipDaysList = Env.getProperties("skipDays.days");

	// parameters for crash handling
	public static volatile int marketCrashLossLimit = Integer.valueOf(Env.getProperty("marketCrash.maxLossInARow","5"));
	public static volatile String marketCrashCoolDownHours = Env.getProperty("marketCrash.cooldownInHours", "8");
	public static volatile Boolean marketCrashCheckActive = Boolean.valueOf(Env.getProperty("marketCrash.active","true"));

	// variables for crash handling
	public static volatile boolean marketCrash_IsCurrentlyCrashing = false;
	public static volatile int market_CrashCurrentLossCount = 0;

	/**
	 * calculate based on per stream updated 24h btc daily, if the value is above
	 * specified min btc 24h value
	 */
	public static boolean is24hBTCDailyGood() {
		if (btc_DailyValue_active) {
			try {
				if (btc_current_24hChange >= GlobalHelper.btc_min_DailyValue) {
					return true;
				} else {
					return false;
				}
			} catch (Throwable f) {
				Log.logAndMail("Exception: Issue in daily btc check occurred: " + f.toString());
			}
		}
		return true;
	}

	/** verify if btc daily is higher than expected per property definition */
	public static void startBtc24hChangeStream() {
		WebSocketStreamClient client = new WebSocketStreamClientImpl();
		client.symbolTicker("btceur", ((event) -> {
			org.json.simple.JSONObject object = (org.json.simple.JSONObject) org.json.simple.JSONValue.parse(event);
			btc_current_24hChange = Double.valueOf(object.get("P").toString());
		}));
	}

	/** cool down for an symbol, any given time */
	public static void cooldownSymbolForSomeHours(Symbol symbol, String sleepingHours) {

		// either from method signature given value or fallback from file, default (0)
		int hours = 0;
		if (sleepingHours != null) {
			hours = Integer.valueOf(sleepingHours);
		} else {
			hours = Integer.valueOf(Env.getProperty("coolDownInHours", "0"));
		}

		if (hours > 0) {
			long coolDownPeriod = System.currentTimeMillis() + (1000 * 60 * 60 * hours);

			Log.log(symbol.getName() + ": Cooldown from now for " + hours + " hour/s!");
			// Log.log("===============================================");
			while (System.currentTimeMillis() <= coolDownPeriod) {
				try {
					Thread.sleep(1000 * 60 * 60); // sleep 1 hours & print message that still on cooldown
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			Log.log(symbol.getName() + ": Cooldown of " + hours + " hours is over! Let's rock again!");
		}
	}

	/** cool down for an symbol */
	public static void cooldownSymbolForSomeHours(Symbol symbol) {
		cooldownSymbolForSomeHours(symbol, null);
	}

	/** verify if current day is a weekend/skip day! */
	public static boolean isASkipDay() {
		if (skipDaysActive) {

			// get todays day
			Calendar cal = Calendar.getInstance();

			// verify if we have a skip day or not
			if (skipDaysList.contains(String.valueOf(cal.get(Calendar.DAY_OF_WEEK)))) {
				return true;
			} else {
				return false;
			}
		} else {
			// if disabled we do not skip any day!
			return false;
		}
	}

	/**
	 * give a timestamp and get back formatted string
	 * 
	 * @param timeInMilliseconds
	 * @param dateFormat         as String
	 * @return
	 */
	public static String formatTimeStamp(long timeInMilliseconds, String sdfFormat) {
		Date date = new Date(timeInMilliseconds);
		SimpleDateFormat sdf2 = new SimpleDateFormat(sdfFormat);
		return sdf2.format(date);
	}

	/** get current time stamp formatted */
	public static String getCurrentTimeStamp() {
		return getCurrentTimeStamp("yyyy-MM-dd HH:mm:ss");
	}

	/** get current time stamp formatted */
	public static String getCurrentTimeStamp(String format) {
		SimpleDateFormat sdfDate = new SimpleDateFormat(format);
		Date now = new Date();
		String strDate = sdfDate.format(now);
		return strDate;
	}

	/** check if windows or unix system */
	public static boolean isWindows() throws Exception {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}

	/** synchronize os time every x minutes */
	public static void synchronizeNTPClientTime() throws Exception {
		if (!isWindows()) {
			runUnixCommand("sudo /etc/init.d/ntp restart");
			// Log.log(runUnixCommand("sudo /etc/init.d/ntp restart"));
		}
	}

	/**
	 * execute unix command and prompt output
	 * 
	 * @param command
	 * @return
	 */
	public static String runUnixCommand(String command) {
		String line = null;
		String strstatus = "";
		try {
			String[] cmd = { "/bin/sh", "-c", command };
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = in.readLine()) != null) {
				strstatus = line;
			}
			in.close();
		} catch (Exception e) {

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			pw.flush();
			String stackTrace = sw.toString();
			int lenoferrorstr = stackTrace.length();
			if (lenoferrorstr > 500) {
				strstatus = "Error:" + stackTrace.substring(0, 500);
			} else {
				strstatus = "Error:" + stackTrace.substring(0, lenoferrorstr - 1);

			}
		}
		return strstatus;
	}

	/**
	 * if check is enabled returns the global crash handling variable status
	 * @return
	 * @throws Exception
	 */
	public static boolean isMarketCrashing() throws Exception {
		if(GlobalHelper.marketCrashCheckActive) {
			return GlobalHelper.marketCrash_IsCurrentlyCrashing;
		} else {
			return false;
		}
	}
	
	/** trade handling for global crash prevention -> counts up in case of losses, resets in case of win */
	public static void marketCrashHandling(boolean win) throws Exception {
		if(GlobalHelper.marketCrashCheckActive) {
			if (win) {
				// reset crash handling
				GlobalHelper.marketCrashHandlingReset();
			} else {
				// increment and global loss handling enabled in case
				GlobalHelper.market_CrashCurrentLossCount++;
				if (GlobalHelper.market_CrashCurrentLossCount >= GlobalHelper.marketCrashLossLimit) {
					GlobalHelper.marketCrash_IsCurrentlyCrashing = true;
					Log.log("############################################################");
					Log.log("############################################################");
					Log.log("############################################################");
					Log.log("MARKET CRASHING - WE LOST " + GlobalHelper.market_CrashCurrentLossCount + " DEALS IN A ROW! DISABLED TRADES!");
					Log.log("############################################################");
					Log.log("############################################################");
					Log.log("############################################################");
				}
			}
		}
	}
	
	/** resets market crash variables */
	public static void marketCrashHandlingReset() throws Exception {
		if(GlobalHelper.marketCrashCheckActive) {
			marketCrash_IsCurrentlyCrashing = false;
			market_CrashCurrentLossCount = 0;
		}
	}
	
}
