package com.zigzag.markets;


import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zigzag.data.ZZPoint;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BinanceUSManager {
	 public enum BinanceExchange {
	        GLOBAL("https://api.binance.com/api/v3/klines", "Binance Global"),
	        US("https://api.binance.us/api/v3/klines", "Binance US");
	        
	        private final String baseUrl;
	        private final String displayName;
	        
	        BinanceExchange(String baseUrl, String displayName) {
	            this.baseUrl = baseUrl;
	            this.displayName = displayName;
	        }
	        
	        public String getBaseUrl() { return baseUrl; }
	        public String getDisplayName() { return displayName; }
	    }
	    
	    /**
	     * Available intervals for Binance Klines
	     */
	    public enum BinanceInterval {
	        ONE_MINUTE("1m", 1),
	        THREE_MINUTES("3m", 3),
	        FIVE_MINUTES("5m", 5),
	        FIFTEEN_MINUTES("15m", 15),
	        THIRTY_MINUTES("30m", 30),
	        ONE_HOUR("1h", 60),
	        TWO_HOURS("2h", 120),
	        FOUR_HOURS("4h", 240),
	        SIX_HOURS("6h", 360),
	        EIGHT_HOURS("8h", 480),
	        TWELVE_HOURS("12h", 720),
	        ONE_DAY("1d", 1440),
	        THREE_DAYS("3d", 4320),
	        ONE_WEEK("1w", 10080),
	        ONE_MONTH("1M", 43200);
	        
	        private final String value;
	        private final int minutes;
	        
	        BinanceInterval(String value, int minutes) {
	            this.value = value;
	            this.minutes = minutes;
	        }
	        
	        public String getValue() { return value; }
	        public int getMinutes() { return minutes; }
	    }
	    
	    private static final int MAX_LIMIT = 1000; // Binance max klines per request
	    private static final int RATE_LIMIT_MS = 100; // Conservative rate limiting
	    
	    private final OkHttpClient httpClient;
	    private final ObjectMapper objectMapper;
	    private final BinanceExchange exchange;
	    
	    public BinanceUSManager() {
	        this(BinanceExchange.US); // Default to Binance Global
	    }
	    
	    public BinanceUSManager(BinanceExchange exchange) {
	        this.httpClient = new OkHttpClient.Builder()
	                .connectTimeout(30, TimeUnit.SECONDS)
	                .readTimeout(30, TimeUnit.SECONDS)
	                .writeTimeout(30, TimeUnit.SECONDS)
	                .retryOnConnectionFailure(true)
	                .build();
	        this.objectMapper = new ObjectMapper();
	        this.exchange = exchange;
	        
	        System.out.println("Initialized " + exchange.getDisplayName() + " data fetcher (OkHttp + FasterXML)");
	        System.out.println("Endpoint: " + exchange.getBaseUrl());
	    }
	    
	    /**
	     * Fetches historical kline/candlestick data from Binance and converts to ZZPoint
	     * @param symbol Trading pair symbol (e.g., "BTCUSDT", "ETHUSDT")
	     * @param interval Kline interval
	     * @param startTime Start time in milliseconds (optional)
	     * @param endTime End time in milliseconds (optional)
	     * @param limit Number of klines to retrieve (max 1000)
	     * @return List of ZZPoint data
	     */
	    public List<ZZPoint> getKlines(String symbol, int interval, 
	                                   Long startTime, Long endTime, Integer limit) 
	            throws IOException, InterruptedException {
	        
	        StringBuilder urlBuilder = new StringBuilder(exchange.getBaseUrl());
	        urlBuilder.append("?symbol=").append(symbol.toUpperCase());
	        
	        String intervalStr = interval+"m";
	        if (interval == 60) {
	        	intervalStr = "1h";
	        }
	        urlBuilder.append("&interval=").append(intervalStr);
	        
	        if (startTime != null) {
	            urlBuilder.append("&startTime=").append(startTime);
	        }
	        if (endTime != null) {
	            urlBuilder.append("&endTime=").append(endTime);
	        }
	        if (limit != null) {
	            urlBuilder.append("&limit=").append(Math.min(limit, MAX_LIMIT));
	        }
	        
	        String url = urlBuilder.toString();
	        
	        Request request = new Request.Builder()
	                .url(url)
	                .addHeader("Accept", "application/json")
	                .addHeader("User-Agent", "BinanceHistoricalDataFetcher/2.0")
	                .get()
	                .build();
	        
	        try (Response response = httpClient.newCall(request).execute()) {
	            if (response.isSuccessful() && response.body() != null) {
	                String responseBody = response.body().string();
	                return parseKlineResponse(responseBody);
	            } else if (response.code() == 429) {
	                System.err.println("Rate limit exceeded (429). Waiting 60 seconds before retry...");
	                TimeUnit.SECONDS.sleep(60);
	                return getKlines(symbol, interval, startTime, endTime, limit); // Retry
	            } else {
	                System.err.println("HTTP Error: " + response.code() + " - " + response.message());
	                if (response.body() != null) {
	                    System.err.println("Response: " + response.body().string());
	                }
	                return new ArrayList<>();
	            }
	        } catch (IOException e) {
	            System.err.println("Network error fetching klines: " + e.getMessage());
	            throw e;
	        }
	    }
	    
	    /**
	     * Fetches historical data for a specified number of days with automatic pagination
	     * @param symbol Trading pair symbol
	     * @param interval Kline interval
	     * @param days Number of days to go back
	     * @return Complete list of ZZPoint data
	     */
	    public List<ZZPoint> getHistoricalData(String pair, int daysBack, int interval)
	            throws IOException, InterruptedException {
	        
	        List<ZZPoint> allPoints = new ArrayList<>();
	        
	        // Calculate time range
	        long endTime = Instant.now().toEpochMilli();
	        long startTime = Instant.now().minus(daysBack, ChronoUnit.DAYS).toEpochMilli();
	        
	        System.out.printf("Fetching %d days of %s data for %s (%s intervals) from %s%n", 
	        		daysBack, pair, interval, interval, exchange.getDisplayName());
	        
	        // Calculate approximate number of requests needed
	        long totalMinutes = daysBack * 24 * 60;
	        long klinesPerRequest = MAX_LIMIT;
	        long totalKlinesNeeded = totalMinutes / interval;
	        int approximateRequests = (int) Math.ceil((double) totalKlinesNeeded / klinesPerRequest);
	        
	        System.out.printf("Estimated requests needed: %d (Total data points: ~%d)%n", 
	                approximateRequests, totalKlinesNeeded);
	        
	        long currentStartTime = startTime;
	        int requestCount = 0;
	        
	        while (currentStartTime < endTime && requestCount < approximateRequests + 5) { // Safety limit
	            try {
	                List<ZZPoint> batch = getKlines(pair, interval, currentStartTime, endTime, MAX_LIMIT);
	                
	                if (batch.isEmpty()) {
	                    System.out.println("No more data available");
	                    break;
	                }
	                
	                allPoints.addAll(batch);
	                requestCount++;
	                
	                // Update start time for next batch (last point timestamp + 1 interval)
	                ZZPoint lastPoint = batch.get(batch.size() - 1);
	                currentStartTime = lastPoint.timestamp + (interval * 60 * 1000);
	                
	                System.out.printf("Request %d: Fetched %d points (Total: %d) - Latest: %s%n", 
	                        requestCount, batch.size(), allPoints.size(), 
	                        formatTimestamp(lastPoint.timestamp));
	                
	                // Rate limiting
	                TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_MS);
	                
	                // Break if we've reached the current time
	                if (lastPoint.timestamp >= endTime - (interval * 60 * 1000)) {
	                    break;
	                }
	                
	            } catch (Exception e) {
	                System.err.println("Error in batch " + requestCount + ": " + e.getMessage());
	                break;
	            }
	        }
	        
	        System.out.printf("Completed! Total ZZPoints: %d%n", allPoints.size());
	        return allPoints;
	    }
	    
	    /**
	     * Get recent data (last N klines) as ZZPoints
	     */
	    public List<ZZPoint> getRecentKlines(String symbol, int interval, int count) 
	            throws IOException, InterruptedException {
	        
	        System.out.printf("Fetching last %d %s klines for %s from %s%n", 
	                count, interval, symbol, exchange.getDisplayName());
	        return getKlines(symbol, interval, null, null, count);
	    }
	    
	    /**
	     * Get trading pairs available on the exchange
	     */
	    public List<String> getPopularTradingPairs() {
	        if (exchange == BinanceExchange.US) {
	            return List.of(
	                "BTCUSD", "BTCUSDT", "ETHUSD", "ETHUSDT", 
	                "ADAUSD", "ADAUSDT", "SOLUSD", "SOLUSDT",
	                "XRPUSD", "XRPUSDT", "LINKUSD", "LINKUSDT",
	                "LTCUSD", "LTCUSDT", "BCHUSD", "BCHUSDT"
	            );
	        } else {
	            return List.of(
	                "BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", 
	                "SOLUSDT", "XRPUSDT", "DOTUSDT", "LINKUSDT",
	                "LTCUSDT", "BCHUSDT", "AVAXUSDT", "MATICUSDT",
	                "ATOMUSDT", "NEARUSDT", "ALGOUSDT", "VETUSDT"
	            );
	        }
	    }
	    
	    /**
	     * Display exchange information and popular pairs
	     */
	    public void displayExchangeInfo() {
	        System.out.println("\n=== " + exchange.getDisplayName() + " Information ===");
	        System.out.println("Endpoint: " + exchange.getBaseUrl());
	        System.out.println("Rate Limit: 1200 requests/minute");
	        System.out.println("HTTP Client: OkHttp3 + FasterXML Jackson");
	        System.out.println("Data Structure: ZZPoint (ZigZag analysis ready)");
	        
	        List<String> pairs = getPopularTradingPairs();
	        System.out.println("Popular trading pairs: " + String.join(", ", pairs.subList(0, Math.min(8, pairs.size()))));
	        
	        if (exchange == BinanceExchange.US) {
	            System.out.println("Note: US-compliant exchange with ~150 trading pairs");
	            System.out.println("Recommended for US users requiring regulatory compliance");
	        } else {
	            System.out.println("Note: Global exchange with 1000+ trading pairs");
	            System.out.println("Higher liquidity and more trading options");
	        }
	        System.out.println();
	    }
	    
	    /**
	     * Get data for multiple symbols as ZZPoints
	     */
	    public List<SymbolData> getMultipleSymbolsData(String[] symbols, int interval, int days) 
	            throws IOException, InterruptedException {
	        
	        List<SymbolData> allData = new ArrayList<>();
	        
	        for (String symbol : symbols) {
	            try {
	                System.out.println("\nFetching data for " + symbol + "...");
	                List<ZZPoint> points = getHistoricalData(symbol, interval, days);
	                allData.add(new SymbolData(symbol, points));
	                
	                // Rate limiting between symbols
	                TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_MS * 2);
	                
	            } catch (Exception e) {
	                System.err.println("Error fetching data for " + symbol + ": " + e.getMessage());
	            }
	        }
	        
	        return allData;
	    }
	    
	    /**
	     * Calculate basic technical indicators for ZZPoints
	     */
	    public void enrichWithTechnicalIndicators(List<ZZPoint> points) {
	        if (points.size() < 14) {
	            System.out.println("Warning: Not enough data points for full technical analysis");
	            return;
	        }
	        
	        // Calculate SMA5
	        for (int i = 4; i < points.size(); i++) {
	            double sum = 0;
	            for (int j = i - 4; j <= i; j++) {
	                sum += points.get(j).close;
	            }
	            points.get(i).sma5 = sum / 5.0;
	        }
	        
	        // Calculate RSI14
	        for (int i = 14; i < points.size(); i++) {
	            double gains = 0, losses = 0;
	            for (int j = i - 13; j <= i; j++) {
	                double change = points.get(j).close - points.get(j - 1).close;
	                if (change > 0) gains += change;
	                else losses -= change;
	            }
	            double avgGain = gains / 14.0;
	            double avgLoss = losses / 14.0;
	            double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
	            points.get(i).rsi14 = 100 - (100 / (1 + rs));
	        }
	        
	        // Calculate momentum (price change over 10 periods)
	        for (int i = 10; i < points.size(); i++) {
	            points.get(i).momentum = points.get(i).close - points.get(i - 10).close;
	        }
	        
	        // Calculate volatility (standard deviation of last 20 closes)
	        for (int i = 19; i < points.size(); i++) {
	            double sum = 0, sumSquares = 0;
	            for (int j = i - 19; j <= i; j++) {
	                sum += points.get(j).close;
	                sumSquares += points.get(j).close * points.get(j).close;
	            }
	            double mean = sum / 20.0;
	            double variance = (sumSquares / 20.0) - (mean * mean);
	            points.get(i).volatility = Math.sqrt(variance);
	        }
	        
	        System.out.printf("Enriched %d ZZPoints with technical indicators%n", points.size());
	    }
	    
	    /**
	     * Cleanup resources
	     */
	    public void close() {
	        if (httpClient != null) {
	            httpClient.dispatcher().executorService().shutdown();
	            httpClient.connectionPool().evictAll();
	        }
	    }
	    
	    private List<ZZPoint> parseKlineResponse(String responseBody) {
	        List<ZZPoint> points = new ArrayList<>();
	        
	        try {
	            JsonNode rootNode = objectMapper.readTree(responseBody);
	            
	            if (rootNode.isArray()) {
	                for (JsonNode klineNode : rootNode) {
	                    if (klineNode.isArray() && klineNode.size() >= 12) {
	                        // Parse Binance kline data into ZZPoint
	                        long openTime = klineNode.get(0).asLong();
	                        double open = klineNode.get(1).asDouble();
	                        double high = klineNode.get(2).asDouble();
	                        double low = klineNode.get(3).asDouble();
	                        double close = klineNode.get(4).asDouble();
	                        double volume = klineNode.get(5).asDouble();
	                        // Note: Using openTime as timestamp, closeTime available at index 6
	                        
	                        ZZPoint point = new ZZPoint(openTime, open, high, low, close, volume, "NONE");
	                        points.add(point);
	                    }
	                }
	            }
	            
	        } catch (JsonProcessingException e) {
	            System.err.println("Error parsing kline response with FasterXML: " + e.getMessage());
	        }
	        
	        return points;
	    }
	    
	    // Data classes
	    public static class SymbolData {
	        private final String symbol;
	        private final List<ZZPoint> points;
	        
	        public SymbolData(String symbol, List<ZZPoint> points) {
	            this.symbol = symbol;
	            this.points = points;
	        }
	        
	        public String getSymbol() { return symbol; }
	        public List<ZZPoint> getPoints() { return points; }
	        
	        public void printSummary() {
	            System.out.printf("=== %s Summary ===%n", symbol);
	            System.out.printf("Total ZZPoints: %d%n", points.size());
	            
	            if (!points.isEmpty()) {
	                ZZPoint first = points.get(0);
	                ZZPoint last = points.get(points.size() - 1);
	                System.out.printf("Date range: %s to %s%n", 
	                        formatTimestamp(first.timestamp), formatTimestamp(last.timestamp));
	                System.out.printf("Price range: $%.2f to $%.2f%n", first.close, last.close);
	                
	                // Count zigzag points
	                long zigzagCount = points.stream().mapToLong(p -> p.isZigZag() ? 1 : 0).sum();
	                System.out.printf("ZigZag points: %d%n", zigzagCount);
	                
	                // Show technical indicators if available
	                boolean hasIndicators = points.stream().anyMatch(p -> p.sma5 > 0 || p.rsi14 > 0);
	                if (hasIndicators) {
	                    ZZPoint lastWithIndicators = points.stream()
	                            .filter(p -> p.sma5 > 0 && p.rsi14 > 0)
	                            .reduce((first1, second) -> second)
	                            .orElse(null);
	                    if (lastWithIndicators != null) {
	                        System.out.printf("Latest SMA5: %.2f, RSI14: %.2f, Volatility: %.2f%n",
	                                lastWithIndicators.sma5, lastWithIndicators.rsi14, lastWithIndicators.volatility);
	                    }
	                }
	            }
	        }
	    }
	    
	    // Utility method to format timestamp
	    public static String formatTimestamp(long unixMillis) {
	        //LocalDateTime dateTime = LocalDateTime.ofInstant(
	        //        Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
	        //DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	        //return dateTime.format(formatter);
	        
	    	LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(unixMillis), ZoneId.systemDefault());
	        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	        return dateTime.format(formatter);
	    }
	    
	    // Example usage
	    public static void main(String[] args) {
	        System.out.println("=== Binance ZZPoint Data Fetcher (OkHttp3 + FasterXML) ===\n");
	        
	        BinanceUSManager fetcher = null;
	        
	        try {
	            // Create fetcher
	            fetcher = new BinanceUSManager(BinanceExchange.GLOBAL);
	            fetcher.displayExchangeInfo();
	            
	            // Example 1: Get recent data as ZZPoints
	            System.out.println("=== FETCHING ZZPOINT DATA ===\n");
	            String symbol = "BTCUSDT";
	            List<ZZPoint> recentData = fetcher.getRecentKlines(symbol, 5, 50);
	            
	            System.out.printf("Fetched %d ZZPoints for %s%n", recentData.size(), symbol);
	            
	            if (!recentData.isEmpty()) {
	                System.out.println("\nFirst 3 ZZPoints:");
	                for (int i = 0; i < Math.min(3, recentData.size()); i++) {
	                    ZZPoint point = recentData.get(i);
	                    System.out.printf("ZZPoint{timestamp=%d, date=%s, OHLCV=[%.2f,%.2f,%.2f,%.2f,%.2f], type=%s}%n",
	                            point.timestamp, formatTimestamp(point.timestamp), 
	                            point.open, point.high, point.low, point.close, point.volume, point.type);
	                }
	                
	                // Enrich with technical indicators
	                System.out.println("\n=== ENRICHING WITH TECHNICAL INDICATORS ===");
	                fetcher.enrichWithTechnicalIndicators(recentData);
	                
	                // Show enriched data
	                System.out.println("\nLast 3 enriched ZZPoints:");
	                for (int i = Math.max(0, recentData.size() - 3); i < recentData.size(); i++) {
	                    ZZPoint point = recentData.get(i);
	                    System.out.printf("ZZPoint{date=%s, close=%.2f, sma5=%.2f, rsi14=%.2f, momentum=%.2f, volatility=%.2f}%n",
	                            formatTimestamp(point.timestamp), point.close, point.sma5, point.rsi14, point.momentum, point.volatility);
	                }
	            }
	            
	            System.out.println("\n=== ZZPOINT STRUCTURE BENEFITS ===");
	            System.out.println("✓ Ready for ZigZag pattern analysis");
	            System.out.println("✓ Built-in technical indicators (SMA, RSI, momentum, volatility)");
	            System.out.println("✓ Type field for marking HIGH/LOW points");
	            System.out.println("✓ Copy constructor for data manipulation");
	            System.out.println("✓ isZigZag() method for filtering significant points");
	            
	            System.out.println("\n=== REQUIRED DEPENDENCIES ===");
	            System.out.println("Maven:");
	            System.out.println("<dependency>");
	            System.out.println("    <groupId>com.squareup.okhttp3</groupId>");
	            System.out.println("    <artifactId>okhttp</artifactId>");
	            System.out.println("    <version>4.12.0</version>");
	            System.out.println("</dependency>");
	            System.out.println("<dependency>");
	            System.out.println("    <groupId>com.fasterxml.jackson.core</groupId>");
	            System.out.println("    <artifactId>jackson-databind</artifactId>");
	            System.out.println("    <version>2.17.0</version>");
	            System.out.println("</dependency>");
	            
	            System.out.println("\nGradle:");
	            System.out.println("implementation 'com.squareup.okhttp3:okhttp:4.12.0'");
	            System.out.println("implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'");
	            
	        } catch (IOException | InterruptedException e) {
	            System.err.println("Failed to fetch data: " + e.getMessage());
	        } finally {
	            // Clean up resources
	            if (fetcher != null) {
	                fetcher.close();
	            }
	        }
	    }

}
