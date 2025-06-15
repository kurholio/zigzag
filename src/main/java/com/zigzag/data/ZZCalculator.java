package com.zigzag.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ZZCalculator {
	
	public static List<ZZPoint> calculateZigZag(List<ZZPoint> bars, int leftBars, int rightBars, double minPercentChange) {
        List<ZZPoint> rawPivots = new ArrayList<>();
        int n = bars.size();

        for (int i = leftBars; i < n - rightBars; i++) {
            double currentHigh = bars.get(i).high;
            double currentLow = bars.get(i).low;
            boolean isHigh = true, isLow = true;

            for (int j = 1; j <= leftBars; j++) {
                if (bars.get(i - j).high >= currentHigh) isHigh = false;
                if (bars.get(i - j).low <= currentLow) isLow = false;
            }
            for (int j = 1; j <= rightBars; j++) {
                if (bars.get(i + j).high >= currentHigh) isHigh = false;
                if (bars.get(i + j).low <= currentLow) isLow = false;
            }

            if (isHigh) {
                ZZPoint p = bars.get(i);
                rawPivots.add(new ZZPoint(p.timestamp, p.open, p.high, p.low, p.close, p.volume, "HIGH"));
            } else if (isLow) {
                ZZPoint p = bars.get(i);
                rawPivots.add(new ZZPoint(p.timestamp, p.open, p.high, p.low, p.close, p.volume, "LOW"));
            }
        }

        // Filter by percent change
        List<ZZPoint> zigzag = new ArrayList<>();
        if (!rawPivots.isEmpty()) {
            zigzag.add(rawPivots.get(0));
            for (int i = 1; i < rawPivots.size(); i++) {
                ZZPoint last = zigzag.get(zigzag.size() - 1);
                ZZPoint current = rawPivots.get(i);
                double change = Math.abs((current.close - last.close) / last.close) * 100;

                if (change >= minPercentChange && !current.type.equals(last.type)) {
                    zigzag.add(current);
                } else if (current.type.equals(last.type)) {
                    // Keep the more extreme value of same type
                    if ((current.type.equals("HIGH") && current.high > last.high) ||
                        (current.type.equals("LOW") && current.low < last.low)) {
                        zigzag.set(zigzag.size() - 1, current);
                    }
                }
            }
        }

        return zigzag;
    }
	
	public static List<ZZPoint> generateFeatureEnrichedZZPoints(List<ZZPoint> ohlcBars, List<ZZPoint> zigzags, boolean zigZagOnly) {
	    int window = 5;
	    List<ZZPoint> enriched = new ArrayList<>();

	    for (int i = 0; i < ohlcBars.size(); i++) {
	        ZZPoint bar = ohlcBars.get(i);
	        ZZPoint enrichedBar = new ZZPoint(bar); // copy constructor

	        // Match to ZigZag point
	        Optional<ZZPoint> match = zigzags.stream()
	                .filter(z -> z.timestamp == bar.timestamp)
	                .findFirst();

	        match.ifPresent(z -> {
	            enrichedBar.type = z.type;
	        });

	        // Technical indicators
	        if (i >= window) {
	            List<ZZPoint> windowBars = ohlcBars.subList(i - window, i);

	            double sma = round(windowBars.stream().mapToDouble(b -> b.close).average().orElse(0));
	            enrichedBar.sma5 = sma;

	            double mom = round(bar.close - windowBars.get(0).close);
	            enrichedBar.momentum = mom;

	            double vol = round(Math.sqrt(windowBars.stream()
	                    .mapToDouble(b -> Math.pow(b.close - sma, 2))
	                    .sum() / window));
	            enrichedBar.volatility = vol;
	        }

	        // RSI (14)
	        if (i >= 14) {
	            List<ZZPoint> windowBars = ohlcBars.subList(i - 14, i);
	            double gain = 0, loss = 0;
	            for (int j = 1; j < windowBars.size(); j++) {
	                double diff = windowBars.get(j).close - windowBars.get(j - 1).close;
	                if (diff > 0) gain += diff;
	                else loss -= diff;
	            }
	            double rs = (loss == 0) ? 100 : gain / loss;
	            double rsi = 100 - (100 / (1 + rs));
	            enrichedBar.rsi14 = round(rsi);
	        }
	        

	        if (zigZagOnly) {
		        if (enrichedBar.isZigZag()) {
		        	enriched.add(enrichedBar);
		        }
	        } else {
	        	enriched.add(enrichedBar);
	        }
	    }

	    return enriched;
	}

	private static double round(double value) {
	    return Math.round(value * 100.0) / 100.0;
	}
}
