package com.zigzag.data;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.kraken.dto.trade.KrakenOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zigzag.data.ZZCalculator;
import com.zigzag.data.ZZPoint;
import com.zigzag.data.ZZSummary;
import com.zigzag.data.ZZTradePrediction;
import com.zigzag.data.ZZWallet;
import com.zigzag.markets.BinanceUSManager;
import com.zigzag.markets.KrakenManager;
import com.zigzag.predictor.OpenAIPredictor;
import com.zigzag.services.AllExchangeServices;
import com.zigzag.services.ExchangeService;
import com.zigzag.services.KrakenService;

public class ZZTradingPair {


     int cycleCount = 0;
     
     
      
     ScheduledExecutorService scheduler;
     List<ZZPoint> allbars = new ArrayList<>();
     List<ZZPoint> zigzag = new ArrayList<>();
     ZZTradePrediction prediction = new ZZTradePrediction();
     double balance  = 0;
     List<LimitOrder> orders=new ArrayList<>();
     List<Trade> trades=new ArrayList<>();
     
    boolean running = false;
    boolean scheduled = false;
    ZZSummary summary = new ZZSummary();
    double latestPrice = 0;
    
    
 
    public ZZTradePrediction prediction() {
    	
    	return prediction;
    }
    

    public String status() {
    	try {
    		if (scheduled) {
    			if (running) {
        			return "running";
        		} else {
        			return "scheduled";
        		}
    		} else {
    			return "stopped";
    		}
    		
        	
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: "+e.getMessage(); // or return a structured error if needed
        }
    }
    
    public int cycleCounter() {
    	return cycleCount;
    }
    

    public String stop() {
    	try {
    		stopScheduledRun();
        	return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: "+e.getMessage(); 
        }
    }
  
    

    public ZZSummary getSummaryHeader() {
    	//ZZSummary s = new ZZSummary();
    	summary.setFrom(prediction, latestPrice);
        return summary;
    }

    public Double getBalance() {
        return balance;
    }

    public List<Trade> getTrades() {
        return trades;
    }

    public List<LimitOrder> getOrders() {
        return orders;
    }
    

    public List<ZZPoint> getZigZags() {
        return zigzag;
    }
 
    public List<ZZPoint> getAllBars() {
          return allbars; // ✅ Automatically serialized as JSON
    }
    
    public String startBot(
    		String base,
            String counter,
            Integer interval,
            Integer leftBars,
            Integer rightBars,
            Integer percentChange,
            Integer daysBack) {
    	
    	summary.base = base;
    	summary.counter = counter;
    	summary.interval = interval;
    	summary.leftBars = leftBars;
    	summary.rightBars = rightBars;
    	summary.percentChange = percentChange;
    	summary.daysBack = daysBack;
        try {
        	scheduled = true;
        	startScheduledRun(base, counter, interval, leftBars, rightBars, percentChange, daysBack);
        	return "{}";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: "+e.getMessage(); // or return a structured error if needed
        }
    }
    
    
    

    public void run(String base,
    		String counter,
    		Integer interval,
            Integer leftBars, 
            Integer rightBars, 
            Integer percentChange, 
            Integer daysBack) {

    	String pair = base+counter;
        try {
            running = true;
            cycleCount++;
            
            //krakenService.connectWebSocketTicker(pair);
            
            List<ZZPoint> bars = new BinanceUSManager().getHistoricalData(pair, daysBack,interval);
            zigzag = ZZCalculator.calculateZigZag(bars, leftBars, rightBars, percentChange);
     
            allbars = ZZCalculator.generateFeatureEnrichedZZPoints(bars, zigzag,false);
            prediction =  OpenAIPredictor.getPredictionFromGPT(zigzag);
            //System.out.println("ZigZag Pivot Points:");
            tryTrades(pair);
            
            Thread.sleep(2000);
            orders = AllExchangeServices.getInstance().getOrders(pair);
            trades = AllExchangeServices.getInstance().getTrades(pair);
            updateZZPointsWithTradesAndOrders();
            running = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	private void tryTrades(String pair) {
		//zigzag.forEach(System.out::println);

		if (KrakenManager.latestPrices.size() > 0) {
		    BigDecimal currentPrice = KrakenManager.getLatestPrice(pair);
		    if (prediction.isBuyNow(currentPrice)) {
		        AllExchangeServices.getInstance().placeBuyLimitOrderWithStopLoss(pair, new BigDecimal(getTradeAmount()), 
		                new BigDecimal(currentPrice.doubleValue()*0.975), true);

		    }
		    if (prediction.isSellNow(currentPrice)) {
		    	AllExchangeServices.getInstance().placeSellLimitOrderWithStopCancel(pair, new BigDecimal(getTradeAmount()), 
		                new BigDecimal(currentPrice.doubleValue()*1.025), true);
		    }
		}
	}
    
    public ZZPoint getClosestZZPoint(Trade trade) {
        if (allbars == null || allbars.isEmpty() || trade == null || trade.getTimestamp() == null) {
            return null;
        }
        ZZPoint closest = null;
        long minDiff = Long.MAX_VALUE;
        long tradeTime = trade.getTimestamp().getTime();
        for (ZZPoint point : allbars) {
            long pointTime = point.getDate().getTime();
            if (tradeTime >= pointTime) {
                long diff = tradeTime - pointTime;
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = point;
                }
                // Prefer exact match
                if (pointTime == tradeTime) {
                    return point;
                }
            }
        }
        return closest;
    }
    
    public ZZPoint getClosestZZPoint(LimitOrder order) {
        if (allbars == null || allbars.isEmpty() || order == null || order.getTimestamp() == null) {
            return null;
        }
        ZZPoint closest = null;
        long minDiff = Long.MAX_VALUE;
        long orderTime = (long) order.getTimestamp().getTime();
        for (ZZPoint point : allbars) {
            long pointTime = point.getDate().getTime();
            long diff = Math.abs(pointTime - orderTime);
            if (diff < minDiff) {
                minDiff = diff;
                closest = point;
            }
            // Prefer exact match
            if (pointTime == orderTime) {
                return point;
            }
        }
        return closest;
    }
    
    public void updateZZPointsWithTradesAndOrders() {
        // For each trade, find the closest ZZPoint by date and attach trade info
        if (allbars == null || allbars.isEmpty()) return;
        if (trades != null) {
            for (Trade trade : trades) {
                ZZPoint closest = getClosestZZPoint(trade);
                if (closest != null) { 
                	closest.setTrade(trade); // You need to add setTrade(Trade) to ZZPoint
                	closest.saleTrade = trade.getType() == OrderType.ASK;
                	closest.buyTrade = trade.getType() == OrderType.BID;
                } else {
                	System.out.println("Trade note found: "+trade.getTimestamp());
                }
            }
        }
        // For each order, find the closest ZZPoint by date and attach order info
        if (orders != null) {
            for (LimitOrder order : orders) {
                ZZPoint closest = getClosestZZPoint(order);
                
                if (closest != null) {
                    closest.setOrder(order); // You need to add setOrder(KrakenOrder) to ZZPoint
                }
            }
        }
    }

    // Triggers the run method every 2 minutes for the given parameters
    public void startScheduledRun(String base, 
    							  String counter,
                                  Integer interval,
                                  Integer leftBars,
                                  Integer rightBars,
                                  Integer percentChange,
                                  Integer daysBack) {
    	
        stopScheduledRun(); // Stop any existing scheduler before starting a new one
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
            () -> run(base, counter, interval, leftBars, rightBars, percentChange, daysBack),
            0, 30, TimeUnit.MINUTES
        );
    }

    // Stops the scheduled run if running
    public void stopScheduledRun() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
            scheduled = false;
        }
    }

    // Resets all class variables to their initial state
    public void resetAll() {
        allbars = new ArrayList<>();
        zigzag = new ArrayList<>();
        prediction = null;
        balance = 0;
        orders = null;
        trades = null;
    }
    
    double getTradeAmount() {
    	return balance/5;
    }
    
    @GetMapping("/zigzag")
    public String zigzag() {
    	
    	try {
            String pair = "XBTUSD";  // Kraken trading pair
            int interval = 60;       // 60 = hourly candles
            int leftBars = 5;
            int rightBars = 5;
            double minPercentChange = 3;

            List<ZZPoint> bars = KrakenManager.fetchOHLC(pair, interval);
            List<ZZPoint> zigzag = ZZCalculator.calculateZigZag(bars, leftBars, rightBars, minPercentChange);

            System.out.println("ZigZag Pivot Points:");
            zigzag.forEach(System.out::println); 
            return zigzag.toString();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "{\"Error\" :\"Not Found\"}";
    }
    
    @GetMapping("/markets")
    public String markets() {
    	
    	try {
            String pair = "XBTUSD";  // Kraken trading pair
            int interval = 60;       // 60 = hourly candles
            int leftBars = 5;
            int rightBars = 5;
            double minPercentChange = 3;

            List<ZZPoint> bars = KrakenManager.fetchOHLC(pair, interval);
            List<ZZPoint> zigzag = ZZCalculator.calculateZigZag(bars, leftBars, rightBars, minPercentChange);

            System.out.println("ZigZag Pivot Points:");
            zigzag.forEach(System.out::println); 
            return zigzag.toString();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "{\"Error\" :\"Not Found\"}";
    }
}