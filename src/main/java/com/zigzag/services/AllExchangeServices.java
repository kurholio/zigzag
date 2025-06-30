package com.zigzag.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kraken.service.KrakenTradeHistoryParams;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.utils.jackson.CurrencyPairDeserializer;

import com.zigzag.data.ZZWallet;


public class AllExchangeServices {
	private static AllExchangeServices instance;
	Exchange exchange = null;
	AccountService accountService = null;
    MarketDataService marketDataService = null;
    TradeService tradeService =null;
	

    private AllExchangeServices() {
        // private constructor to prevent instantiation
    }

    public static synchronized AllExchangeServices getInstance() {
        if (instance == null) {
            instance = new AllExchangeServices();
        }
        return instance;
    }
	
	 public List<ZZWallet> fetchWalletData(String exchangeClass, String apiKey, String secretKey) throws Exception {
		 	exchange = ExchangeFactory.INSTANCE.createExchange(exchangeClass);
		    exchange.getExchangeSpecification().setApiKey(apiKey);
		    exchange.getExchangeSpecification().setSecretKey(secretKey);
		    exchange.applySpecification(exchange.getExchangeSpecification());

		    accountService = exchange.getAccountService();
		    marketDataService = exchange.getMarketDataService();
		    tradeService = exchange.getTradeService();
		    Map<Instrument, InstrumentMetaData> instruments = exchange.getExchangeMetaData().getInstruments();

		    Wallet wallet = null;
		    if (exchangeClass.contains("Kraken")) {
		    	wallet = accountService.getAccountInfo().getWallets().values().iterator().next();
		    } else {
		    	wallet = accountService.getAccountInfo().getWallet();
		    }
		    Map<Currency, Balance> balances = wallet.getBalances();

		    List<ZZWallet> results = new ArrayList<>();

		    for (Map.Entry<Currency, Balance> entry : balances.entrySet()) {
		        Currency currency = entry.getKey();
		        Balance balance = entry.getValue();

		        if (balance.getTotal().compareTo(BigDecimal.ZERO) <= 0) continue;

		        BigDecimal amount = balance.getAvailable(); // or getTotal() if you want full

		        BigDecimal usdValue = BigDecimal.ZERO;
		        if (Currency.USD.equals(currency)) {
		        	usdValue = amount;
		        }
		        List<String> tradablePairs = new ArrayList<>();

		        for (Instrument instrument : instruments.keySet()) {
		            if (instrument instanceof CurrencyPair) {
		                CurrencyPair pair = (CurrencyPair) instrument;

		                if (pair.base.equals(currency) && pair.counter.equals(Currency.USD)) {
		                    tradablePairs.add(pair.toString());

		                    if (pair.counter.getCurrencyCode().equalsIgnoreCase("USD")) {
		                        try {
		                            Ticker ticker = marketDataService.getTicker(pair);
		                            if (ticker != null && ticker.getLast() != null) {
		                                usdValue = ticker.getLast().multiply(amount);
		                            }
		                        } catch (Exception ignored) {}
		                    } 
		                }
		            }
		        }
		        
		        if (usdValue.doubleValue() >1) {
			        ZZWallet wb = new ZZWallet();
			        wb.setCurrency(currency.getCurrencyCode());
			        wb.setBalance(amount);
			        wb.setUsdValue(usdValue);
			        wb.setTradablePairs(tradablePairs);
			        results.add(wb);
		        }
		    }

		    return results;
	    }
	 
	 
	 
	 public List<LimitOrder> getOrders() {
	    	
		  List<LimitOrder> orders = new ArrayList<>();
			try {
				orders = tradeService.getOpenOrders().getOpenOrders();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return orders;
			}
	        return  orders;
	    }

	    public List<LimitOrder> getOrders(String pair) {
	        CurrencyPair currencyPair = CurrencyPairDeserializer.getCurrencyPairFromString(pair);
	        List<LimitOrder> orders = getOrders();
	        return orders.stream().filter(o->o.getCurrencyPair().toString()
	        		.equals(currencyPair.toString())).collect(Collectors.toList());
	    }
	 public List<Trade> getTrades() {
	    	
	    	UserTrades t = null;
	    	List<Trade> trades = new ArrayList<>();
			try {
				t = tradeService.getTradeHistory(null);
			} catch (ExchangeException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return trades;
			}
	        return t.getTrades();
	    }
	    
	    public TradeHistoryParamCurrencyPair getTradeHistoryParams(String pair) {
	    	CurrencyPair cp = CurrencyPairDeserializer.getCurrencyPairFromString(pair);
	    	
	    	KrakenTradeHistoryParams params = (KrakenTradeHistoryParams) tradeService.createTradeHistoryParams();
	        params.setCurrencyPair(cp);
	        
	        return params;
	    }

	    public List<Trade> getTrades(String pair) {
	    	UserTrades t = null;
	    	List<Trade> trades = new ArrayList<>();
			try {
				t = tradeService.getTradeHistory(getTradeHistoryParams(pair));
			} catch (ExchangeException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return trades;
			}
	        return t.getTrades();
	    }
	    
	    
	    public String placeSellLimitOrderWithStopCancel(
	            String pair,
	            BigDecimal amount,
	            BigDecimal limitPrice,
	            boolean includeStopLoss
	    ) {
	        CurrencyPair currencyPair = CurrencyPairDeserializer.getCurrencyPairFromString(pair);
	        BigDecimal stopPrice = new BigDecimal(limitPrice.doubleValue()*1.05);
	        try {
	            // Submit SELL LIMIT order
	            LimitOrder limitSell = new LimitOrder.Builder(OrderType.ASK, currencyPair)
	                    .limitPrice(limitPrice)
	                    .originalAmount(amount)
	                    .build();

	            String limitOrderId = tradeService.placeLimitOrder(limitSell);
	            System.out.println("Sell Limit order placed: " + limitOrderId);

	            String stopOrderId = null;

	            // Optionally submit STOP-LOSS
	            if (includeStopLoss && stopPrice != null) {
	                StopOrder stopLoss = new StopOrder.Builder(OrderType.BID, currencyPair)
	                        .originalAmount(amount)
	                        .stopPrice(stopPrice)
	                        .build();
	                stopOrderId = tradeService.placeStopOrder(stopLoss);
	                System.out.println("Stop-loss order placed: " + stopOrderId);
	            }

	            // Monitor manually (you'd normally run this async with websocket or polling)
	            // Sample logic:
	            /*
	            while (true) {
	                KrakenOrder limitStatus = tradeService.getKrakenOrder(limitOrderId);
	                KrakenOrder stopStatus = stopOrderId != null ? tradeService.getKrakenOrder(stopOrderId) : null;

	                if (limitStatus != null && "closed".equalsIgnoreCase(limitStatus.getStatus())) {
	                    if (stopOrderId != null) {
	                        tradeService.cancelOrder(stopOrderId);
	                        System.out.println("Limit order filled. Stop-loss canceled.");
	                    }
	                    break;
	                }

	                if (stopStatus != null && "closed".equalsIgnoreCase(stopStatus.getStatus())) {
	                    tradeService.cancelOrder(limitOrderId);
	                    System.out.println("Stop-loss triggered. Limit order canceled.");
	                    break;
	                }

	                Thread.sleep(5000);
	            }
	            */

	            return limitOrderId;
	        } catch (Exception e) {
	            e.printStackTrace();
	            return null;
	        }
	    }
	    
	    public String placeBuyLimitOrderWithStopLoss(
	    		String pair, 
	    		BigDecimal amount, 
	    		BigDecimal limitPrice,
	    		boolean includeStopLoss) 
	    {
	        CurrencyPair currencyPair = CurrencyPairDeserializer.getCurrencyPairFromString(pair);
	        BigDecimal stopPrice = new BigDecimal(limitPrice.doubleValue()*0.95);
	        try {
	            // Submit LIMIT order
	            LimitOrder limitOrder = new LimitOrder.Builder(OrderType.BID, currencyPair)
	                .limitPrice(limitPrice)
	                .originalAmount(amount)
	                .build();
	            String limitOrderId = tradeService.placeLimitOrder(limitOrder);
	            System.out.println("Limit order placed: " + limitOrderId);

	            // Optionally submit STOP order (only if supported and requested)
	            if (includeStopLoss && stopPrice != null) {
	                StopOrder stopOrder = new StopOrder.Builder(OrderType.ASK, currencyPair)
	                    .originalAmount(amount)
	                    .stopPrice(stopPrice)
	                    .build();
	                String stopOrderId = tradeService.placeStopOrder(stopOrder);
	                System.out.println("Stop-loss order placed: " + stopOrderId);
	            }

	            return limitOrderId;
	        } catch (Exception e) {
	            e.printStackTrace();
	            return null;
	        }
	    }
	    
	    
}


