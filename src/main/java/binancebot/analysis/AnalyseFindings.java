package binancebot.analysis;

import java.io.BufferedReader;
import java.io.FileReader;
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
import binancebot.utilities.StopWatch;

public class AnalyseFindings {

	// threads & ip weight settings
	public static volatile int PARALLEL_THREADS = 250;
	public static volatile int ip_weight_current_run = 0;
//	public static volatile int ip_weight_max = 4500;

	// best trade settings finder
	public static double sellpoint = 2.65; // 2.65
	public static double stoploss = 1.6; // 1.6
	
	public static boolean showSkipped = false;

	// RSI (TODO welcher is der richtige 15m -1h 4h 1D, ...)
	public static volatile double rsimax = 54; // 56
	public static volatile double rsimin = 0;

	// btc min 24h > 0.0
	public static boolean dailyBTC24HCheck = false;
	public static volatile double btc_minDailyPoint = 0.0;

	// weekend skip TODO (enable weekend after testing data)
	public static boolean skipDaysActive = false;
	public static List<String> skipDaysList = Arrays.asList("6,7,1".split("[,\\s]+"));

	// verify bot settings
	public static String logfilename = "findings.properties";
	public static final String candleInterval = "15m";

	// summary variables
	public static volatile int winCnt = 0;
	public static volatile int lossCnt = 0;
	public static volatile int skipCnt = 0;
	public static volatile int skipByDayCnt = 0;
	public static volatile int openCnt = 0;

	// AVG variables
	public static volatile double total_highest = 0.0;
	public static volatile double total_lowest = 0.0;
	public static volatile double total_rsi = 0.0;

	public static volatile double skipped_highest = 0.0;
	public static volatile double skipped_lowest = 0.0;
	public static volatile double skipped_rsi = 0.0;

	public static volatile double win_highest = 0.0;
	public static volatile double win_lowest = 0.0;
	public static volatile double win_rsi = 0.0;

	public static volatile double loss_highest = 0.0;
	public static volatile double loss_lowest = 0.0;
	public static volatile double loss_rsi = 0.0;

	public static void main(String[] args) throws Exception, InterruptedException {

		// read symbol findings from file
		List<List<String>> boostedSymbols = readFindingsFromFile();

		// IP pre-calculation check
//		int ip_weight_precalc = boostedSymbols.size() * 2;
//		if (ip_weight_precalc > 5800) {
//			throw new Exception("IP weight: " + ip_weight_precalc + " -> We will overloaded API!!! Findings from file: " + boostedSymbols.size());
//		} else {
//			System.out.println("---------------------------------------");
//			System.out.println("Pre-Calculation of IP Weight: " + ip_weight_precalc);
//			System.out.println("---------------------------------------");
//		}

		// max thread check, if too many threads are set limit to boosters found in file
		if (PARALLEL_THREADS > boostedSymbols.size()) {
			PARALLEL_THREADS = boostedSymbols.size();
		}

		// measure calculation speed
		StopWatch.start("analyse_findings");

		// print run info
		printAnalysisInfo();

		// write result file header (findings.csv to keep analysis we did)
		writeAnalysisFileHeader();

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

		System.out.println("....................................................................................");
		System.out.println("AVG Total Highest: " + DF.format(total_highest / boostedSymbols.size(), 2));
		System.out.println("AVG Total Lowest: " + DF.format(total_lowest / boostedSymbols.size(), 2));
		System.out.println("AVG Total RSI: " + DF.format(total_rsi / boostedSymbols.size(), 2));

		if (skipCnt > 0) {
			System.out.println("....................................................................................");
			System.out.println("AVG Skipped Highest: " + DF.format(skipped_highest / skipCnt, 2));
			System.out.println("AVG Skipped Lowest: " + DF.format(skipped_lowest / skipCnt, 2));
			System.out.println("AVG Skipped RSI: " + DF.format(skipped_rsi / skipCnt, 2));
		}

		if (winCnt > 0) {
			System.out.println("....................................................................................");
			System.out.println("AVG Win Highest: " + DF.format(win_highest / winCnt, 2));
			System.out.println("AVG Win Lowest: " + DF.format(win_lowest / winCnt, 2));
			System.out.println("AVG Win RSI: " + DF.format(win_rsi / winCnt, 2));
		}

		if (lossCnt > 0) {
			System.out.println("....................................................................................");
			System.out.println("AVG Loss Highest: " + DF.format(loss_highest / lossCnt, 2));
			System.out.println("AVG Loss Lowest: " + DF.format(loss_lowest / lossCnt, 2));
			System.out.println("AVG Loss RSI: " + DF.format(loss_rsi / lossCnt, 2));
		}

		System.out.println("=========================================================================================");
		System.out.println("ANALYSE END RESULT WITH GIVEN PARAMETERS");
		System.out.println("-> Take Profit At: 	" + sellpoint);
		System.out.println("-> StopLoss At: 	" + stoploss);
		System.out.println("-> RSI Max: 		" + rsimax);
		System.out.println("-> BTC Daily On: 	" + dailyBTC24HCheck);
		System.out.println("-> BTC Daily Min:	" + btc_minDailyPoint);
		System.out.println("=========================================================================================");
		System.out.println("# TEST RESULT END SUMMARY:");
		System.out.println("=========================================================================================");
		System.out.println("Tokens Analysed Total:   " + (winCnt + lossCnt + skipCnt + openCnt));
		System.out.println("-----------------------------------------------------------------------------------------");
		System.out.println("Potential Skipped (weekend):   	" + skipCnt + " (" + skipByDayCnt + ")");
		System.out.println("Potential Win:     		" + winCnt);
		System.out.println("Potential Losses:  		" + lossCnt);
		System.out.println("Potential Running:     		" + openCnt);

		// PROFIT OR LOSS IN TOTAL
		if(winCnt!=0 && lossCnt!=0) {
			System.out.println("#########################################################################################");
			System.out.println("!!! WIN 2 LOSS/OPEN RATIO:  	" + winCnt * 100 / (winCnt + lossCnt + openCnt) + "%");
			System.out.println("#########################################################################################");
		} else {
			System.out.println("#########################################################################################");
			System.out.println("!!! WIN 2 LOSS/OPEN RATIO: 	NOT ENOUGH DATA!");
			System.out.println("#########################################################################################");
		}
		System.out.println("!!! TOTAL POSSIBLE PROFIT: 	" + DF.format(winCnt * sellpoint - (lossCnt + openCnt) * stoploss, 2) + "% !!!");
		System.out.println("#########################################################################################");

		System.out.println("=========================================================================================");
		StopWatch.end("analyse_findings");
		System.out.println("=========================================================================================");
	}

	private static HashMap<String, String> analyseSymbol(String symbol, String foundAtDate, double foundPrice, String rsi, String btc_daily) {

		// TODO IP WEIGHT BREAK TO NOT OVERLOAD
		if (ip_weight_current_run > 5000) {
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
			// double openPrice = Double.valueOf(candles[i].split(",")[1].replace("\"",""));
			// double closePrice =
			// Double.valueOf(candles[i].split(",")[3].replace("\"",""));
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
					// double sopenPrice =
					// Double.valueOf(startCandle[n].split(",")[1].replace("\"",""));
					// double sclosePrice =
					// Double.valueOf(startCandle[n].split(",")[3].replace("\"", ""));
					// long scandleClosingTime =
					// Long.valueOf(startCandle[n].split(",")[6].replace("\"", ""));

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

		// calculate total AVG values
		total_highest = total_highest + Double.valueOf(results.get("highestInPercent"));
		total_lowest = total_lowest + Double.valueOf(results.get("lowestInPercent"));
		total_rsi = total_rsi + Double.valueOf(results.get("rsi"));

		// ##################################################################################################
		// print summary to console
		// ##################################################################################################

		// rsi ok, enough boosted, not stopped out
		String outcome = "";

		// A) rsi AND BTC daily or weekend day - all 3 can prevent and exits regardless LOSS OR WIN
		boolean skipByDayList = isASkipDay(foundAtDate);

		if (skipByDayList) {
			skipByDayCnt++;
		}

		if (skipByDayList
				|| !(Double.valueOf(results.get("rsi")) >= rsimin && Double.valueOf(results.get("rsi")) <= rsimax)
				|| !isBTCAboveMinDaily(Double.valueOf(results.get("btcDaily")))) {

			outcome = "SKIPPED";
			skipCnt++;
			skipped_highest = skipped_highest + Double.valueOf(results.get("highestInPercent"));
			skipped_lowest = skipped_lowest + Double.valueOf(results.get("lowestInPercent"));
			skipped_rsi = skipped_rsi + Double.valueOf(results.get("rsi"));
		} else {

			// B) stopped out
			if (Double.valueOf(results.get("lowestInPercent")) >= stoploss) {
				outcome = "LOSS";
				lossCnt++;
				loss_highest = loss_highest + Double.valueOf(results.get("highestInPercent"));
				loss_lowest = loss_lowest + Double.valueOf(results.get("lowestInPercent"));
				loss_rsi = loss_rsi + Double.valueOf(results.get("rsi"));
			} else if (Double.valueOf(results.get("highestInPercent")) >= sellpoint) {
				// B) price reached: WIN
				outcome = "WIN";
				winCnt++;
				win_highest = win_highest + Double.valueOf(results.get("highestInPercent"));
				win_lowest = win_lowest + Double.valueOf(results.get("lowestInPercent"));
				win_rsi = win_rsi + Double.valueOf(results.get("rsi"));
			} else {
				outcome = "RUN";
				openCnt++;
			}

		}

		// show wins, losses, open or skipped summary
//			System.out.println(StringUtils.rightPad(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(foundTimeLong), 22)
//					+ StringUtils.rightPad(outcome, 10) + StringUtils.rightPad(symbol, 10, " ") + " Price: "
//					+ StringUtils.rightPad(DF.format(foundPrice), 13) + " Highest: "
//					+ StringUtils.rightPad(results.get("highestPrice"), 11) + " HighestInPercent: "
//					+ StringUtils.rightPad(DF.format(Double.valueOf(results.get("highestInPercent")), 2), 9)
//					+ " LowestPrice: "
//					+ StringUtils.rightPad(DF.format(Double.valueOf(results.get("lowestPrice")), 8), 11)
//					+ " LowestInPercent: "
//					+ StringUtils.rightPad(DF.format(Double.valueOf(results.get("lowestInPercent")), 2), 9)
//					+ " RSI-" + candleInterval +": " + DF.format(Double.valueOf(results.get("rsi")), 0));

		// short version
		if(outcome.equals("SKIPPED") && !showSkipped) {
			// dont show skipped status
		} else {
			System.out.println(
					StringUtils.rightPad(outcome, 10) + StringUtils.rightPad(symbol, 10, " ") + " HighestInPercent: "
							+ StringUtils.rightPad(DF.format(Double.valueOf(results.get("highestInPercent")), 2), 9)
							+ " LowestInPercent: "
							+ StringUtils.rightPad(DF.format(Double.valueOf(results.get("lowestInPercent")), 2), 9)
							+ " RSI: " + DF.format(Double.valueOf(results.get("rsi")), 0));
		}

		// append result details into file
		appendIntoAnalysisFile(
				StringUtils.rightPad(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(foundTimeAsLong), 22) + ";"
						+ StringUtils.rightPad(outcome, 10) + ";" + StringUtils.rightPad(symbol, 10, " ") + ";"
						+ StringUtils.rightPad(DF.format(foundPrice), 13) + ";"
						+ StringUtils.rightPad(results.get("highestPrice"), 10) + ";="
						+ StringUtils.rightPad(DF.format(Double.valueOf(results.get("highestInPercent")), 2), 9) + ";"
						+ StringUtils.rightPad(DF.format(Double.valueOf(results.get("lowestPrice")), 8), 11) + ";="
						+ StringUtils.rightPad(DF.format(Double.valueOf(results.get("lowestInPercent")), 2), 9) + ";"
						+ DF.format(Double.valueOf(results.get("rsi")), 0));

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

	private static void printAnalysisInfo() {
		System.out.println("=========================================================================================");
		System.out.println("VERIFY TEST PARAMETER ON HISTORICAL DATA:");
		System.out.println("=========================================================================================");
	}

	private static boolean isBTCAboveMinDaily(double currentBTCDaily) {
		if (dailyBTC24HCheck) {
			return (currentBTCDaily >= btc_minDailyPoint);
		}
		return true;
	}

	private static void writeAnalysisFileHeader() {
//		File file = new File("findings_analysis.csv");
//		file.delete();
//		Log.intoFile("FOUNDAT;RESULT;SYMBOL;PRICE;HIGHEST;HIGHEST_IN_PERCENT;LOWEST;LOWEST_IN_PERCENT;RSI",
//				"findings_analysis.csv");
	}

	private static void appendIntoAnalysisFile(String line) {
//		Log.intoFile(line, "./findings_analysis.csv");
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

}
