package examples.spot.margin;

import java.util.LinkedHashMap;
import java.util.Map;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.SpotClientImpl;

import examples.PrivateConfig;

public final class CrossMarginData {
    private CrossMarginData() {
    }

    public static void main(String[] args) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("coin", "BNB");

        SpotClient client = new SpotClientImpl(PrivateConfig.API_KEY, PrivateConfig.SECRET_KEY);
        String result = client.createMargin().crossMarginData(parameters);
        System.out.println(result);
    }
}
