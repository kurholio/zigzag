package com.zigzag.markets;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zigzag.data.ZZPoint;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class KrakenManager {
	private static final int RATE_LIMIT_MS = 1000; // 1 second between requests
	
	
	
	public static Map<String, BigDecimal> latestPrices = new ConcurrentHashMap<>();
	private static WebSocketClient wsClient;
	
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
	
	 public static String formatUnixTimestamp(long unixSeconds) {
        Instant instant = Instant.ofEpochSecond(unixSeconds);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTime.format(formatter);
    }
	
	public static List<ZZPoint> getHistoricalData(String pair, int daysBack, int interval)  {
        List<ZZPoint> bars = new ArrayList<>();
        
        // Calculate timestamp for X days ago
        OkHttpClient client = new OkHttpClient();
        long since = Instant.now().minus(daysBack, ChronoUnit.DAYS).getEpochSecond();
        long currentTime = Instant.now().getEpochSecond();
        
        
       
        System.out.println("Fetching " + daysBack + " days of historical data for " + pair);
        //System.out.println("Current time: "+currentTimeStr+" : "+currentTime);
      
        
        int requestCount = 0;
        
        while (since < currentTime) {
        	String sinceStr = formatUnixTimestamp(since);
        	System.out.println("Since time: "+sinceStr+" : "+since);
            String url = "https://api.kraken.com/0/public/OHLC?pair=" + pair + "&interval=" + interval+"&since="+since;
            Request request = new Request.Builder().url(url).build();
            try {
	            Response response = client.newCall(request).execute();
	            
	            
	            ObjectMapper mapper = new ObjectMapper();
	            JsonNode root = mapper.readTree(response.body().string());
	            JsonNode result = root.path("result");
	            
	            
	
	            String dynamicKey = result.fieldNames().next(); // e.g., "XXBTZUSD"
	            JsonNode ohlcArray = result.path(dynamicKey);
           
            	List<ZZPoint> thisBars = new ArrayList<>();
            	for (int i = 0; i < ohlcArray.size(); i++) {
                    JsonNode candle = ohlcArray.get(i);
                    long timestamp = candle.get(0).asLong();
                    double open = candle.get(1).asDouble();
                    double high = candle.get(2).asDouble();
                    double low = candle.get(3).asDouble();
                    double close = candle.get(4).asDouble();
                    double volume = candle.get(6).asDouble();
                    thisBars.add(new ZZPoint(timestamp, open, high, low, close, volume, null));
                }
            	
            	if (thisBars.size()<3) {
            		break;
            	} else {
            		since = thisBars.get(thisBars.size()-1).timestamp + 1;
            	}
            			
            	//JsonNode lastNode = result.get("last");
	           
	            //if (lastNode != null) {
	            //	long sinceLong = lastNode.asLong();
	            //	since = sinceLong+interval;
	            //}
	            
            	bars.addAll(thisBars);
                requestCount++;
                System.out.printf("Request %d: Fetched %d data points (Total: %d)%n", 
                                requestCount, thisBars.size(), bars.size());
                
              
                // Rate limiting - wait 1 second between requests
                TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_MS);
                
            } catch (Exception e) {
            	e.printStackTrace();
                System.err.println("Error fetching data: " + e.getMessage());
                return bars;
            }
        }
        
        System.out.println("Completed! Total data points: " + bars.size());
        return bars;
    }
	
	public static void connectWebSocketTicker(List<String> krakenPairs) {
    	
		//CurrencyPair currencyPair = CurrencyPairDeserializer.getCurrencyPairFromString(krakenPair);
        try {
        	
        	
        	
        	if (wsClient != null) {
				if ( wsClient.isClosed() || wsClient.isClosing() || wsClient.isFlushAndClose()) {
					wsClient.close();
					latestPrices.clear();
				}
        	}

			

			// Create WebSocket client for Kraken ticker
        	String symbol = krakenPairs.toString();
            wsClient = new WebSocketClient(new URI("wss://ws.kraken.com")) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("WebSocket Opened. Subscribing...");
                    String subscribeMessage = String.format(
                        "{\"event\":\"subscribe\", \"pair\":[\"%s\"], \"subscription\":{\"name\":\"ticker\"}}",
                        symbol
                    );
                    send(subscribeMessage);
                }

                @Override
                public void onMessage(String message) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode json = mapper.readTree(message);

                        if (json.isArray() && json.size() > 1 && json.get(1).has("c")) {
                            // "c" = last trade closed [ price, lot volume ]
                            String pair = json.get(json.size() - 1).asText();
                            BigDecimal price = new BigDecimal(json.get(1).get("c").get(0).asText());
                            
                            latestPrices.put(pair, price);
                            System.out.println("Price update for " + pair + ": " + price);
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing message: " + message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("WebSocket closed: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };

            wsClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public static BigDecimal getLatestPrice(String krakenPair) {
    	
    	if (krakenPair.equals("BTCUSD")) {
    		krakenPair = "XBT/USD";
    	}
        return latestPrices.get(krakenPair);
    }

}
