package examples.websocketstream;

import com.binance.connector.client.WebSocketStreamClient;
import com.binance.connector.client.impl.WebSocketStreamClientImpl;

public final class SymbolTicker {
    
	private SymbolTicker() {
    
    }

    public static void main(String[] args) {
        WebSocketStreamClient client = new WebSocketStreamClientImpl();
        client.symbolTicker("btceur", ((event) -> {
            org.json.simple.JSONObject object = (org.json.simple.JSONObject) org.json.simple.JSONValue.parse(event);
            System.out.println(object.get("P"));
//            client.closeAllConnections();
        }));
        System.out.println("test");
    }
}
