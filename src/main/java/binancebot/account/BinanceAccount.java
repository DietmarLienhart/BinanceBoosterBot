package binancebot.account;

import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.exceptions.BinanceClientException;
import com.binance.connector.client.exceptions.BinanceConnectorException;
import com.binance.connector.client.impl.SpotClientImpl;
import com.binance.connector.client.impl.spot.Wallet;

import binancebot.FindBooster;
import binancebot.marketdata.Symbol;
import binancebot.utilities.DF;
import binancebot.utilities.Env;
import binancebot.utilities.Log;

public class BinanceAccount {

	private SpotClient client;
	private Wallet wallet;
	private double currentPlannedInvestInBTCPerTrade = 0.0;
	public String eurosPerTrade = Env.getProperty("investEURPerTrade");

	public BinanceAccount() {

		while (true) {
			try {
				// connect client via api and secret key
				this.client = new SpotClientImpl(PrivateConfig.API_KEY, PrivateConfig.SECRET_KEY);
				this.wallet = client.createWallet();

				// client status fetching
				String status = client.createWallet().systemStatus();
				if (status.contains("normal")) {
					break;
				}
			} catch (BinanceConnectorException bce) {
				Log.log((String) String.format("BinanceAccount connection failed: %s", bce.getMessage()));
				reconnectWallet();
				try {
					Thread.sleep(FindBooster.watchingPeriod_seconds * 60);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/** calculates investment per thread in BTC */
	public double getCurrentPlannedInvestInBTC() throws Exception {
		return this.currentPlannedInvestInBTCPerTrade;
	}

	/** updates available BTC investment balance per trade */
	public double calculateCurrentPlannedInvestInBTC() throws Exception {
		try {
			double plannedInvest = 0.0;
			if (eurosPerTrade.contains("%")) {
				double btc_total_balance = getTotalWalletBalanceBTC();
				plannedInvest = Double.valueOf(eurosPerTrade.replace("%", "")) / 100 * btc_total_balance;
			} else {
				// fetch btc lastest price to EUR
				Map<String, Object> parameters = new LinkedHashMap<>();
				parameters.put("symbol", "BTCEUR");
				String result = client.createMarket().tickerSymbol(parameters);
				org.json.simple.JSONObject object = (org.json.simple.JSONObject) org.json.simple.JSONValue.parse(result);
				double btc_value = Double.valueOf(object.get("price").toString());

				// calculate and update BTC per trade based on amount of euros to spent
				plannedInvest = Double.valueOf(eurosPerTrade) * 1 / btc_value;
			}

			// update info if value changed after trading!
			if (this.currentPlannedInvestInBTCPerTrade != plannedInvest) {

				// dont print at startup when 0.0
//				if (this.currentPlannedInvestInBTCPerTrade != 0.0) {
//					Log.log("  -> Updated BTC Invest: " + DF.format(currentPlannedInvestInBTCPerTrade, 8));
//				}
				this.currentPlannedInvestInBTCPerTrade = plannedInvest;
			}
			return this.currentPlannedInvestInBTCPerTrade;

		} catch (BinanceConnectorException bce) {
			Log.log((String) String.format("calculateCurrentPlannedInvestInBTC failed: %s", bce.getMessage()));
			reconnectWallet();
			return calculateCurrentPlannedInvestInBTC();
		}

	}

	/**
	 * calculate all asset balances together and convert into BTC for current total
	 * wallet balance
	 */
	public double getTotalWalletBalanceBTC() {

		double totalBTCInWallet = 0;
		try {
			String result = wallet.getUserAsset(new LinkedHashMap<>());
			JSONArray arr = new JSONArray(result);
			for (int i = 0; i < arr.length(); i++) {
				JSONObject object = arr.getJSONObject(i);
				String symbol = object.getString("asset");

				// ignore crap coins!!!
				if (!symbol.equals("WABI") && !symbol.equals("BNB") && !symbol.equals("EUR")) {
					if (symbol.equals("BTC")) {
						totalBTCInWallet += Double.valueOf(object.getString("free"));
						totalBTCInWallet += Double.valueOf(object.getString("locked"));
					} else {
						totalBTCInWallet += ((Double.valueOf(object.getString("free"))
								+ Double.valueOf(object.getString("locked")))
								* Double.valueOf(FindBooster.symbolsObj.get(symbol + "BTC").getLastPrice()));
					}
				}
			}
			return totalBTCInWallet;
		} catch (BinanceConnectorException bce) {
			Log.log((String) String.format("getTotalWalletBalanceBTC failed: %s", bce.getMessage()));
			reconnectWallet();
			return getTotalWalletBalanceBTC();
		}
	}
	
	/**
	 * get all Symbols in wallet without BTC (also ignore WABI, BNB)
	 * @return all current existing symbols in wallet
	 * @throws Exception
	 */
	public JSONArray getAllSymbolsInWalletExcludingBTC() throws Exception {
		JSONArray arrFiltered = new JSONArray();
		
		// send request
		String result = wallet.getUserAsset(new LinkedHashMap<>());
		JSONArray arr = new JSONArray(result);
		
		// loop through response
		for (int i = 0; i < arr.length(); i++) {
			JSONObject symbolJSON = arr.getJSONObject(i);
			
			// filter WABI BNB BTC from list
			String symbol = symbolJSON.getString("asset");
			if (!symbol.equals("WABI") && !symbol.equals("BNB") && !symbol.equals("BTC")) {
				arrFiltered.put(symbolJSON);
			}
		}
		
		return arrFiltered;
	}

	/** returns currently locked amount within account for e.g. USDT, USDC */
	public double getLockedBalance(String targetSymbol) throws Exception {
		try {
			LinkedHashMap<String, Object> parameters = new LinkedHashMap<String, Object>();
			parameters.put("asset", targetSymbol);

			String result = client.createWallet().getUserAsset(parameters);
			JSONArray arr = new JSONArray(result);
			JSONObject object = arr.getJSONObject(0);
			return Double.valueOf(object.getString("locked"));
		} catch (BinanceConnectorException bce) {
			Log.log((String) String.format("getLockedBalance failed: %s", bce.getMessage()));
			reconnectWallet();
			return getLockedBalance(targetSymbol);
		}
	}

	public enum FeeType {
		makerCommission, takerCommission
	}

	/**
	 * get fee for given symbol
	 * 
	 * @param targetSymbol
	 * @return
	 * @throws Exception
	 */
	public double getCommissionFee(String targetSymbol, FeeType feeType) throws Exception {
		double commissionFee = 0.0;
		try {
			String result;
			JSONArray arr = null;
			JSONObject object = null;

			LinkedHashMap<String, Object> parameters = new LinkedHashMap<String, Object>();
			parameters.put("asset", targetSymbol);
			parameters.put("recvWindow", "60000");

			result = client.createWallet().tradeFee(parameters);
			try {
				arr = new JSONArray(result);
				object = arr.getJSONObject(0);
				if (arr.length() > 0) {
					commissionFee = Double.valueOf(object.getString(feeType.toString()));
				}
			} catch (Throwable f) {
				Log.log(targetSymbol + ": GET Commission Fee FAILED! + " + f.toString());
			}
			return commissionFee;

		} catch (BinanceConnectorException bce) {
			Log.log((String) String.format(targetSymbol + ": getCommissionFee failed: %s", bce.getMessage()));
			reconnectWallet();
			return getCommissionFee(targetSymbol, feeType);
		}
	}

	/**
	 * get free amount of symbol if get free amount of symbol we do not own, we get
	 * targetSymbolBTC e.g. ADABTC back "[]" as response! as such 0.0 is return
	 * value of method
	 */
	public double getFreeAmount(String targetSymbolBTC) throws Exception {

		try {
			double freeAmount = 0.0;
			String result;
			JSONArray arr = null;
			JSONObject object = null;

			LinkedHashMap<String, Object> parameters = new LinkedHashMap<String, Object>();
			parameters.put("asset", targetSymbolBTC);
			parameters.put("recvWindow", "60000");

			result = client.createWallet().getUserAsset(parameters);

			if (!result.equals("[]")) {
				try {
					arr = new JSONArray(result);
					object = arr.getJSONObject(0);
					if (arr.length() > 0) {
						freeAmount = Double.valueOf(object.getString("free"));
					}
				} catch (Throwable f) {
					Log.log("GET FREE BTC FAILED! + " + f.toString());
				}
			}
			return freeAmount;

		} catch (BinanceConnectorException bce) {
			Log.log((String) String.format(targetSymbolBTC + ": getFreeAmount API failed: %s", bce.getMessage()));
			reconnectWallet();
			return getFreeAmount(targetSymbolBTC);
		}

	}

	/** returns 0 if order was filled (gone) or 1 if order still exists */
	public String getOrderStatus(String targetSymbol) throws Exception {
		String result = "";
		JSONArray arr = null;
		try {
			Map<String, Object> parameters = new LinkedHashMap<>();
			parameters.put("symbol", targetSymbol);
			parameters.put("recvWindow", "60000");
			result = client.createTrade().getOpenOrders(parameters);
			arr = new JSONArray(result);
			if (arr.length() > 0) {
				return "1";
			} else {
				return "0";
			}
		} catch (BinanceConnectorException bce) {
			Log.log((String) String.format(targetSymbol + ": getOrderStatus failed: %s", bce.getMessage()));
			reconnectWallet();
			return getOrderStatus(targetSymbol);
		}
	}

	/** sell order for a token (must NOT exceed 5x of current value!) */
	public String placeOrder(String symbol, String amount, String price, String side, String type) throws Exception {

		// mandatory main parameters
		Map<String, Object> parameters = new LinkedHashMap<>();
		
		parameters.put("symbol", symbol);
		parameters.put("side", side); // "BUY", "SELL"
		parameters.put("type", type); // "LIMIT", "MARKET"
		parameters.put("recvWindow", "60000"); // max request window

		// limit mandatory timeInForce, quantity, price
		if (type.equals("LIMIT")) {
			parameters.put("quantity", amount);
			parameters.put("timeInForce", "GTC");
			parameters.put("price", price);
		} else {
			// market: mandatory is only the quantity/amount, rest comes from market
			parameters.put("quantity", amount);
		}

		String result = "";
		String orderId = "FAIL";
		try {
			Log.log(symbol + ": Placing " + side + " " + type + " P: " + price + " Amount: " + amount);

			// place order
			result = client.createTrade().newOrder(parameters);

			// parse order ID from placed order
			JSONParser parser = new JSONParser();
			Object p = parser.parse(result.toString());
			if (p instanceof org.json.simple.JSONArray) {
				org.json.simple.JSONArray object = (org.json.simple.JSONArray) p;
				org.json.simple.JSONObject objectitself = (org.json.simple.JSONObject) object.get(0);
				orderId = (String) objectitself.get("orderId");
			} else if (p instanceof org.json.simple.JSONObject) {
				org.json.simple.JSONObject object = (org.json.simple.JSONObject) p;
				orderId = String.valueOf(object.get("orderId"));
			}

			// log info that trade was placed successfully
			Log.log(symbol + ": Placed " + side + " " + type + " ID: " + orderId);

			parser = null;
		} catch (BinanceConnectorException bce) {
			Log.log((String) String.format(symbol + ": 	-> Order failed: %s", bce.getMessage()));
			return "FAIL";
		} catch (BinanceClientException bcle) {
			Log.log((String) String.format(symbol + ": 	-> Order failed: %s", bcle.getMessage()));
			return "FAIL";
		}

		return orderId;
	}

	/** place a buy limit order */
	public String buyLimitOrder(String symbol, String amount, String price) throws Exception {
		return placeOrder(symbol, amount, price, "BUY", "LIMIT");
	}

	/** place a buy limit order */
	public String buyMarketOrder(String symbol, String amount) throws Exception {
		return placeOrder(symbol, amount, "", "BUY", "MARKET");
	}

	/** sell order for a token (must NOT exceed 5x of current value!) */
	public String sellLimitOrder(String symbol, String amount, String price) throws Exception {
		return placeOrder(symbol, amount, price, "SELL", "LIMIT");
	}

	/** sell order for a token (must NOT exceed 5x of current value!) */
	public String sellMarketOrder(String symbol, String amount) throws Exception {
		return placeOrder(symbol, amount, "", "SELL", "MARKET");
	}

	/** cancel order by giving symbol and orderID */
	public Boolean cancelOrder(String symbol, String orderID) throws Exception {
		try {
			Map<String, Object> parameters = new LinkedHashMap<>();
			parameters.put("symbol", symbol);
			parameters.put("orderId", orderID);
			parameters.put("recvWindow", "60000");
			client.createTrade().cancelOrder(parameters);
			return true;
		} catch (BinanceConnectorException bce) {
			reconnectWallet();
			return cancelOrder(symbol, orderID);
		} catch (Throwable f) {
			Log.log(symbol + ": cancelOrder failed! ID: " + orderID + " Error: " + f.getMessage());
			return false;
		}
	}

	/** keep new rest api for account alive */
	public void keepAlive() throws Exception {
		try {
			client.createMarket().ping();
		} catch (Throwable f) {
			Log.log("keepAlive api call failed!");
			reconnectWallet();
			keepAlive();
		}
	}

	/** disconnect rest client and set account to null */
	public void disconnectWallet() {
		if (this.client != null) {
			this.client = null;
		}
		if (this.wallet != null) {
			this.wallet = null;
		}
	}

	/**
	 * connect or re-connect to wallet
	 */
	public void reconnectWallet() {

		// disconnect at first if required
		disconnectWallet();

		// re-connect spot client and wallet
		while (true) {
			try {
				this.client = new SpotClientImpl(PrivateConfig.API_KEY, PrivateConfig.SECRET_KEY);
				this.wallet = client.createWallet();

				// client status fetching - exit if connected
				String status = wallet.systemStatus();
				if (status.contains("normal")) {
					Log.log("Successfully re-connected to Binance Wallet ...");
					return;
				}
			} catch (Throwable f) {
				try {
					// retry every 30 seconds
					Thread.sleep(30 * 1000);
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}
	}

	/** returns all open order ids */
	public JSONArray getAllOpenOrderIDs() throws Exception {
		String result = "";
		JSONArray jsonArray = null;
		try {
			Map<String, Object> parameters = new LinkedHashMap<>();
			parameters.put("recvWindow", "60000");
			result = client.createTrade().getOpenOrders(parameters);
			jsonArray = new JSONArray(result);
			return jsonArray;
		} catch (BinanceConnectorException bce) {
			reconnectWallet();
			return getAllOpenOrderIDs();
		}
	}

	/**
	 * cancels all open trades and sells everything!!! ONLY WITH LIVE TRADING ENABLED - STILL BE CAREFUL!!!
	 * @throws Exception
	 */
	public void big_Selling_OFF_Everything_on_StartUp() throws Exception {
		Log.log("-----------------------------------------------");
		if(Boolean.valueOf(Env.getProperty("resetTradesOnStartup","false")) && Boolean.valueOf(Env.getProperty("liveTrading"))) {
			Log.log("RESET WALLET: SELL ALL OPEN ORDERS:");
			marketSellALLOpenTrades();
			Log.log("-----------------------------------------------");
			Log.log("RESET WALLET: SELL ALL SYMBOLS IN WALLET:");
			marketSellAllSymbolsInWallet();
		} else {
			Log.log("RESET WALLET: No selling off at startup!");
		}
	}

	/** instantly sell off all pending trades via market sell to reset bot */
	public void marketSellALLOpenTrades() throws Exception {

		// cancel all open orders
		JSONArray arr = this.getAllOpenOrderIDs();
		for (int i = 0; i <= arr.length() - 1; i++) {
			org.json.simple.JSONObject currentTrade = (org.json.simple.JSONObject) org.json.simple.JSONValue.parse(arr.get(i).toString());
			String symbol = currentTrade.get("symbol").toString();
			String tradeID = currentTrade.get("orderId").toString();

			// cancel existing trade
			this.cancelOrder(symbol, tradeID);

			// market sell total amount
			Symbol symbolInfo = FindBooster.symbolsObj.get(symbol);
			double freeAmount = this.getFreeAmount(symbolInfo.getNameShort());
			String amount = DF.format(freeAmount, symbolInfo.getStepSize(), RoundingMode.DOWN);
			this.placeOrder(symbol, amount, "", "SELL", "MARKET");
		}
	}

	/**
	 * 
	 * @throws Exception
	 */
	public void marketSellAllSymbolsInWallet() throws Exception {

		// get all symbols from wallet (exclude WABI, BNB, BTC)
		JSONArray arr = this.getAllSymbolsInWalletExcludingBTC();
			
		for (int i = 0; i < arr.length(); i++) {
			JSONObject object = arr.getJSONObject(i);
			String symbol = object.getString("asset");
			
			// market sell total amount
			Symbol symbolInfo = FindBooster.symbolsObj.get(symbol + "BTC");
			double freeAmount = this.getFreeAmount(symbolInfo.getNameShort());
			String amount = DF.format(freeAmount, symbolInfo.getStepSize(), RoundingMode.DOWN);
			this.placeOrder(symbol + "BTC", amount, "", "SELL", "MARKET");
		}

	}
	
}
