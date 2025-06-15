package com.zigzag.data;

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
    public double percentFromZigZag = 0.0;
    public int barsSinceZigZag = 0;

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
        this.percentFromZigZag = other.percentFromZigZag;
        this.barsSinceZigZag = other.barsSinceZigZag;
    }
    
    public boolean isZigZag() {
    	return "HIGH".equals(type) || "LOW".equals(type);
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
