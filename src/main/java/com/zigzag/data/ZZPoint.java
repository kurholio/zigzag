package com.zigzag.data;

import java.util.Date;

import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.kraken.Kraken;
import org.knowm.xchange.kraken.dto.trade.KrakenOrder;

public class ZZPoint {
    public long timestamp;
    public double open;
    public double high;
    public double low;
    public double close;
    public double volume;

    public String type = "NONE"; // e.g. PEAK or VALLEY

    // Enriched features
    public double sma5 = 0.0;
    public double rsi14 = 0.0;
    public double momentum = 0.0;
    public double volatility = 0.0;
    private Trade trade;
    private KrakenOrder order;
    public boolean saleTrade = false;
    public boolean buyTrade = false;
    
    public ZZPoint() {}
    
    public ZZPoint(long timestamp, double open, double high, double low,
         double close, double volume, String type) {
		 this.timestamp = timestamp;
		 this.open = open;
		 this.high = high;
		 this.low = low;
		 this.close = close;
		 this.volume = volume;
		 this.type = type;
		}

    public ZZPoint(ZZPoint other) {
        this.timestamp = other.timestamp;
        this.open = other.open;
        this.high = other.high;
        this.low = other.low;
        this.close = other.close;
        this.volume = other.volume;
        this.type = other.type;
        this.sma5 = other.sma5;
        this.rsi14 = other.rsi14;
        this.momentum = other.momentum;
        this.volatility = other.volatility;
    }
    
   

    public void setTrade(Trade trade) {
        this.trade = trade;
    }

    public Trade getTrade() {
        return trade;
    }

    public boolean isZigZag() {
    	return "HIGH".equals(type) || "LOW".equals(type);
    }

    public Date getDate() {
        return new Date(timestamp);
    }   

    public void setOrder(KrakenOrder order) {
        this.order = order;
    }

    public KrakenOrder getOrder() {
        return this.order;
    }


    public boolean isOrder() {
        return order != null;
    }

    public boolean isSaleTrade() {
    	
    	if (trade == null) {
    		return false;
    	}
        if( trade.getType() == OrderType.ASK) {
        	return true;
        } else {
        	return false;
        }
    }

    public boolean isBuyTrade() {
    	if (trade == null) {
    		return false;
    	}
    	if( trade.getType() == OrderType.BID) {
        	return true;
        } else {
        	return false;
        }
    }


    @Override
    public String toString() {
        return "ZZPoint{" +
                ", timestamp=" + timestamp +
                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", close=" + close +
                ", volume=" + volume +
                ", type='" + type + '\'' +
                ", sma5=" + sma5 +
                ", rsi14=" + rsi14 +
                ", momentum=" + momentum +
                ", volatility=" + volatility +
                '}';
    }
} 
