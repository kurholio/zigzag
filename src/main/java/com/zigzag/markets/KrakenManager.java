package com.zigzag.markets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zigzag.data.ZZPoint;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class KrakenManager {
	
	public static List<ZZPoint> fetchOHLC(String pair, int interval) throws IOException {
        String url = "https://api.kraken.com/0/public/OHLC?pair=" + pair + "&interval=" + interval;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body().string());
        JsonNode result = root.path("result");

        String dynamicKey = result.fieldNames().next(); // e.g., "XXBTZUSD"
        JsonNode ohlcArray = result.path(dynamicKey);

        List<ZZPoint> bars = new ArrayList<>();
        for (int i = 0; i < ohlcArray.size(); i++) {
            JsonNode candle = ohlcArray.get(i);
            long timestamp = candle.get(0).asLong();
            double open = candle.get(1).asDouble();
            double high = candle.get(2).asDouble();
            double low = candle.get(3).asDouble();
            double close = candle.get(4).asDouble();
            double volume = candle.get(6).asDouble();
            bars.add(new ZZPoint(timestamp, open, high, low, close, volume, null));
        }
        return bars;
    }

}
