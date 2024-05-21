package binancebot;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import binancebot.account.BinanceAccount;
import binancebot.marketdata.MarketDataRest;
import binancebot.marketdata.MarketDataStream;
import binancebot.marketdata.Symbol;
import binancebot.marketdata.SymbolsFileReader;
import binancebot.trading.TradeExecutor;
import binancebot.utilities.DF;
import binancebot.utilities.Env;
import binancebot.utilities.GlobalHelper;
import binancebot.utilities.Log;
import binancebot.utilities.Mail;

public class FindBooster {

	// symbol definition lists
	public static volatile ArrayList<String> symbolsList = new ArrayList<String>();
	public static HashMap<String, Symbol> symbolsObj = new HashMap<String, Symbol>();
	
	// ignored list for symbols we do not want to touch!
	public static volatile ArrayList<String> ignoredList = new ArrayList<String>();
	
	// start the market data stream
	public MarketDataStream marketDataStream;
	public static MarketDataRest restAPI;

	// global account for sharing invest balance
	public volatile BinanceAccount global_account = new BinanceAccount();
	public static volatile double plannedBTCInvestment;

	// search indicator settings
	public static final double boost_in_percentage = Double.valueOf(Env.getProperty("boostlevel"));
	public static final int watchingPeriod_seconds = Integer.valueOf(Env.getProperty("watching_period_in_mins")) * 60;
	public static final double maxDaily = Double.valueOf(Env.getProperty("max_daily"));
	public static final double minDaily = Double.valueOf(Env.getProperty("min_daily"));

	// trade settings
	public static final boolean liveTrading = Boolean.valueOf(Env.getProperty("liveTrading", "false"));
	public static final boolean useTestNet = Boolean.valueOf(Env.getProperty("APITestNet", "false"));
	public static final double sellingPoint = Double.valueOf(Env.getProperty("sellingPoint", "1.9"));
	public static final double stopLossPoint = Double.valueOf(Env.getProperty("stopLossPoint", "1.9"));
	public static final double TRADING_EXTRA_AT_Buy = Double.valueOf(Env.getProperty("addExtraBuyFee", "1.0028"));

	// general settings
	public static final boolean sendmails = Boolean.valueOf(Env.getProperty("sendEmails", "false"));
	public static final int limitcpu = Integer.valueOf(Env.getProperty("cpu_cooldown", "100"));

	// run binance trading bot
	public static void main(String[] args) throws Exception {
		FindBooster binanceBot = new FindBooster();
		binanceBot.run();
	}

	/** start most beautiful crypto bot ever <3 */
	public void run() throws Exception {
		
		// bot startup tasks
		startUpTasks();

		// print parameter & configuration information
		printRunInformation();

		// TODO instant trade testing
//		BinanceAccount acctest = new BinanceAccount();
//		acctest.calculateCurrentPlannedInvestInBTC();
//		TradeExecutor tradetest = new TradeExecutor(acctest, symbolsObj.get("CTXCBTC"));
//		tradetest.placeSellLimitOrder(0.00001);
		
		//		Symbol ada = symbolsObj.get("ADABTC");
		//		ada.calculateRSI();
		//	System.out.println("now: " + ada.getRSI());
		
		//	ada.calculateRSI("2024-01-28 07:30:00");
		//	System.out.println("date: " + ada.getRSI());
		//	System.out.println("...........");

		// start threads per symbol & search
		int threadcount = symbolsList.size();
		CountDownLatch lock = new CountDownLatch(threadcount);
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(threadcount);

		// compare results, save into file
		for (String symbol : symbolsList) {

			executor.schedule(() -> {

				// infinite loop inside of symbol
				while (true) {

					long timeframe = System.currentTimeMillis() + (watchingPeriod_seconds * 1000);

					try {
						// start price point - first or update to get a new snapshot set
						Symbol symbolObj = symbolsObj.get(symbol);

						// set initial lowest and highest price values by the current latest price
						String startPrice = symbolObj.getLastPrice();
						symbolObj.setLowPoint(startPrice);
						symbolObj.setHighPoint(startPrice);

						// unlist unavailable symbols where no data is sent anymore!!!
						if (startPrice.equals("0.00000000")) {
							lock.countDown();
							// Log.logAndFile( "Could not fetch market data for: " + symbol + " - terminated thread! Remaining threads: " + lock.getCount(), "./SymbolsUnlisted.log");
							Log.removeSymbolFromPairsFile(symbol, "./pairs.properties");
							break;
						}

						// watching time frame (e.g. 20 minutes)
						while (System.currentTimeMillis() <= timeframe) {
							
							// market crash prevention -> sleep all tokens for x hours when we lost x trades in a row
							if(GlobalHelper.isMarketCrashing()) {
								
								// print crash detected, first thread will disable after x hours again
								GlobalHelper.cooldownSymbolForSomeHours(symbolObj, GlobalHelper.marketCrashCoolDownHours);
								
								// reset crash prevention variables
								GlobalHelper.marketCrashHandlingReset();
							}

							// CPU load limiter instead of polling
							Thread.sleep(limitcpu);

							// price change last 24 hours
							String last24hours = symbolObj.getLast24hChangePercent();

							// price change (calculate price change for a negative boost level from highest
							// or for a positive boost level from lowest)
							double priceChange = 0.0;
							if (FindBooster.boost_in_percentage >= 0) {
								String lowPoint = symbolObj.getLowPoint();
								priceChange = (100
										* (Double.parseDouble(symbolObj.getLastPrice()) - Double.parseDouble(lowPoint))
										/ Double.parseDouble((lowPoint)));
							} else {
								String highPoint = symbolObj.getHighPoint();
								priceChange = (100
										* (Double.parseDouble(symbolObj.getLastPrice()) - Double.parseDouble(highPoint))
										/ Double.parseDouble((highPoint)));
							}

							// =====================================================================================
							// BOOSTER OR DUMPSTER - CONDITIONS EVALUATION:
							// FUNDAMENTAL CONDITIONS: token big enough, in daily range and boosted up or down
							// =====================================================================================
							if (boostConditionChecks(startPrice, priceChange, last24hours)) {

								// TRACK AND MAIL BOOSTER/DUMPSTER INFO
								printBoosterInformation(startPrice, symbolObj.getLastPrice(), priceChange, last24hours, symbol, symbolObj.calculateRSI());

								// LIVE TRADING - let's go and trade!
								if (GlobalHelper.is24hBTCDailyGood()) {
									Log.log(symbolObj.getName() + ": Min Daily BTC passed! " + GlobalHelper.btc_current_24hChange);

									// go trading if enabled
									if (liveTrading) {
										try {
											// if we had no money to trade, skip retry search again - else we traded and
											// go for cool down
											BinanceAccount account = new BinanceAccount();
											TradeExecutor trader = new TradeExecutor(account, symbolObj);
											trader.verifyAndTrade();

											// free memory - set zero after trading
											account = null;
											trader = null;
										} catch (Throwable f) {
											Log.log("TRADE FAILED: " + symbol + ": " + f.toString());
										}
									}
									break;
								} else {
									// just sleep a few hours before retries of searching in range
									Log.log(symbol + ": No Trade -> BTC Daily below: " + GlobalHelper.btc_min_DailyValue + "%. Sleeping 2 Hours!");
									GlobalHelper.cooldownSymbolForSomeHours(symbolObj, "1");
									break;
								}
							}
						}

						// frequently run keep-alive checks and investment balance calculation
						if (isLastThread(lock)) {

							// write alive.log to know bot runs still
							Log.intoFile("[[ BALANCE/PING/BTC_DAILY: " + Thread.currentThread().getName() + " ]]", "./alive.log");
							
							// restart NTP service to re-sync ...
							GlobalHelper.synchronizeNTPClientTime();

							// re-calculate planned investment every e.g. 20 mins based on btc value changes
							// and after successful trading!
							plannedBTCInvestment = global_account.calculateCurrentPlannedInvestInBTC();

							// reset market data streams
							if (marketDataStream != null) {
								marketDataStream = null;
								marketDataStream = new MarketDataStream();
							}

							// ping rest api to keep alive
							restAPI.keepAlive();
						}

					} catch (Throwable f) {
						if (f.getStackTrace().length > 0) {
							for (int i = 0; i < f.getStackTrace().length - 1; i++) {
								Log.log(f.getStackTrace()[i].toString());
							}
						}
						Log.logAndMail("THREAD DIED - CHECK LOGS: " + f.toString());
						lock.countDown();
					}
				}
			}, 300, TimeUnit.MILLISECONDS);
		}

		// it ends here if all threads somehow died finally
		lock.await();
		executor.shutdown();

		Log.logAndMail("BOT HAS COMPLETELY DIED!!! -> RESTART PI!");
		System.exit(0);
	}
	
	/** list of tasks to do on startup of bot */
	private void startUpTasks() throws Exception {

		// load market data stream
		marketDataStream = new MarketDataStream();

		// print start header
		printStartHeader();

		// clean all logs
		Log.cleanupAllLogFiles();

		// load all symbol definitions at startup
		loadSymbolDataFromFiles();

		// initially load all data for all symbols from definition list
		restAPI = new MarketDataRest(symbolsList);
		symbolsObj = restAPI.getAllSymbols24hTicker();
		restAPI.updateTickAndStepSize();

		// load btc daily from stream
		GlobalHelper.startBtc24hChangeStream();
		
		// sell off all current trades and symbols in wallet to completely reset bot!!!
		global_account.big_Selling_OFF_Everything_on_StartUp();
		
		// Calculate planned investment on startup
		plannedBTCInvestment = global_account.calculateCurrentPlannedInvestInBTC();

	}

	/** start parameter information */
	public void printRunInformation() throws Exception {
		Log.log("-----------------------------------------------");
		Log.log("Binance Account Information: ");
		Log.log("-> Total Balance BTC: " + DF.format(global_account.getTotalWalletBalanceBTC()));
		Log.log("-> Free Wallet Balance BTC: " + DF.format(global_account.getFreeAmount("BTC")));
		Log.log("-> Planned BTC Per Trade: " + DF.format(global_account.getCurrentPlannedInvestInBTC()));
		Log.log("-> Planned EUR per Trade: " + global_account.eurosPerTrade);
		Log.log("-----------------------------------------------");
		Log.log("-> Send Mails: " + sendmails);
		Log.log("-----------------------------------------------");
		Log.log("Symbol Indicator Settings:");
		Log.log("-> Boost Level: " + boost_in_percentage + " %");
		Log.log("-> Watching Timeframe: " + watchingPeriod_seconds / 60 + " Minutes");
		Log.log("-> Max Daily 24h: " + maxDaily + " %");
		Log.log("-> Min Daily 24h: " + minDaily + " %");
		Log.log("-----------------------------------------------");
		Log.log("Market Crash Prevention Settings:");
		Log.log("-> crash handling active: " + Env.getProperty("marketCrash.active"));
		Log.log("-> crash max loss in a row: " + Env.getProperty("marketCrash.maxLossInARow"));
		Log.log("-> crash cooldown in hours: " + Env.getProperty("marketCrash.cooldownInHours"));
		Log.log("-----------------------------------------------");
		Log.log("RSI Settings:");
		Log.log("-> RSI active: " + Env.getProperty("RSI.active"));
		Log.log("-> RSI interval: " + Env.getProperty("RSI.interval"));
		Log.log("-> RSI max: " + Env.getProperty("RSI.max"));
		Log.log("-> RSI min: " + Env.getProperty("RSI.min"));
		Log.log("-----------------------------------------------");
		Log.log("BTC Min Daily Settings:");
		Log.log("-> BTC Min Daily Active: " + GlobalHelper.btc_DailyValue_active);
		Log.log("-> BTC Min Daily Value: " + GlobalHelper.btc_min_DailyValue);
		Log.log("-----------------------------------------------");
		Log.log("Weekend/Skip Days Settings:");
		Log.log("-> Skip Days Active: " + GlobalHelper.skipDaysActive);
		Log.log("-> SO(1) MO(2), DI(3), MI(4), DO(5), FR(6), SA(7)");
		Log.log("-> Current Skip Days: " + GlobalHelper.skipDaysList);
		Log.log("-----------------------------------------------");
		Log.log("Trading Settings:");
		Log.log("-> Live Trading: " + liveTrading);
		Log.log("-> Take Profit: " + FindBooster.sellingPoint + " %");
		Log.log("-> Cut Loss At: " + FindBooster.stopLossPoint + " %");
		Log.log("-> Extra Fee For Buy: " + FindBooster.TRADING_EXTRA_AT_Buy);
		Log.log("-----------------------------------------------\n");
		Log.log("###############################################");
		Log.log("######## STARTED BINANCE BOT SEARCH... ########");
		Log.log("###############################################");
	}

	/** inform that a booster was found */
	public static void printBoosterInformation(String startPrice, String currentPrice, double priceChange,
			String last24hours, String symbol, double RSI) throws Exception {

		// booster found info
		String found = symbol + ";" + currentPrice + ";" + DF.format(priceChange, 2) + ";" + DF.format(Double.parseDouble(last24hours), 2) + ";" + RSI + ";" + GlobalHelper.btc_current_24hChange;

		// write booster info to file
		Log.log("###############################################");
		Log.log(symbol + ": BOOST/DUMP AT " + currentPrice + " BTC ###");
		Log.logAndFile(found, "./findings.properties");

		// send mails (if booster mails are enabled)
		if (Mail.boosterInfoMail()) {
			Mail.send(
					// subject
					StringUtils.rightPad(symbol, 9, " ") + " BOOST " + DF.format(priceChange, 2) + "%",
					// mail body
					GlobalHelper.getCurrentTimeStamp() + ": " + found + "\r\n\r\n"
							+ "https://de.tradingview.com/symbols/" + symbol + "/" + "\r\n\r\n"
							+ "https://www.binance.com/en/trade/" + symbol + "?theme=dark&type=spot");
		}

	}

	/** booster conditions evaluation */
	private boolean boostConditionChecks(String currentPrice, double priceChange, String last24hours) throws Exception {

		// A) min token size to trade (small onces jump too heavily in percentage)
		// B) between min and max daily range
		// C) booster from lowest or dumper from highest
		if (Double.parseDouble(currentPrice) > 0.00000129 && Double.parseDouble(last24hours) <= maxDaily
				&& Double.parseDouble(last24hours) >= minDaily) {
			if (boost_in_percentage < 0) {
				// search dumper downwards!
				if (priceChange <= boost_in_percentage) {
					return true;
				}
			} else {
				// search booster upwards!
				if (priceChange >= boost_in_percentage) {
					return true;
				}
			}
		}
		return false;
	}

	/** check if current thread in latch is the last thread of all in total */
	private boolean isLastThread(CountDownLatch lock) throws Exception {
		return Thread.currentThread().getName().toString().contains(String.valueOf(lock.getCount()));
	}

	/**
	 * initially load the symbol definitions as well as ignored list
	 * 
	 * @throws Exception
	 */
	private void loadSymbolDataFromFiles() throws Exception {
		
		// load from pairs file all definitions at first of known tokens
		String pairsfilepath = "./pairs.properties";
		if (new File(pairsfilepath).exists()) {
			symbolsList = SymbolsFileReader.loadPairsFromFile("./pairs.properties");
		} else {
			new Exception("pairs.properties file not found! Definition missing, aborting bot startup!");
			System.exit(0);
		}
		
		// load from ignore list
		String ignorefilepath = "./ignore.properties";
		if (new File(ignorefilepath).exists()) {
			ignoredList = SymbolsFileReader.loadPairsFromFile(ignorefilepath);
			symbolsList = (ArrayList<String>) CollectionUtils.subtract(symbolsList, ignoredList);
		}
	
		SymbolsFileReader.allSymbolsLoaded = true;
	}

	/** bot header info for startup */
	private void printStartHeader() throws Exception {
		Log.log("###############################################");
		Log.log("############## BTC-HAMSTER-BOT ################");
		Log.log("############ by Dietmar Lienhart ##############");
		Log.log("###############################################");
	}

//	/**
//	 * generate a findings report on regular basis
//	 * @param executor
//	 * @param lock
//	 * @throws Exception
//	 */
//	private static void runDailyReport(ScheduledExecutorService executor, CountDownLatch lock) throws Exception {
//		executor.schedule(() -> {
//			while (true) {
//				try {
//					AnalyseFindingsReport.generateReport();
//					
//					// sleep hours
//					Thread.sleep(1 * 1000 * 60 * 60);
//				} catch (Throwable e) {
//					e.printStackTrace();
//					lock.countDown();
//				}
//			}
//		}, 300, TimeUnit.MILLISECONDS);
//	}

}
