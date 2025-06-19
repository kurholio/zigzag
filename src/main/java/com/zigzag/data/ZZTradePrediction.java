package com.zigzag.data;

import java.math.BigDecimal;

public class ZZTradePrediction {
    public double buyPrice;
    public double sellPrice;
    public double buyConfidence;
    public double sellConfidence;
    public String rationale;
    public String prompt;
    
    
    public boolean isBuyNow(BigDecimal currentPrice) {
    	return currentPrice.doubleValue() <= buyPrice && buyConfidence>0.7;
    }
    
    public boolean isSellNow(BigDecimal currentPrice) {
    	return currentPrice.doubleValue()>= sellPrice && sellConfidence>0.7;
    }
}
