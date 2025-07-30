package com.jshook.analysis;

import java.util.List;

/// Generates Unicode-based sparklines for performance data visualization
public class SparklineGenerator {
    
    /// Unicode characters for sparklines (8 levels)
    private static final char[] SPARK_CHARS = {
        '▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'
    };
    
    /// Generates a sparkline from a list of values
    public static String generateSparkline(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        
        // Find min and max values
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        
        if (min == max) {
            // All values are the same
            return String.valueOf(SPARK_CHARS[4]).repeat(values.size());
        }
        
        StringBuilder sparkline = new StringBuilder();
        double range = max - min;
        
        for (double value : values) {
            // Normalize value to 0-1 range
            double normalized = (value - min) / range;
            
            // Map to spark character index (0-7)
            int index = (int) Math.round(normalized * (SPARK_CHARS.length - 1));
            index = Math.max(0, Math.min(SPARK_CHARS.length - 1, index));
            
            sparkline.append(SPARK_CHARS[index]);
        }
        
        return sparkline.toString();
    }
    
    /// Generates a sparkline showing order of magnitude changes
    public static String generateOrderOfMagnitudeSparkline(List<Double> values) {
        if (values == null || values.size() < 2) {
            return generateSparkline(values);
        }
        
        StringBuilder sparkline = new StringBuilder();
        
        for (int i = 0; i < values.size(); i++) {
            double value = values.get(i);
            
            if (i == 0) {
                // First value - use middle character
                sparkline.append(SPARK_CHARS[4]);
            } else {
                double prevValue = values.get(i - 1);
                double ratio = value / prevValue;
                
                // Map ratio to sparkline character
                char sparkChar;
                if (ratio < 0.5) {
                    sparkChar = SPARK_CHARS[0]; // Significant decrease
                } else if (ratio < 0.8) {
                    sparkChar = SPARK_CHARS[2]; // Moderate decrease
                } else if (ratio < 1.2) {
                    sparkChar = SPARK_CHARS[4]; // Roughly same
                } else if (ratio < 2.0) {
                    sparkChar = SPARK_CHARS[6]; // Moderate increase
                } else {
                    sparkChar = SPARK_CHARS[7]; // Significant increase
                }
                
                sparkline.append(sparkChar);
            }
        }
        
        return sparkline.toString();
    }
    
    /// Generates a sparkline with labels showing the actual values
    public static String generateLabeledSparkline(List<Double> values, String unit) {
        String sparkline = generateSparkline(values);
        
        if (values.isEmpty()) {
            return sparkline;
        }
        
        // Add min and max labels
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        
        return String.format("%s (%.1f%s - %.1f%s)", sparkline, min, unit, max, unit);
    }
    
    /// Generates a logarithmic sparkline showing proportional changes
    /// Requirement: Line 71 - logarithmic scale representation
    public static String generateLogarithmicSparkline(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        
        // Convert to log scale for better visualization of wide ranges
        List<Double> logValues = values.stream()
            .map(v -> v > 0 ? Math.log10(v) : 0.0)
            .collect(java.util.stream.Collectors.toList());
        
        return generateSparkline(logValues);
    }
    
    /// Gets a single sparkline character for a specific index in a value list
    /// Used for grid pattern layouts (requirement line 72-73)
    public static char getSingleSparklineChar(List<Double> values, int index) {
        if (values == null || values.isEmpty() || index < 0 || index >= values.size()) {
            return '▁';
        }
        
        // Find min and max values
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        
        if (min == max) {
            return SPARK_CHARS[4]; // Middle character for uniform values
        }
        
        // Normalize the specific value
        double value = values.get(index);
        double normalized = (value - min) / (max - min);
        
        // Map to spark character index (0-7)
        int charIndex = (int) Math.round(normalized * (SPARK_CHARS.length - 1));
        charIndex = Math.max(0, Math.min(SPARK_CHARS.length - 1, charIndex));
        
        return SPARK_CHARS[charIndex];
    }
}