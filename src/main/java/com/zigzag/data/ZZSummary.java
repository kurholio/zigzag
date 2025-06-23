package com.zigzag.data;

public class ZZSummary {
	
    public double targetBuy;
    public double targetSell;
    public double buyConfidence;
    public double sellConfidence;
    public double currentPrice;
    public String base="";
    public String counter="";
    public Integer interval =0;
    public Integer leftBars=0;
    public Integer rightBars=0;
    public Integer percentChange=0;
    public Integer daysBack=0;
    
    public void setFrom(ZZTradePrediction tp, double currentPrice) {
    	targetBuy = tp.buyPrice;
    	targetSell = tp.sellPrice;
    	buyConfidence = tp.buyConfidence;
    	sellConfidence = tp.sellConfidence;
    	this.currentPrice = currentPrice;
    }

}
