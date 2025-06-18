package com.zigzag.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.kraken.KrakenExchange;
import org.knowm.xchange.kraken.dto.trade.KrakenOrder;
import org.knowm.xchange.kraken.service.KrakenAccountService;
import org.knowm.xchange.kraken.service.KrakenTradeHistoryParams;
import org.knowm.xchange.kraken.service.KrakenTradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.springframework.stereotype.Service;

@Service
public class KrakenService {

	private Exchange exchange;
	private KrakenAccountService accountService;
	private KrakenTradeService tradeService;
	
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
}
