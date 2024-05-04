package binancebot.marketdata;
import org.json.JSONArray;

import com.binance.connector.client.impl.WebSocketStreamClientImpl;
import com.binance.connector.client.utils.websocketcallback.WebSocketClosedCallback;
import com.binance.connector.client.utils.websocketcallback.WebSocketClosingCallback;
import com.binance.connector.client.utils.websocketcallback.WebSocketFailureCallback;
import com.binance.connector.client.utils.websocketcallback.WebSocketMessageCallback;
import com.binance.connector.client.utils.websocketcallback.WebSocketOpenCallback;

import binancebot.FindBooster;
import binancebot.utilities.Log;
import okhttp3.Response;

public class MarketDataStream {

	// stream connector
	private WebSocketStreamClientImpl streamclient; 

	// ws callbacks
	private WebSocketOpenCallback onOpenCallback;
	private WebSocketMessageCallback onMessageCallback;
	private WebSocketClosingCallback onClosingCallback;
	private WebSocketClosedCallback onClosedCallback;
	private WebSocketFailureCallback onFailureCallback;

	// connect to web socket stream
	private void connectToStream(WebSocketStreamClientImpl client,
			WebSocketOpenCallback openCallback,
			WebSocketMessageCallback messageCallback,
			WebSocketClosingCallback closingCallback, 
			WebSocketClosedCallback closedCallback, 
			WebSocketFailureCallback failureCallback) {
		try {
			client = null;
			client = new WebSocketStreamClientImpl();
			client.allMiniTickerStream(openCallback, messageCallback, closingCallback, closedCallback, failureCallback);
		} catch (Throwable f) {
			// Log.debug("Something went wrong with streaming API Connection! " + f.toString());
		}
	}

	public MarketDataStream() {
		
		streamclient = new WebSocketStreamClientImpl();

		// web socket opening event
		onOpenCallback = openEvent -> {
			// Log.debug("WebSocket Connection OPENED ...");
		};

		// update market data on change event
		onMessageCallback = (message) -> {
			
			try {
				// log stream event message
				// Log.debug(message);

				// update symbols (BTC pairs only) via stream event
				JSONArray arr = new JSONArray(message);
				for(int i = 0; i < arr.length(); i++) {
					
					// only take BTC PAIRS FOR NOW
					String symbolName = arr.getJSONObject(i).getString("s");
					if (symbolName.endsWith("BTC")) {
						Symbol symbol = FindBooster.symbolsObj.get(arr.getJSONObject(i).getString("s"));
						if (symbol != null) {
							
							// update symbol
							String cPrice = arr.getJSONObject(i).getString("c");
							String oPrice = arr.getJSONObject(i).getString("o");
							
							// update current last price
							symbol.setLastPrice(cPrice);
							
							// update new lower lowpoint, if current price is getting lower in our watching time frame
							if(!"".equals(symbol.getLowPoint()) && Double.valueOf(cPrice) < Double.valueOf(symbol.getLowPoint())) {
								symbol.setLowPoint(cPrice);
							}
							
							// update new higher highpoint, if current price is getting higher in our watching time frame
							if(!"".equals(symbol.getHighPoint()) && Double.valueOf(cPrice) > Double.valueOf(symbol.getHighPoint())) {
								symbol.setHighPoint(cPrice);
							}
							
							// update change in %
							String priceChangePercent = String.valueOf((Double.valueOf(cPrice) - Double.valueOf(oPrice)) * 100 / Double.valueOf(oPrice));			
							symbol.setLast24hChangePercent(priceChangePercent);
							
							// debugging update process
							// Log.debug("STREAMING UPDATE: " + symbol.getName() + " Price: " + symbol.getLastPrice() + " 24h: " + symbol.getLast24hChangePercent());
							
							// update in central data store
							FindBooster.symbolsObj.replace(symbol.getName(), symbol);
							
						} else {
							// print token that we do not have in our list, wait for all symbols to be loaded from file at first!
							if (SymbolsFileReader.allSymbolsLoaded && !FindBooster.ignoredList.contains(symbolName)) {
								Log.newSymbolsIntoPairsFile(symbolName, "./pairs.properties");
							}
						}
					}
				}
			} catch (Throwable f) {
				// Log.debug(f.toString());
			}
		};

		// close socket connection event
		onClosingCallback = (int code, String reason) -> {
			// Log.debug("WebSocket Connection CLOSED ... " + "Code: " + code + " Reason: " + reason);
			
			// retry connecting to stream
			connectToStream(streamclient, onOpenCallback, onMessageCallback, onClosingCallback, onClosedCallback, onFailureCallback);
		};

		// retry on failure event
		onFailureCallback = (Throwable t, Response response) -> {
			// Log.debug("WebSocket Connection FAILED -> Trying Reconnect ... ");
			
			// retry connecting to stream
			connectToStream(streamclient, onOpenCallback, onMessageCallback, onClosingCallback, onClosedCallback, onFailureCallback);
			try {
				// we wait as long as our timeframe is to retry connecting all threads to the streaming API again
				Thread.sleep(FindBooster.watchingPeriod_seconds * 60);
			} catch(Throwable f) {}
		};

		// connect to web socket stream (aggTradeStream)
		this.connectToStream(streamclient, onOpenCallback, onMessageCallback, onClosingCallback, onClosedCallback, onFailureCallback);
		
	}

	/** stream closer */
	public void closeWebSocket() throws Exception {
		if(streamclient != null) {
			streamclient.closeAllConnections();
		}
	}

}
