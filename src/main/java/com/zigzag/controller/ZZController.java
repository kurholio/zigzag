package com.zigzag.controller;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.kraken.dto.trade.KrakenOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zigzag.data.ZZCalculator;
import com.zigzag.data.ZZPoint;
import com.zigzag.data.ZZTradePrediction;
import com.zigzag.markets.BinanceUSManager;
import com.zigzag.markets.KrakenManager;
import com.zigzag.predictor.OpenAIPredictor;
import com.zigzag.services.KrakenService;



@RequestMapping("/api")
@RestController
public class ZZController {


     @Autowired
     private KrakenService krakenService;
     
     
	 
    @GetMapping("/hello")
    public String hello() {
        return "Hello from backend!";
    }
    
    @GetMapping("/catalog")
    public String catalog() {
        return "Hello from catalogsssssssssssssssssssssssssssssssssssssss!";
    }
    
    @GetMapping("/balance/all")
    public ResponseEntity<Map<String, BigDecimal>> getBalances() {
        return ResponseEntity.ok(krakenService.getBalances());
    }

    @GetMapping("/balance/base/{symbol}")
    public ResponseEntity<BigDecimal> getBalancel(@PathVariable String symbol) {
        return ResponseEntity.ok(krakenService.getBalance(symbol));
    }

    @GetMapping("/trades/all")
    public ResponseEntity<List<Trade>> getTrades() {
        return ResponseEntity.ok(krakenService.getTrades());
    }

    @GetMapping("/trades/pair/{pair}")
    public ResponseEntity<List<Trade>> getTrades(@PathVariable String pair) {
        return ResponseEntity.ok(krakenService.getTrades(pair));
    }
    @GetMapping("/orders/all")
    public ResponseEntity<Map<String, KrakenOrder>> getOrders() {
        return ResponseEntity.ok(krakenService.getOrders());
    }
    

    @GetMapping("/orders/pair/{pair}")
    public ResponseEntity<List<KrakenOrder>> getOrders(@PathVariable String pair) {
        return ResponseEntity.ok(krakenService.getOrders(pair));
    }
    
    ///api/zigzag/XBTUSD?interval=60&leftBars=5&leftBars=5&percentChange=3
    @GetMapping("/zigzag/zz/{pair}")
    public ResponseEntity<List<com.zigzag.data.ZZPoint>> zigzagZZ(
            @PathVariable String pair,
            @RequestParam(defaultValue = "60") Integer interval,
            @RequestParam(defaultValue = "5") Integer leftBars,
            @RequestParam(defaultValue = "5") Integer rightBars,
            @RequestParam(defaultValue = "3") Integer percentChange,
            @RequestParam(defaultValue = "30") Integer daysBack) {

        try {
        	List<ZZPoint> bars = new BinanceUSManager().getHistoricalData(pair, daysBack,interval);
            List<ZZPoint> zigzag = ZZCalculator.calculateZigZag(bars, leftBars, rightBars, percentChange);
            zigzag = ZZCalculator.generateFeatureEnrichedZZPoints(bars, zigzag,true);
            System.out.println("ZigZag Pivot Points:");
            zigzag.forEach(System.out::println);

            return ResponseEntity.ok(zigzag); // ✅ Automatically serialized as JSON
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Collections.emptyList()); // or return a structured error if needed
        }
    }
    
    @GetMapping("/zigzag/all/{pair}")
    public ResponseEntity<List<ZZPoint>> zigzagAll(
            @PathVariable String pair,
            @RequestParam(defaultValue = "60") Integer interval,
            @RequestParam(defaultValue = "5") Integer leftBars,
            @RequestParam(defaultValue = "5") Integer rightBars,
            @RequestParam(defaultValue = "3") Integer percentChange,
            @RequestParam(defaultValue = "30") Integer daysBack) {

        try {
            List<ZZPoint> bars = new BinanceUSManager().getHistoricalData(pair, daysBack,interval);
            List<ZZPoint> zigzag = ZZCalculator.calculateZigZag(bars, leftBars, rightBars, percentChange);
            zigzag = ZZCalculator.generateFeatureEnrichedZZPoints(bars, zigzag, false);
            System.out.println("ZigZag Pivot Points: "+zigzag.size());
            //zigzag.forEach(System.out::println);

            return ResponseEntity.ok(zigzag); // ✅ Automatically serialized as JSON
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Collections.emptyList()); // or return a structured error if needed
        }
    }
    
    @GetMapping("/zigzag/predict/{pair}")
    public ResponseEntity<ZZTradePrediction> zigzagPredict(
            @PathVariable String pair,
            @RequestParam(defaultValue = "60") Integer interval,
            @RequestParam(defaultValue = "5") Integer leftBars,
            @RequestParam(defaultValue = "5") Integer rightBars,
            @RequestParam(defaultValue = "3") Integer percentChange,
            @RequestParam(defaultValue = "30") Integer daysBack) {

        try {
        	List<ZZPoint> bars = new BinanceUSManager().getHistoricalData(pair, daysBack,interval);
            List<ZZPoint> zigzag = ZZCalculator.calculateZigZag(bars, leftBars, rightBars, percentChange);
            
            zigzag = ZZCalculator.generateFeatureEnrichedZZPoints(bars, zigzag,false);
            
            ZZTradePrediction prediction =  OpenAIPredictor.getPredictionFromGPT(zigzag);
            
            System.out.println("ZigZag Pivot Points:");
            zigzag.forEach(System.out::println);

            return ResponseEntity.ok(prediction); // ✅ Automatically serialized as JSON
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ZZTradePrediction()); // or return a structured error if needed
        }
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
}