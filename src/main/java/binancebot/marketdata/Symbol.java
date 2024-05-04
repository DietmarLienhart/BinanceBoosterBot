package binancebot.marketdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import com.binance.connector.client.impl.SpotClientImpl;

import binancebot.account.PrivateConfig;
import binancebot.indicators.RSI;
import binancebot.utilities.Env;
import binancebot.utilities.Log;

public class Symbol {

	private String name = "";
	private String lastPrice = "";
	private String lowPoint = "";
	private String highPoint = "";
	private String last24hChangePercent = "";
	private int placesAfterComma = 0;
	private int stepSize = 0;
	private double RSI = 0.0;

	public Symbol(String name) {
		this.name = name;
	}
	
	public void setLowPoint(String lower) {
		this.lowPoint = lower;
	}
	
	public String getLowPoint() {
		return this.lowPoint;
	}
	
	public void setHighPoint(String higher) {
		this.highPoint = higher;
	}
	
	public String getHighPoint() {
		return this.highPoint;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public String getNameShort() {
		return this.name.replace("BTC", "");
	}

	public void setLastPrice(String price) {
		this.lastPrice = price;
	}

	public String getLastPrice() {
		return this.lastPrice;
	}
	
	public int getPlacesAfterComma() {
		return this.placesAfterComma;
	}
	
	public void setPlacesAfterComma(int placesAfterComma) {
		this.placesAfterComma = placesAfterComma;
	}
	
	public int getStepSize() {
		return this.stepSize;
	}
	
	public void setStepSize(int setStepSize) {
		this.stepSize = setStepSize;
	}

	public void setLast24hChangePercent(String change) {
		this.last24hChangePercent = change;
	}

	public String getLast24hChangePercent() {
		return this.last24hChangePercent;
	}

	/** returns candle sticks as 2 dimensional array */
	public List<Double> getClosingPrices(String datapoints) {

		// connect spot client API implementation
		LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
		SpotClientImpl client = new SpotClientImpl(PrivateConfig.API_KEY, PrivateConfig.SECRET_KEY);
		parameters.put("symbol", this.name);
		parameters.put("limit", datapoints);
		parameters.put("interval", Env.getProperty("RSI.interval"));

		// fetch data points
		String result = client.createMarket().klines(parameters);

		// parse result string
		result = result.replace("]]", "").replace("[[", "");
		String[] candles = result.split("\\],\\[");

		// 12 = hard coded values from API call binance which we receive
		List<Double> closingPrices = new ArrayList<Double>();

		// cut off the last current candle which has no closing and is as "null, null, null"
		for (int i = 0; i < candles.length - 1; i++) {
			closingPrices.add(Double.valueOf(candles[i].split(",")[4].replace("\"", "")));
		}

		// set current last price of symbol
		closingPrices.add(Double.valueOf(this.lastPrice));

		// disconnect rest api client
		client = null;

		return closingPrices;
	}

	/** calculate latest RSI for symbol */
	public double calculateRSI() throws Exception {
		RSI rsi = new RSI();
		this.RSI = rsi.calculateRSI(this);
		return this.RSI;
	}
	
	/** calculate latest RSI for symbol 
	 * @timestamp UTC timestamp in format yyyy-MM-dd HH:mm:ss 
	 * */
	public double calculateRSI(String timestamp) throws Exception {
		RSI rsi = new RSI();
		this.RSI = rsi.calculateRSI(this, timestamp);
		return this.RSI;
	}
	
	/**
	 * returns a complete hashmap with all rsi intervals for this symbol (15m,1h,4h,1d,1w,1m)
	 * @param symbol
	 * @return
	 * @throws Exception
	 */
	public HashMap<String, Double> calculateRSI_ALL() throws Exception {
		RSI rsi = new RSI();
		return rsi.calculateRSIAllIntervals(this);
	}

	/** get last calculated RSI */
	public double getRSI() throws Exception {
		return this.RSI;
	}
	
	/** perform all enabled indicator verifications */
	public boolean verifyRSI() throws Exception {
		
		boolean rsiCheck = false;

		// RSI range
		Double max_rsi = Double.valueOf(Env.getProperty("RSI.max"));
		Double min_rsi = Double.valueOf(Env.getProperty("RSI.min"));
	
		// RSI check
		rsiCheck = (this.RSI >= min_rsi && this.RSI <= max_rsi) ? true : false;
		if(rsiCheck) {
			Log.log(this.getName() + ": RSI passed! -> " + this.RSI);
		} else {
			Log.log(this.getName() + ": RSI failed! -> " + this.RSI);
		}
		
		return rsiCheck;

	}
	
}
