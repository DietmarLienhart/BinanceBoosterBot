package binancebot.account;

import binancebot.utilities.Env;

public class PrivateConfig {
	public static String API_KEY = Env.getProperty("binance_api_key");
	public static String SECRET_KEY = Env.getProperty("binance_secret_key");
	public static String TESTNET_API_KEY = "";
	public static String TESTNET_SECRET_KEY = "";
	public static String TESTNET_URL = "https://testnet.binance.vision/api";
}
