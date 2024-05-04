package binancebot.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.binance.connector.client.impl.SpotClientImpl;

import binancebot.FindBooster;
import binancebot.account.PrivateConfig;
import binancebot.utilities.DF;
import binancebot.utilities.Env;
import binancebot.utilities.Log;
import binancebot.utilities.StopWatch;

public class AnalyseFindingsReport {

	// threads & ip weight settings
	public static volatile int PARALLEL_THREADS = 15;
	public static volatile int ip_weight_current_run = 0;

	// best trade settings finder
	public static double sellpoint = FindBooster.sellingPoint;
	public static double stoploss = FindBooster.stopLossPoint;

	// RSI
	public static volatile double rsimax = Double.valueOf(Env.getProperty("RSI.max"));
	public static volatile double rsimin = Double.valueOf(Env.getProperty("RSI.min"));

	// btc min 24h
	public static boolean dailyBTCheckActive = false;
	public static volatile double btc_minDailyPoint = 0.0;

	// weekend skip
	public static boolean skipDaysActive = false;
	public static List<String> skipDaysList = Arrays.asList("1".split("[,\\s]+"));

	// verify bot settings
	public static String logfilename = "findings.properties";
	public static final String candleInterval = "15m";

	// summary variables
	public static volatile int winCnt = 0;
	public static volatile int lossCnt = 0;
	public static volatile int skipCnt = 0;
	public static volatile int skipByDayCnt = 0;
	public static volatile int openCnt = 0;

	public static volatile String final_report = "";
	
	 public static void main(String[] args) throws InterruptedException, Exception {
		 AnalyseFindingsReport.generateReport();
	 }

	public static String generateReport() throws Exception, InterruptedException {

		// skip analysis if file not written yet
		File reportFile = new File(logfilename);
		if (!reportFile.exists()) {
			Log.log("  -> Findings-Report skipped! No data yet!");
			return "NO DATA!";
		}
		
		// read symbol findings from file
		List<List<String>> boostedSymbols = readFindingsFromFile();

		// max thread check, if too many threads are set limit to boosters found in file
		if (PARALLEL_THREADS > boostedSymbols.size()) {
			PARALLEL_THREADS = boostedSymbols.size();
		}

		// measure calculation speed
		StopWatch.start("analyse_findings");

		// print run info
		logReport("=========================================================================================");
		logReport("VERIFY TEST PARAMETER ON HISTORICAL DATA:");
		logReport("=========================================================================================");

		CountDownLatch latch = new CountDownLatch(PARALLEL_THREADS);
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(PARALLEL_THREADS);

		// loop boosters in findings.properties
		for (int i = 0; i <= boostedSymbols.size() - 1; i++) {

			// fetch data
			String foundAt = boostedSymbols.get(i).get(0);
			String symbolname = boostedSymbols.get(i).get(1);
			String price = boostedSymbols.get(i).get(2);
			String RSI = boostedSymbols.get(i).get(5);
			String btc_daily = (boostedSymbols.get(i).size() >= 7) ? boostedSymbols.get(i).get(6) : "0.0";

			// analyze data
			executor.schedule(() -> {

				// PERFORM ANALYSIS
				analyseSymbol(symbolname, foundAt, Double.valueOf(price), RSI, btc_daily);

			}, 300, TimeUnit.MILLISECONDS);

			// end current thread
			latch.countDown();

		}

		// wait for all scheduled threads
		latch.await();
		executor.shutdown();
		executor.awaitTermination(60000, TimeUnit.DAYS);

		logReport("=========================================================================================");
		logReport("# TEST REPORT SUMMARY:");
		logReport("=========================================================================================");
		logReport("Tokens Analysed Total:   " + (winCnt + lossCnt + skipCnt + openCnt));
		logReport("-----------------------------------------------------------------------------------------");
		logReport("Potential Skipped (weekend):   	" + skipCnt + " (" + skipByDayCnt + ")");
		logReport("Potential Win:	     		" + winCnt);
		logReport("Potential Losses:	  	" + lossCnt);
		logReport("Potential Running:     		" + openCnt);

		// PROFIT OR LOSS IN TOTAL
		if(winCnt!=0 && lossCnt!=0 && openCnt!=0) {
			logReport("#########################################################################################");
			logReport("!!! WIN 2 LOSS/OPEN RATIO:  	" + winCnt * 100 / (winCnt + lossCnt + openCnt) + "%");
			logReport("#########################################################################################");
			logReport("!!! TOTAL POSSIBLE PROFIT: 	" + DF.format(winCnt * sellpoint - (lossCnt + openCnt) * stoploss, 2) + "% !!!");
		} else {
			logReport("!!! TOTAL POSSIBLE PROFIT: 	NOT ENOUGH DATA!");
		}
		logReport("#########################################################################################");

		logReport("=========================================================================================");
		logReport("Report Generation took " + StopWatch.end("analyse_findings") + " Seconds!");
		logReport("=========================================================================================");
		
		// finally write report into file or send a mail
		writeToReportFile();
		
		// final log message
		Log.log("  -> Findings-Report has been generated!");
		
		return final_report;
	}

	private static HashMap<String, String> analyseSymbol(String symbol, String foundAtDate, double foundPrice, String rsi, String btc_daily) {

		// IP WEIGHT BREAK TO NOT OVERLOAD
		if (ip_weight_current_run > 4000) {
			try {
				Thread.sleep(60 * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		ip_weight_current_run = ip_weight_current_run + 2;

		// found price corrected to our buying price to be realistic
		foundPrice = foundPrice * FindBooster.TRADING_EXTRA_AT_Buy;

		// result collection
		HashMap<String, String> results = new HashMap<String, String>();
		double highestPriceReached = 0.0;
		double lowestPriceReached = foundPrice;
		long foundTimeAsLong = stringToLongDate(foundAtDate);

		// fetch candles from found time
		String[] candles = fetchCandlesticks(symbol, foundTimeAsLong);

		boolean veryFirstCandle = true;

		// ##################################################################################################
		// calculate all data via candlesticks
		// ##################################################################################################
		for (int i = 0; i < candles.length - 1; i++) {
			long candleOpenTime = Long.valueOf(candles[i].split(",")[0].replace("\"", ""));
			double highPrice = Double.valueOf(candles[i].split(",")[2].replace("\"", ""));
			double lowPrice = Double.valueOf(candles[i].split(",")[3].replace("\"", ""));
			long candleClosingTime = Long.valueOf(candles[i].split(",")[6].replace("\"", ""));

			// walk these 15 minutes in detail (900x 1s) if the high was before or after our
			// booster found time
			if (veryFirstCandle) {
				veryFirstCandle = false;
				String[] startCandle = fetchCandlesticks(symbol, "1s", String.valueOf(candleOpenTime),
						String.valueOf(candleClosingTime));
				for (int n = 0; n < startCandle.length - 1; n++) {
					long scandleOpenTime = Long.valueOf(startCandle[n].split(",")[0].replace("\"", ""));
					double shighPrice = Double.valueOf(startCandle[n].split(",")[2].replace("\"", ""));
					double slowPrice = Double.valueOf(startCandle[n].split(",")[3].replace("\"", ""));

					// verify lows and highs in the 15x 1m first candle ONLY starting from boost
					// found time!
					if (scandleOpenTime >= stringToLongDate(foundAtDate)) {

						// get lowest price since boost
						if (slowPrice < lowestPriceReached) {
							lowestPriceReached = slowPrice;
						}

						// get highest price since boost
						if (shighPrice > highestPriceReached) {
							highestPriceReached = shighPrice;
						}
					}
				}
				// go to next 15m candle from here
				continue;
			}

			// get lowest price since boost + stop loss check
			if (lowPrice < lowestPriceReached) {
				lowestPriceReached = lowPrice;
			}
			if (lowestPriceReached <= (foundPrice * (100 - stoploss) / 100)) {
				break;
			}

			// get highest price since boost + profit check
			if (highPrice > highestPriceReached) {
				highestPriceReached = highPrice;
			}
			if (highestPriceReached >= foundPrice * ((sellpoint / 100) + 1)) {
				break;
			}

		}

		// calculate result metrics
		results.put("boostPrice", DF.format(foundPrice));
		results.put("lowestPrice", DF.format(lowestPriceReached));
		results.put("lowestInPercent", String.valueOf((lowestPriceReached * 100 / foundPrice - 100) * (-1)));
		results.put("highestPrice", DF.format(highestPriceReached));
		results.put("highestInPercent", String.valueOf((foundPrice / highestPriceReached - 1.00) * (-100)));
		results.put("rsi", rsi);
		results.put("btcDaily", btc_daily);

		// ##################################################################################################
		// print summary to console
		// ##################################################################################################

		// rsi ok, enough boosted, not stopped out
		String outcome = "";

		// A) rsi AND BTC daily or weekend day - all 3 can prevent and exits regardless
		// LOSS OR WIN
		boolean skipByDayList = isASkipDay(foundAtDate);

		if (skipByDayList) {
			skipByDayCnt++;
		}
		if (skipByDayList
				|| !((Double.valueOf(results.get("rsi")) >= rsimin) && (Double.valueOf(results.get("rsi")) <= rsimax))
				|| !isBTCAboveMinDaily(Double.valueOf(results.get("btcDaily")))) {
			outcome = "SKIPPED";
			skipCnt++;
		} else {

			// B) stopped out
			if (Double.valueOf(results.get("lowestInPercent")) >= stoploss) {
				outcome = "LOSS";
				lossCnt++;
			} else if (Double.valueOf(results.get("highestInPercent")) >= sellpoint) {
				// B) price reached: WIN
				outcome = "WIN";
				winCnt++;
			} else {
				outcome = "RUN";
				openCnt++;
			}
		}
		
		// short version
		logReport(StringUtils.rightPad(outcome, 10) + StringUtils.rightPad(symbol, 10, " ") + " HighestInPercent: "
						+ StringUtils.rightPad(DF.format(Double.valueOf(results.get("highestInPercent")), 2), 9)
						+ " LowestInPercent: "
						+ StringUtils.rightPad(DF.format(Double.valueOf(results.get("lowestInPercent")), 2), 9)
						+ " RSI: " + DF.format(Double.valueOf(results.get("rsi")), 0));

		return results;

	}

	private static List<List<String>> readFindingsFromFile() throws Exception {
		List<List<String>> records = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(logfilename))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (!line.startsWith("#")) {
					if (!line.equals("")) {
						String[] values = line.split(";");
						records.add(Arrays.asList(values));
					} else {
						continue;
					}
				}
			}
		}
		return records;
	}

	private static String[] fetchCandlesticks(String symbol, long foundTimeLong) {
		LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
		SpotClientImpl client = new SpotClientImpl(PrivateConfig.API_KEY, PrivateConfig.SECRET_KEY);
		parameters.put("symbol", symbol);
		parameters.put("interval", candleInterval);
		parameters.put("startTime", (foundTimeLong - 15 * 60 * 1000));
		parameters.put("limit", "1000");

		// fetch data points
		String result = client.createMarket().klines(parameters);
		result = result.replace("]]", "").replace("[[", "");
		return result.split("\\],\\[");
	}

	private static String[] fetchCandlesticks(String symbol, String interval, String starttime, String endtime) {
		LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
		SpotClientImpl client = new SpotClientImpl(PrivateConfig.API_KEY, PrivateConfig.SECRET_KEY);
		parameters.put("symbol", symbol);
		parameters.put("startTime", starttime);
		parameters.put("endTime", endtime);
		parameters.put("interval", interval);
		parameters.put("limit", "1000");

		// fetch data points
		String result = client.createMarket().klines(parameters);
		result = result.replace("]]", "").replace("[[", "");
		return result.split("\\],\\[");
	}

	private static long stringToLongDate(String foundTime) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDateTime dateTime = LocalDateTime.parse(foundTime, formatter);
		ZoneId berlinZone = ZoneId.of("Europe/Berlin");
		ZonedDateTime berlinDateTime = dateTime.atZone(berlinZone);
		long foundTimeLong = berlinDateTime.toInstant().toEpochMilli();
		return foundTimeLong;
	}
	
	private static void logReport(String content) {
		final_report += content + "\n";
	}

	private static boolean isBTCAboveMinDaily(double currentBTCDaily) {
		if (dailyBTCheckActive) {
			return (currentBTCDaily >= btc_minDailyPoint);
		}
		return true;
	}

	/**
	 * write from string whole summary into a file
	 */
	private static void writeToReportFile() {
		System.out.println(final_report);
//		String reportFile = "findings_report_"+ GlobalHelper.formatTimeStamp(System.currentTimeMillis(),"yyyy_MM_dd_hh_mm") + ".log";
//		intoReportFile(final_report, reportFile);
	}

	/** verify if current day is a weekend/skip day! */
	public static boolean isASkipDay(String dateString) {

		// if feature is disabled we take all days and skip none
		if (!skipDaysActive) {
			return false;
		}

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		Date myDate = null;
		try {
			// convert string to date
			myDate = sdf.parse(dateString);
			Calendar c = Calendar.getInstance();
			c.setTime(myDate);

			// verify if date is a weekend/skip day as we define them
			if (skipDaysList.contains(String.valueOf(c.get(Calendar.DAY_OF_WEEK)))) {
				return true;
			} else {
				return false;
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return false;

	}
	
	/** append log messages into a file only */
	public static void intoReportFile(String message, String filePath) {
		try {
			FileWriter fw = new FileWriter(filePath, true); // the true will append the new data
			fw.write(message + System.lineSeparator());
			fw.close();
		} catch (Exception e) {
			System.err.println("Exception writing file: " + e.toString());
		}
	}

}
