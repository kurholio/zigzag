package com.zigzag.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.springframework.stereotype.Service;

import com.zigzag.data.ZZWallet;

@Service
public class ExchangeService {
	
	 public List<ZZWallet> fetchWalletData(String exchangeClass, String apiKey, String secretKey) throws Exception {
		 Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exchangeClass);
		    exchange.getExchangeSpecification().setApiKey(apiKey);
		    exchange.getExchangeSpecification().setSecretKey(secretKey);
		    exchange.applySpecification(exchange.getExchangeSpecification());

		    AccountService accountService = exchange.getAccountService();
		    MarketDataService marketDataService = exchange.getMarketDataService();
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
	    
}


