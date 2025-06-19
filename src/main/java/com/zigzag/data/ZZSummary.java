package com.zigzag.data;

public class ZZSummary {
	
    public double targetBuy;
    public double targetSell;
    public double buyConfidence;
    public double sellConfidence;
    public double currentPrice;
    public String base;
    public String counter;
    public Integer interval;
    public Integer leftBars;
    public Integer rightBars;
    public Integer percentChange;
    public Integer daysBack; 
    
    public void setFrom(ZZTradePrediction tp, double currentPrice) {
    	targetBuy = tp.buyPrice;
    	targetSell = tp.sellPrice;
    	buyConfidence = tp.buyConfidence;
    	sellConfidence = tp.sellConfidence;
    	this.currentPrice = currentPrice;
    }

}
