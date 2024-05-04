package binancebot.trading;

import java.math.RoundingMode;

import binancebot.FindBooster;
import binancebot.account.BinanceAccount;
import binancebot.marketdata.Symbol;
import binancebot.utilities.DF;
import binancebot.utilities.Env;
import binancebot.utilities.GlobalHelper;
import binancebot.utilities.Log;
import binancebot.utilities.Mail;

public class TradeExecutor {

	// buy order variables
	private String target_amount = "";
	private double target_BuyPrice = 0;

	// sell order variables
	private double target_SellPrice = 0;
	private double target_stopLossPrice = 0;

	private Symbol symbolObj;
	private BinanceAccount account;

	public TradeExecutor(BinanceAccount account, Symbol symbol) {
		this.account = account;
		this.symbolObj = symbol;
	}

	/**
	 * start to execute a trading strategy
	 * 
	 * @return true if trade was done otherwise false
	 */
	public void verifyAndTrade() throws Exception {

		// pre-calculate RSI - if enabled (default: true)
		boolean RSICheckOk = true;
		if (Boolean.valueOf(Env.getProperty("RSI.active", "false")).equals(true)) {
			RSICheckOk = this.symbolObj.verifyRSI();
		}

		// 1.) RSI CHECK
		if (RSICheckOk) {
			// 2.) FREE BTC CHECK
			double freeBTC = account.getFreeAmount("BTC");
			double invest_btc = FindBooster.plannedBTCInvestment;

			// as a good principle ... we do nothing on weekends, just keep api alive!(FR-SA-SUN)
			if (GlobalHelper.isASkipDay()) {
				Log.log(this.symbolObj.getName() + ": NO DEAL! IT'S WEEKEND BABY!");
			} else {
				if(!GlobalHelper.isMarketCrashing()) {
					// any other day we deal
					if (invest_btc > 0 && invest_btc <= freeBTC) {
						executeTrades(invest_btc);
					} else {
						Log.log(this.symbolObj.getName() + ": MISSED! NOT ENOUGH BTC! PRICE: " + this.symbolObj.getLastPrice());
					}
					
					// cooldown after trade (regardless if we had the money and invested or not!)
					GlobalHelper.cooldownSymbolForSomeHours(this.symbolObj);
				} else {
					// market crash cooldown has a different hours settings
					
				}
			}

		} else {
			// no cooldown after RSI check failed (we can be long above RSI and after couple
			// of hits go finally trade in RSI range, as such better!!! :-)
		}

	}

	/** buy and hold an asset until your price level is reached */
	public boolean executeTrades(double btc) throws Exception {

		String symbolName = this.symbolObj.getName();
		Log.log("===============================================");
		Log.log(symbolName + ": GOING TO INVEST " + DF.format(btc) + " BTC!!");
		Log.log("===============================================");

		try {

			// PLACE BUY ORDERS (LIMIT OR MARKET ORDERS)
			boolean buySuccess = false;
			if (Boolean.valueOf(Env.getProperty("useMarketOrders"))) {
				buySuccess = placeBuyMarketOrder(btc);
			} else {
				buySuccess = placeBuyLimitOrder(btc);
			}

			// PLACE SELL ORDERS (LIMIT OR MARKET ORDERS)
			if (buySuccess) {

				// place and wait for sell order to reach price target (infinite loop)
				boolean sellSuccess = false;

				// SELL MARKET OR LIMIT ORDER
				if (Boolean.valueOf(Env.getProperty("useMarketOrders"))) {
					sellSuccess = placeSellMarketOrder();
				} else {
					sellSuccess = placeSellLimitOrder(this.target_BuyPrice);
				}

				// re-calculate possible btc balance after sell trades got executed
				FindBooster.plannedBTCInvestment = account.calculateCurrentPlannedInvestInBTC();

				// outcome info mail
				double sellPercentWithFees = FindBooster.sellingPoint * 0.9976;
				double stopPercentWithFees = FindBooster.stopLossPoint * FindBooster.TRADING_EXTRA_AT_Buy;

				// win or loss handling
				if (sellSuccess) {
					GlobalHelper.marketCrashHandling(true);
					
					Mail.send(symbolName + " : WIN: " + FindBooster.sellingPoint + "%");
					Log.logAndFile(
							"WIN;" + symbolName + ";" + DF.doubleToString(this.target_BuyPrice, this.symbolObj) + ";"
									+ DF.doubleToString(this.target_SellPrice, this.symbolObj) + ";"
									+ sellPercentWithFees + ";" + DF.format(btc * ((sellPercentWithFees / 100) + 1), 2),
							"./result.log");
				} else {

					// loss handling if market crashes
					GlobalHelper.marketCrashHandling(false);

					Mail.send(symbolName + " LOSS: " + FindBooster.stopLossPoint + "%");
					Log.logAndFile(
							"LOSS;" + symbolName + ";" + DF.doubleToString(this.target_BuyPrice, this.symbolObj) + ";"
									+ DF.doubleToString(this.target_stopLossPrice, this.symbolObj) + ";"
									+ stopPercentWithFees + ";" + DF.format(btc * ((stopPercentWithFees / 100) - 1), 2),
							"./result.log");
				}
			}

		} catch (Throwable f) {
			Log.logAndMail(symbolName + ": TRADES TECHNICALLY FAILED!", f.toString());
			if (f.getStackTrace().length > 0) {
				for (int i = 0; i < f.getStackTrace().length - 1; i++) {
					Log.log(f.getStackTrace()[i].toString());
				}
			}
		}
		return true;

	}

	/** place a BUY order with the given amount of BTC */
	public boolean placeBuyLimitOrder(double btc) throws Exception {

		String buyOrderID = "";
		String symbolName = this.symbolObj.getName();

		// current token price
		double lastPrice = Double.valueOf(this.symbolObj.getLastPrice());

		// add x % for a target price to catch the buy via limit buy for sure
		this.target_BuyPrice = Double.valueOf(
				DF.format((lastPrice * FindBooster.TRADING_EXTRA_AT_Buy), this.symbolObj.getPlacesAfterComma()));

		// calculate amount of symbols to buy
		this.target_amount = DF.format((btc / this.target_BuyPrice), this.symbolObj.getStepSize());

		// log buy order info for debugging
//		Log.log("################################################");
//		Log.log(this.symbolObj.getName() + " LIMIT BUY ORDER SETTINGS:");
//		Log.log("btc planned invest:	" + DF.format(btc));
//		Log.log("target amount:		" + amount);
//		Log.log("last price:		" + DF.format(lastPrice));
//		Log.log("target buy price:		" + DF.format(this.target_BuyPrice));
//		Log.log("StepSize:			" + this.symbolObj.getStepSize());
//		Log.log("PlacesAfterComma:		" + this.symbolObj.getPlacesAfterComma());
//		Log.log("################################################");

		// place buy order
		buyOrderID = account.buyLimitOrder(symbolName, this.target_amount, DF.format(this.target_BuyPrice));

		// wait for BUY order to get filled and amount in wallet! (60 sec max) - cancel
		// if not bought in time
		long timeout = System.currentTimeMillis() + (60 * 1000);
		Log.log(symbolName + ": Buy Limit Order - WAITING for filling...");
		while (account.getOrderStatus(symbolName).equals("1") && account.getFreeAmount(symbolName) == 0.0) {

			// exit: we did NOT make the buy deal!
			if (System.currentTimeMillis() > timeout) {

				Log.log(symbolName + ": Order " + buyOrderID + " not filled after 60 sec!");

				// cancel pending order
				account.cancelOrder(symbolName, buyOrderID);

				// send order mail
				if (Mail.cancelOrderMails()) {
					Mail.send("MISSED BUY ORDER - CANCELLED: " + symbolName,
							" Price: " + DF.doubleToString(this.target_BuyPrice, this.symbolObj) + " Amount: "
									+ this.target_amount);
				}
				return false;
			}

			Thread.sleep(500);

		}

		// send buy limit order info
		Log.log(symbolName + ": Buy Limit Order FILLED! P: " + DF.doubleToString(this.target_BuyPrice, this.symbolObj)
				+ " Amount: " + this.target_amount);

		// send order mail
		if (Mail.buyOrderMails()) {
			Mail.send("BUY LIMIT ORDER FILLED: " + symbolName, " Price: "
					+ DF.doubleToString(this.target_BuyPrice, this.symbolObj) + " Amount: " + this.target_amount);
		}

		return true;

	}

	/** place a BUY order with the given amount of BTC */
	public boolean placeBuyMarketOrder(double btc) throws Exception {

		String symbolName = this.symbolObj.getName();

		this.target_BuyPrice = Double.valueOf(
				DF.format((Double.valueOf(this.symbolObj.getLastPrice())), this.symbolObj.getPlacesAfterComma()));

		String amount = DF.format(btc / this.target_BuyPrice, this.symbolObj.getStepSize(), RoundingMode.DOWN);

		// log market buy order info
		Log.log("##########################");
		Log.log(this.symbolObj.getName() + " MARKET BUY ORDER SETTINGS:");
		Log.log("btc planned invest: " + DF.format(btc));
		Log.log("target amount: " + amount);
		Log.log("target buy price: " + DF.format(this.target_BuyPrice));
		Log.log("StepSize:" + this.symbolObj.getStepSize());
		Log.log("PlacesAfterComma:" + this.symbolObj.getPlacesAfterComma());

		// place buy order
		account.buyMarketOrder(symbolName, amount);

		// wait for buying to be complete
		while (account.getOrderStatus(symbolName).equals("1")) {
			Thread.sleep(500);
			account.keepAlive();
		}

		// send info mail
		if (Mail.buyOrderMails()) {
			Mail.send("BUY MARKET ORDER FILLED: " + symbolName, " Price: "
					+ DF.doubleToString(this.target_BuyPrice, this.symbolObj) + " Amount: " + this.target_amount);
		}

		return true;

	}

	/**
	 * place a sell order with for given symbol based on buying price and amount
	 * 
	 * @return sold with win or loss
	 */
	public boolean placeSellLimitOrder(double buyingPrice) throws Exception {

		String sellOrderID = "";
		String symbolName = this.symbolObj.getName();

		// calculate available sell amount from wallet (ideally we already know from
		// buying order, not if bot was restarted ...)
		// this.target_amount =
		// DF.format(account.getFreeAmount(symbolObj.getNameShort()),
		// this.symbolObj.getStepSize(), RoundingMode.DOWN);

		// buy, sell and stop loss target prices
		this.target_SellPrice = buyingPrice * (1 + (FindBooster.sellingPoint / 100));
		this.target_stopLossPrice = (buyingPrice * (1 - (FindBooster.stopLossPoint / 100)));

		// log info
//		Log.log("-----------------------------------------------");
//		Log.log(symbolName + " SELL LIMIT ORDER SETTINGS:");
//		Log.log("amount: " + this.target_amount);
//		Log.log("target sell price: " + DF.doubleToString(this.target_SellPrice));
//		Log.log("-----------------------------------------------");

		// place set sell order (we know what have bought before, but API randomly comes
		// back with less amount than ordered ... (commission fee...)
		sellOrderID = account.sellLimitOrder(symbolName, this.target_amount,
				DF.symbolsDouble2Str(this.symbolObj, this.target_SellPrice));

		// retry (binance api commission bug, randomly happens...by
		// purpose/randomly...!?!)
		if (sellOrderID.equals("FAIL")) {

			double freeAmount = 0.0;
			while (freeAmount == 0.0) {
				freeAmount = account.getFreeAmount(symbolObj.getNameShort());
				this.target_amount = DF.format(freeAmount, this.symbolObj.getStepSize(), RoundingMode.DOWN);
			}

			Log.log(this.symbolObj.getName() + ": Re-Placing limit sell order | amount: " + this.target_amount
					+ " | free: " + freeAmount);
			sellOrderID = account.sellLimitOrder(symbolName, this.target_amount,
					DF.symbolsDouble2Str(this.symbolObj, this.target_SellPrice));

		}

		// placed sell trade - info mail
		if (Mail.sellOrderMails()) {
			Mail.send("SELL LIMIT ORDER SET: " + symbolName,
					" Buy Price: " + DF.format(this.target_BuyPrice) + "\r\n" + " Stop Price: "
							+ DF.format(this.target_stopLossPrice) + "\r\n" + " Target Price: "
							+ DF.format(this.target_SellPrice) + "\r\n" + " Amount: " + this.target_amount);
		}

		// WAIT FOR SELL TO FILL with 2 strategies: stop loss or if 0 => buy and hold
		// ...
		if (FindBooster.stopLossPoint == 0) {

			// A) wait for filling the sell order - buy and hold forever ... high risk!!!
			Log.log(symbolName + ": BUY_AND_HOLD: Wait for ever for Sell Limit Order to fill!");

			// loop here until we get the sell order filled
			while (account.getOrderStatus(symbolName).equals("1")) {
				Thread.sleep(10000);
				account.keepAlive();
			}
		} else {

			// B) while waiting for sell order to get filled - sell at max x % loss
			Log.log(symbolName + ": Wait for limit sell order with stop loss: " + FindBooster.stopLossPoint + "%.");

			// wait for sell price, but with stop loss
			while (account.getOrderStatus(symbolName).equals("1")) {

				// WAIT FOR FILL WITH STOP LOSS EXECUTION IN CASE
				if (Double.valueOf(this.symbolObj.getLastPrice()) <= this.target_stopLossPrice) {

					// cancel current limit sell
					account.cancelOrder(symbolName, sellOrderID);

					// instantly cut off loss at 1% with market sell order
					account.sellMarketOrder(symbolName, this.target_amount);

					Log.log(this.symbolObj.getName() + ": STOPLOSS: CUT VIA MARKET SELL!");
					Log.log("-----------------------------------------------");
					return false;
				}

				// keep threads api connection alive and wait a bit
				account.keepAlive();
				Thread.sleep(10000);
			}
		}

		Log.log(symbolName + ": Sell Limit Order successfully filled!");
		Log.log("-----------------------------------------------");
		return true;
	}

	/**
	 * place a sell order with for given symbol based on buying price and amount
	 * 
	 * @return successfully sold or with stop-loss
	 */
	public boolean placeSellMarketOrder() throws Exception {

		Double currentLastPrice = Double.valueOf(this.symbolObj.getLastPrice());
		String symbolName = this.symbolObj.getName();

		// buy-based the NEW target sell and stop loss prices
		this.target_SellPrice = currentLastPrice * (1 + (FindBooster.sellingPoint / 100));
		this.target_stopLossPrice = (currentLastPrice * (1 - (FindBooster.stopLossPoint / 100)));

		// log sell info
		Log.log("#######################");
		Log.log(symbolName + " MARKET SELL ORDER SETTINGS:");
		Log.log("amount: " + String.valueOf(this.target_amount));
		Log.log("target sell price: " + DF.doubleToString(this.target_SellPrice, this.symbolObj));
		Log.log("#######################");

		// wait for execution of market sell order - until target price is reached
		if (Double.valueOf(symbolObj.getLastPrice()) >= this.target_SellPrice) {

			// instant market sell order
			String sellOrderID = account.sellMarketOrder(symbolName, this.target_amount);

			// retry (binance api commission bug, randomly happens...by
			// purpose/randomly...!?!)
			if (sellOrderID.equals("FAIL")) {

				// update existing amount and round
				double freeAmount = account.getFreeAmount(symbolObj.getNameShort());
				this.target_amount = DF.format(freeAmount, this.symbolObj.getStepSize(), RoundingMode.DOWN);

				// re-place market sell order again with "correct" amount
				Log.log(this.symbolObj.getName() + ": Re-Placing market sell order | found amount: "
						+ this.target_amount + " | free: " + freeAmount);
				sellOrderID = account.sellMarketOrder(symbolName, this.target_amount);
			}

			// wait for market sell order filling
			while (account.getOrderStatus(symbolName).equals("1")) {
				Thread.sleep(5000);
				account.keepAlive();
			}

			// info mail
			if (Mail.sellOrderMails()) {
				Mail.send("SELL LIMIT ORDER PLACED SUCCESSFULLY");
			}

			return true;
		}
		return false;

	}

}
