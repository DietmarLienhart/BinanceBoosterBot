package binancebot.analysis;

import java.io.FileWriter;
import java.io.IOException;
//import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import binancebot.indicators.RSI;
import binancebot.marketdata.MarketDataRest;

//import com.binance.api.client.BinanceApiClientFactory;
//import com.binance.api.client.BinanceApiRestClient;
//import com.binance.api.client.domain.market.Candlestick;
//import com.binance.api.client.domain.market.CandlestickInterval;
//import com.binance.api.client.domain.market.TickerStatistics;

import binancebot.marketdata.Symbol;
import binancebot.marketdata.SymbolsFileReader;
import binancebot.utilities.StopWatch;

public class AnalyseData {

	// pairs and http client instance
	private static ArrayList<String> allSymbols = new ArrayList<String>();
	public static HashMap<String, Symbol> allSymbolsObj = new HashMap<String, Symbol>();

	static double rsi_1min = 0.0;
	static double rsi_15min = 0.0;
	static double rsi_1h = 0.0;
	static double rsi_1d = 0.0;
	static double rsi_1w = 0.0;
	static double rsi_1M = 0.0;

	public static void main(String[] args) throws Exception {

		int cnt = 0;

//		// load all pairs manually
		allSymbols = SymbolsFileReader.loadPairsFromFile("./pairs.properties");
		MarketDataRest restAPI = new MarketDataRest(allSymbols);
		allSymbolsObj = restAPI.getAllSymbols24hTicker();

		StopWatch.start("ALLRSI");

//		// header
		System.out.println("symbol;1m;15m;1h;1d;1w;1M");

		
		// testcode
		Symbol symbolx = allSymbolsObj.get("ADABTC");
		RSI rsicalc = new RSI();
//		Instant instant = Instant.parse( "2024-01-23T09:39:14");
		rsi_1h = rsicalc.calculateRSI(symbolx, "15m", "1000");
		System.out.println(rsi_1h);
		
		
		
		// analysed data
		for (String symbolname : allSymbols) {

			Symbol symbol = allSymbolsObj.get(symbolname);
			RSI rsi_calculator = new RSI();
//			rsi_1min = minute.calculateRSI(symbol, "1m", "1000");
			rsi_15min = rsi_calculator.calculateRSI(symbol, "15m", "1000");
			rsi_1h = rsi_calculator.calculateRSI(symbol, "1h", "1000");
			rsi_1d = rsi_calculator.calculateRSI(symbol, "1d", "1000");
			rsi_1w = rsi_calculator.calculateRSI(symbol, "1w", "1000");
			rsi_1M = rsi_calculator.calculateRSI(symbol, "1M", "1000");

			// low coins only
			if (rsi_1M > 15 && rsi_1M < 25) {
				if (rsi_1w > 25 && rsi_1w < 35) {
					if (rsi_1d > 35 && rsi_1d < 50) {
						if (rsi_15min > 40 && rsi_15min < 60) {
							rsi_1min = rsi_calculator.calculateRSI(symbol, "1m", "1000");
							System.out.println(symbolname + ";" + rsi_1min + ";" + rsi_15min + ";" + rsi_1h + ";"
									+ rsi_1d + ";" + rsi_1w + ";" + rsi_1M);
							cnt++;
						}
					}
				}
			}
		}

		StopWatch.end("ALLRSI");

		System.out.println("low tokens total found: " + cnt);

//		AnalyseData.boostCountMonthsBack("ADABTC");

	}

	public static String getCurrentTimeStamp() {
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date now = new Date();
		String strDate = sdfDate.format(now);
		return strDate;
	}

	public static String formatTime(Date timestamp) {
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String strDate = sdfDate.format(timestamp);
		return strDate;
	}

	/**
	 * save values found to file
	 * 
	 * @param text
	 * @throws Exception
	 */
	public static void saveValueInFile(String text) throws Exception {
		try {
			String filename = "./findings.properties";
			FileWriter fw = new FileWriter(filename, true); // the true will append the new data
			fw.write(text + System.lineSeparator());
			fw.close();
		} catch (IOException ioe) {
			System.err.println("IOException writing file: " + ioe.getMessage());
		}
	}

	@SuppressWarnings("unused")
	private static void boostCountMonthsBack(String pair) {

//		BinanceApiClientFactory factory;
//		BinanceApiRestClient client;

//		factory = BinanceApiClientFactory.newInstance(
//				"Ub5qDfZUDlkRfsVGiKF6OKZ35nj9LcXgNLuWTsS2gtVsM578l0Deq6sQXuc8u63z",
//				"1S3bWoVF91onrzztJIBQQrMX9IVwn2OeGBJtx135rpnkLBZyUaydqS3xMm51AplC");
//		client = factory.newRestClient();

		// how often a coin boosted x % on a single day!
		int daysback = 500;
		int boostLevel = 90;

//		List<Candlestick> candlesticks = client.getCandlestickBars(pair, CandlestickInterval.WEEKLY);

		StringBuilder boostz = new StringBuilder();
		int boostCnt = 0;

		double ath = 0;
		Date athTime = new Date(Long.MIN_VALUE);

//		if (candlesticks.size() >= daysback) {
//
//			for (Candlestick candle : candlesticks) {
//				double high = Double.parseDouble(candle.getHigh());
//				double low = Double.parseDouble(candle.getLow());
//				String time = formatTime(new Date(candle.getOpenTime()));
//				Date candleTime = new Date(candle.getOpenTime());
//
//				// boost in %
//				double boost = low * 100 / high;
//
//				if (boost > boostLevel) {
//					if (candleTime.after(athTime)) {
//						ath = boost;
//						boostz.setLength(0);
//						boostz.append(boost + "%;");
//						athTime = candleTime;
//					}
//					boostCnt++;
//				}
//			}
//
//			// print boost results
//			if (boostCnt >= 4) {
//				Log.log(pair + "; last boost on " + athTime + "; " + df.format(ath));
//			}
//		} else {
////			Log.log(pair + ": No Data after " + candlesticks.size() + " days!");
//		}

	}

//	@SuppressWarnings("unused")
//	private static void getChangeAndBoostFactorXMonthsBack(BinanceApiRestClient c, String pair, int monthsback) {
	// lowest price since 1 month ago
//		List<Candlestick> candlesticks = c.getCandlestickBars(pair, CandlestickInterval.MONTHLY);
//
//		// get all time high
//		double ath = 0;
//		for (Candlestick candle : candlesticks) {
//			double currentCandleHigh = Double.parseDouble(candle.getHigh());
//			if (currentCandleHigh > ath) {
//				ath = currentCandleHigh;
//			}
//		}
//		if (candlesticks.size() > monthsback) {
//
//			// get candlesticks previous months lowest prices
//			String oldPriceOneMonth = candlesticks.get(candlesticks.size() - (monthsback + 1)).getLow();
//
//			// price
//			TickerStatistics tickerstats = c.get24HrPriceStatistics(pair);
//			String currentPrice = tickerstats.getLastPrice();
//			double factor = ath / Double.parseDouble(currentPrice);
//
//			// price change last x month
//			double priceBackinXMonth = (100 * (Double.parseDouble(currentPrice) - Double.parseDouble(oldPriceOneMonth))
//					/ Double.parseDouble((oldPriceOneMonth)));
//
//			Log.log(pair + "; " + String.valueOf(df.format(priceBackinXMonth)) + "% ; " + df.format(factor));
//		}
//	}

}
