package binancebot.indicators;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import com.binance.connector.client.impl.SpotClientImpl;

import binancebot.account.PrivateConfig;
import binancebot.marketdata.Symbol;
import binancebot.utilities.Env;

public class RSI {

	public final String RSI_INTERVAL = Env.getProperty("RSI.interval");
	public final String RSI_DATA_POINTS = Env.getProperty("RSI.datapoints", "1000");
	public final int RSI_PERIOD = Integer.valueOf(Env.getProperty("RSI.periods", "14"));
	private double RSI = 0.0;

	/** get RSI via smoothing */
	public double calculateRSI(Symbol symbol) throws Exception {
		return calculateRSI(symbol, RSI_INTERVAL, RSI_DATA_POINTS);
	}
	
	/** get RSI via smoothing for defined timestamp */
	public double calculateRSI(Symbol symbol, String timestamp) throws Exception {
		return calculateRSI(symbol, RSI_INTERVAL, RSI_DATA_POINTS, timestamp);
	}
	
	/**
	 * calculate all possible RSI variants and return them
	 * @param symbol
	 * @return ArrayList<String> allRSI
	 * @throws Exception
	 */
	public HashMap<String, Double> calculateRSIAllIntervals(Symbol symbol) throws Exception {
		HashMap<String, Double> allRSI = new HashMap<String, Double>();
		
		allRSI.put("15m", calculateRSI(symbol, "15m", "1000"));
		allRSI.put("1h", calculateRSI(symbol, "1h", "1000"));
		allRSI.put("4h", calculateRSI(symbol, "4h", "1000"));
		allRSI.put("1d", calculateRSI(symbol, "1d", "1000"));
		allRSI.put("1w", calculateRSI(symbol, "1w", "1000"));
		allRSI.put("1M", calculateRSI(symbol, "1M", "1000"));
		
		return allRSI;
	}
	
	/** get RSI via smoothing */
	public double calculateRSI(Symbol symbol, String rsi_interval, String rsi_datapoints) throws Exception {
		return calculateRSI(symbol, rsi_interval, rsi_datapoints, null);
	}
	
	/** get RSI via smoothing 
	 * @startTime has to be given in UTC time always, format to be yyyy-MM-dd HH:mm:ss
	 * */
	public double calculateRSI(Symbol symbol, String rsi_interval, String rsi_datapoints, String startTime) throws Exception {
		
		// Create a builder that will be used to build the BarSeries
		BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder();

		// Build the BarSeries from the candles
		List<Bar> bars = new ArrayList<Bar>();

		// load data via connecting spot client API implementation
		LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
		SpotClientImpl client = new SpotClientImpl(PrivateConfig.API_KEY, PrivateConfig.SECRET_KEY);
		parameters.put("symbol", symbol.getName());
		parameters.put("interval", rsi_interval);
		parameters.put("limit", rsi_datapoints);
		
		// always to be given in UTC
		if(startTime != null) {
			parameters.put("startTime", convertDateToLong(startTime));
		}

		// fetch data points
		String result = client.createMarket().klines(parameters);

		// parse result string
//	    1499040000000,      // Kline open time 0
//	    "0.01634790",       // Open price 1 
//	    "0.80000000",       // High price 2 
//	    "0.01575800",       // Low price 3
//	    "0.01577100",       // Close price 4
//	    "148976.11427815",  // Volume 5
//	    1499644799999,      // Kline Close time 6
//	    "2434.19055334",    // Quote asset volume
//	    308,                // Number of trades
//	    "1756.87402397",    // Taker buy base asset volume
//	    "28.46694368",      // Taker buy quote asset volume
//	    "0"                 // Unused field, ignore.
		result = result.replace("]]", "").replace("[[", "");
		String[] candles = result.split("\\],\\[");

		// cut off the last current candle which has no closing and is as "null, null, null"
		for (int i = 0; i < candles.length - 1; i++) {
			double openPrice = Double.valueOf(candles[i].split(",")[1].replace("\"", ""));
			double highPrice = Double.valueOf(candles[i].split(",")[2].replace("\"", ""));
			double lowPrice = Double.valueOf(candles[i].split(",")[3].replace("\"", ""));
			double closePrice = Double.valueOf(candles[i].split(",")[4].replace("\"", ""));
			double volume = Double.valueOf(candles[i].split(",")[5].replace("\"", ""));
			Instant instant = Instant.ofEpochMilli(Long.valueOf(candles[i].split(",")[6].replace("\"", "")));
			double amount = Double.valueOf(candles[i].split(",")[8].replace("\"", ""));

			ZonedDateTime timestamp = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
			BaseBar bar = new BaseBar(Duration.ofMinutes(1), timestamp, openPrice, highPrice, lowPrice, closePrice,
					volume, amount);
			bars.add(bar);

		}

		builder.withBars(bars);

		BarSeries series = builder.build();

		// Create a ClosePriceIndicator
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

		// Create an RSIIndicator with a time frame of e.g.14 bars
		RSIIndicator rsi = new RSIIndicator(closePrice, RSI_PERIOD);

//		// Print all the RSI values
//		for (int i = 0; i < series.getBarCount(); i++) {
//			System.out.println("RSI at " + series.getBar(i).getEndTime() + ": " + rsi.getValue(i));
//		}

		// Get the last RSI value
		RSI = Math.ceil(rsi.getValue(series.getEndIndex()).doubleValue());

		// log info line for RSI
		// Log.log(symbol.getName() + "-RSI-Calculated: \"" + RSI_INTERVAL + "\": " + String.valueOf(RSI));

		return RSI;
	}
	
	/** convert a given timestamp (yyyy-MM-dd HH:mm:ss) into long */
	public long convertDateToLong(String timestamp) throws Exception {
	    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	    Date date = sdf.parse(timestamp);
	    String specifiedDateString = sdf.format(date);
	    long timestampAsLong = sdf.parse(specifiedDateString).getTime();
	    return timestampAsLong;
	}

}