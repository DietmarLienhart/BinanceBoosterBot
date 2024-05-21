package binancebot.marketdata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import com.binance.connector.client.impl.SpotClientImpl;

import binancebot.FindBooster;
import binancebot.account.PrivateConfig;
import binancebot.utilities.Log;

public class MarketDataRest {

	private SpotClientImpl client;
	private ArrayList<String> symbols = new ArrayList<String>();

	public MarketDataRest(ArrayList<String> allSymbols) {
		this.symbols = allSymbols;
		client = new SpotClientImpl(PrivateConfig.API_KEY, PrivateConfig.SECRET_KEY);
	}
	
	/** get all symbols data in json array */
	public HashMap<String, Symbol> getSingleSymbols24hTicker(String targetSymbol) throws Exception {

		// fetch data for all in constructor given symbols
		LinkedHashMap<String, Object> symbolParam = new LinkedHashMap<>();
		symbolParam.put("symbols", new ArrayList<String>(Arrays.asList(targetSymbol)));
		String result = client.createMarket().ticker24H(symbolParam);
		JSONArray arr = new JSONArray(result);

		// convert to symbol and add to an array list
		HashMap<String, Symbol> list = new HashMap<String, Symbol>();
		for (int i = 0; i < arr.length(); i++) {
			JSONObject object = arr.getJSONObject(i);
			String name = object.getString("symbol");
			Symbol symbol = new Symbol(name);
			
			// initial values per symbol
			symbol.setLastPrice(object.getString("lastPrice"));
			symbol.setLast24hChangePercent(object.getString("priceChangePercent"));
			
			list.put(name, symbol);
		}

		return list;
	}

	/** get all symbols data in json array */
	public HashMap<String, Symbol> getAllSymbols24hTicker() throws Exception {

		// fetch data for all in constructor given symbols
		LinkedHashMap<String, Object> symbolParam = new LinkedHashMap<>();
		symbolParam.put("symbols", this.symbols);
		
		String result = client.createMarket().ticker24H(symbolParam);
		JSONArray arr = new JSONArray(result);

		// convert to symbol and add to an array list
		HashMap<String, Symbol> list = new HashMap<String, Symbol>();
		for (int i = 0; i < arr.length(); i++) {
			JSONObject object = arr.getJSONObject(i);
			String name = object.getString("symbol");
			Symbol symbol = new Symbol(name);
			
			// initial values per symbol
			symbol.setLastPrice(object.getString("lastPrice"));
			symbol.setLast24hChangePercent(object.getString("priceChangePercent"));
			
			list.put(name, symbol);
		}
		
		Log.log("Loaded market data for " + list.size() + " symbols ...");

		return list;
	}

	/** get all symbols data in json array */
	public void updateTickAndStepSize() throws Exception {

		// fetch data for all in constructor given symbols
		LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
		parameters.put("symbols", this.symbols);
		String result = client.createMarket().exchangeInfo(parameters);
		JSONObject mainJson = new JSONObject(result);
		JSONArray symbols = mainJson.getJSONArray("symbols");

		// update decimal places for all symbols
		for (int n = 0; n < symbols.length(); n++) {
			JSONObject object = symbols.getJSONObject(n);
			String name = String.valueOf(object.get("symbol"));

			// update tick size for all symbols
			JSONObject filters = (JSONObject) object.getJSONArray("filters").get(0);
			int tickSize = String.valueOf(filters.get("tickSize")).indexOf("1") - 1;
			FindBooster.symbolsObj.get(name).setPlacesAfterComma(tickSize);
			if (tickSize == -1) {
				tickSize = 0;
			}

			// update step size for all symbols (required for amount!)
			JSONObject filters02 = (JSONObject) object.getJSONArray("filters").get(1);
			int stepSize = (String.valueOf(filters02.get("stepSize")).indexOf("1") - 1);
			if (stepSize == -1) {
				stepSize = 0;
			}
			FindBooster.symbolsObj.get(name).setStepSize(stepSize);
		}
		
		Log.log("Loaded ticksize and stepsize for " + this.symbols.size() + " symbols ...");

	}

	/** keep session alive - use all 9 minutes once! */
	public void keepAlive() throws Exception {
		try {
			client.createMarket().ping();
		} catch(Throwable f) {
			Log.log("EXCEPTION: we lost connection to market data rest api client! Try re-connection!");
			client = new SpotClientImpl(PrivateConfig.API_KEY, PrivateConfig.SECRET_KEY);
			Log.log("Successfully re-connected RestApiClient!");
		}
	}

}
