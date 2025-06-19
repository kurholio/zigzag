package com.zigzag.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.kraken.KrakenExchange;
import org.knowm.xchange.kraken.dto.trade.KrakenOrder;
import org.knowm.xchange.kraken.service.KrakenAccountService;
import org.knowm.xchange.kraken.service.KrakenTradeHistoryParams;
import org.knowm.xchange.kraken.service.KrakenTradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class KrakenService {

	private Exchange exchange;
	private KrakenAccountService accountService;
	private KrakenTradeService tradeService;
	private WebSocketClient wsClient;
	public Map<String, BigDecimal> latestPrices = new ConcurrentHashMap<>();
	private String xmlAK = "u1KONtudYv6wdb6DIRWmrGTDV5es5Ivo4K4SHtYKFFTG+CGxKGDQiK4G";
	private String xmpk = "XqeUljXl8uys1CdmT/oYh8Q/0+qjzwyztQIXQ4KriN4l13cU9Jaoe7DN8+ff3VeGl3Vtehw3/v3TbkVg4Gs5VQ==";

	public KrakenService() {
		ExchangeSpecification spec = new KrakenExchange().getDefaultExchangeSpecification();
        spec.setApiKey(xmlAK);
        spec.setSecretKey(xmpk);
        
        exchange = ExchangeFactory.INSTANCE.createExchange(spec);
        
        // Initialize services as final fields
        accountService = (KrakenAccountService) exchange.getAccountService();
        tradeService = (KrakenTradeService) exchange.getTradeService();
	}
    
    
    
    public Map<String, Wallet> getWallets() {
    	
        Map<String, Wallet> wallets = new HashMap<>();
		try {
			wallets = accountService.getAccountInfo().getWallets();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return wallets;
		}
       return wallets;
    }
    
    public Wallet getWallet() {
    	
    	Wallet wallet = null;
		try {
			wallet = accountService.getAccountInfo().getWallet();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return wallet;
    }

    public Map<String, BigDecimal> getBalances() {
    	
    	
    	Collection<Wallet> wallets = getWallets().values();
    	Map<String, BigDecimal> allBalances = new HashMap<>();

    	for (Wallet wallet : wallets) {
    	    for (Balance balance : wallet.getBalances().values()) {
    	        allBalances.merge(balance.getCurrency().getCurrencyCode(), balance.getTotal(), BigDecimal::add);
    	    }
    	}
    	
    
        return allBalances;
    }

    public BigDecimal getBalance(String symbol) {
        return getBalances().get(symbol);
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
    	CurrencyPair cp = CurrencyPairDeserializer.deserialize(pair);
    	
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

    public Map<String, KrakenOrder> getOrders() {
    	
    	Map<String, KrakenOrder> orders = new HashMap<>();
		try {
			orders = tradeService.getKrakenOpenOrders();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return orders;
		}
        return  orders;
    }

    public List<KrakenOrder> getOrders(String pair) {
        CurrencyPair currencyPair = CurrencyPairDeserializer.deserialize(pair);
        Map<String, KrakenOrder> orders = getOrders();
        return orders.values().stream().filter(o->o.getOrderDescription().
        		getAssetPair().equals(currencyPair.toString())).collect(Collectors.toList());
    }

    private static class CurrencyPairDeserializer {
        public static CurrencyPair deserialize(String pair) {
            String base = pair.substring(0, 3);
            String counter = pair.substring(3);
            return new CurrencyPair(base, counter);
        }
    }
    
    public String placeSellLimitOrderWithStopCancel(
            String pair,
            BigDecimal amount,
            BigDecimal limitPrice,
            boolean includeStopLoss
    ) {
        CurrencyPair currencyPair = CurrencyPairDeserializer.deserialize(pair);
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
        } catch (IOException e) {
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
        CurrencyPair currencyPair = CurrencyPairDeserializer.deserialize(pair);
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
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    
    public void connectWebSocketTicker(String krakenPair) {
    	
    	
        try {
        	if (krakenPair.equals("BTCUSD")) {
        		krakenPair = "XBT/USD";
        	}
        	String symbol = krakenPair;
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
    
    public BigDecimal getLatestPrice() {
    	if (latestPrices.size() > 0) {
    		return latestPrices.values().iterator().next();
    	} else {
    		return new BigDecimal(0.0);
    	}
    }
    
    public BigDecimal getLatestPrice(String krakenPair) {
    	
    	if (krakenPair.equals("BTCUSD")) {
    		krakenPair = "XBT/USD";
    	}
        return latestPrices.get(krakenPair);
    }
}
