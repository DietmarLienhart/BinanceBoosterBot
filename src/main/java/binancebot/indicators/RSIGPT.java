package binancebot.indicators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.binance.connector.client.impl.SpotClientImpl;

import binancebot.account.PrivateConfig;
import binancebot.marketdata.Symbol;
import binancebot.utilities.Env;
import binancebot.utilities.Log;

public class RSIGPT {

	private final String RSI_INTERVAL = Env.getProperty("RSI.interval");

	private final int RSI_PERIOD = Integer.valueOf(Env.getProperty("RSI.periods","14"));

	/** get RSI via smoothing */
	public double getRSIChatGPT(Symbol symbol) throws Exception {

		double RSI = 0.0;

		// load data via connecting spot client API implementation
		LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
		SpotClientImpl client = new SpotClientImpl(PrivateConfig.API_KEY, PrivateConfig.SECRET_KEY);
		parameters.put("symbol", symbol.getName());
		parameters.put("interval", RSI_INTERVAL);
		parameters.put("limit", "1000");

		// fetch data points
		String result = client.createMarket().klines(parameters);

		// parse result string
		result = result.replace("]]", "").replace("[[", "");
		String[] candles = result.split("\\],\\[");

		// 12 = hard coded values from API call Binance which we receive
		List<Double> closingPrices = new ArrayList<Double>();

		// cut off the last current candle which has no closing and is as "null, null,  null"
		for (int i = 0; i < candles.length - 1; i++) {
			closingPrices.add(Double.valueOf(candles[i].split(",")[4].replace("\"", "")));
		}

		// set current last price of symbol
		closingPrices.add(Double.valueOf(symbol.getLastPrice()));

		// CALCULATE RSI with smoothing
		RSI = Math.ceil(calculateRSI_interpolate_and_smooth(closingPrices, 0.1));

		// info line
		Log.log(symbol.getName() + " - RSI Indicator \"" + RSI_INTERVAL + "\": " + String.valueOf(RSI));
		
		// round UP to be on safer side!
		return RSI;
		
	}

	// get latest RSI with smoothing
	@SuppressWarnings("unused")
	private Double calculateRSI_smoothing(List<Double> closingPrices, double smoothingFactor) throws Exception {

		List<Double> rsiValues = new ArrayList<>();

		double gainSum = 0;
		double lossSum = 0;

		// Calculate the initial gain and loss sums
		for (int i = 1; i < RSI_PERIOD; i++) {
			double priceDiff = closingPrices.get(i) - closingPrices.get(i - 1);
			if (priceDiff >= 0) {
				gainSum += priceDiff;
			} else {
				lossSum += -priceDiff;
			}
		}

		// Calculate the first RSI value
		double avgGain = gainSum / RSI_PERIOD;
		double avgLoss = lossSum / RSI_PERIOD;
		double rs = avgGain / avgLoss;
		double rsi = 100 - (100 / (1 + rs));
		rsiValues.add(rsi);

		// Calculate the rest of the RSI values
		for (int i = RSI_PERIOD; i < closingPrices.size(); i++) {
			double priceDiff = closingPrices.get(i) - closingPrices.get(i - 1);
			double gain = 0;
			double loss = 0;
			if (priceDiff >= 0) {
				gain = priceDiff;
			} else {
				loss = -priceDiff;
			}
			gainSum = ((RSI_PERIOD - 1) * gainSum + gain) / RSI_PERIOD;
			lossSum = ((RSI_PERIOD - 1) * lossSum + loss) / RSI_PERIOD;
			avgGain = gainSum / RSI_PERIOD;
			avgLoss = lossSum / RSI_PERIOD;
			rs = avgGain / avgLoss;
			rsi = 100 - (100 / (1 + rs));

			// Apply the smoothing factor
			double smoothedRSI = (rsi * smoothingFactor) + (rsiValues.get(i - RSI_PERIOD) * (1 - smoothingFactor));
			rsiValues.add(smoothedRSI);

		}

		// fetch latest RSI
		Double RSI = rsiValues.get(rsiValues.size() - 1);

		return RSI;

	}

	private Double calculateRSI_interpolate_and_smooth(List<Double> closingPrices, double smoothingFactor) {
		List<Double> rsiValues = new ArrayList<>();

		// Interpolate missing data
		for (int i = 1; i < closingPrices.size(); i++) {
			if (closingPrices.get(i) == 0) {
				closingPrices.set(i, closingPrices.get(i - 1));
			}
		}

		double gainSum = 0;
		double lossSum = 0;

		// Calculate the initial gain and loss sums
		for (int i = 1; i < RSI_PERIOD; i++) {
			double priceDiff = closingPrices.get(i) - closingPrices.get(i - 1);
			if (priceDiff >= 0) {
				gainSum += priceDiff;
			} else {
				lossSum += -priceDiff;
			}
		}

		// Calculate the first RSI value
		double avgGain = gainSum / RSI_PERIOD;
		double avgLoss = lossSum / RSI_PERIOD;
		double rs = avgGain / avgLoss;
		double rsi = 100 - (100 / (1 + rs));
		rsiValues.add(rsi);

		// Calculate the rest of the RSI values
		for (int i = RSI_PERIOD; i < closingPrices.size(); i++) {
			double priceDiff = closingPrices.get(i) - closingPrices.get(i - 1);
			double gain = 0;
			double loss = 0;
			if (priceDiff >= 0) {
				gain = priceDiff;
			} else {
				loss = -priceDiff;
			}
			gainSum = ((RSI_PERIOD - 1) * gainSum + gain) / RSI_PERIOD;
			lossSum = ((RSI_PERIOD - 1) * lossSum + loss) / RSI_PERIOD;
			avgGain = gainSum / RSI_PERIOD;
			avgLoss = lossSum / RSI_PERIOD;
			rs = avgGain / avgLoss;
			rsi = 100 - (100 / (1 + rs));

		}

		// fetch latest RSI
		Double RSI = rsiValues.get(rsiValues.size() - 1);
		return RSI;
	}

}