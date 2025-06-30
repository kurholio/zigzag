package com.zigzag.data;

import java.math.BigDecimal;
import java.util.List;

public class ZZWallet {
	public String currency;
	public BigDecimal balance;
	public BigDecimal usdValue;
	public List<String> tradablePairs;
	
	public String getCurrency() {
		return currency;
	}
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	public BigDecimal getBalance() {
		return balance;
	}
	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}
	public BigDecimal getUsdValue() {
		return usdValue;
	}
	public void setUsdValue(BigDecimal usdValue) {
		this.usdValue = usdValue;
	}
	public List<String> getTradablePairs() {
		return tradablePairs;
	}
	public void setTradablePairs(List<String> tradablePairs) {
		this.tradablePairs = tradablePairs;
	}
	
	
}
