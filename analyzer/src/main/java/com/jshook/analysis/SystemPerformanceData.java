package com.jshook.analysis;

/**
 * Data class to hold performance metrics extracted from system analysis reports
 */
public class SystemPerformanceData {
    private final String systemName;
    private final String systemProfile;
    private final double optimalThroughputMBps; // MB/s
    private final double optimalIOPS;
    private final double optimalLatencyP99; // microseconds
    private final String optimalBlocksize;
    private final double kneePointLatencyIncrease; // percentage
    private final int totalWorkloads;
    
    public SystemPerformanceData(String systemName, String systemProfile, 
                               double optimalThroughputMBps, double optimalIOPS, 
                               double optimalLatencyP99, String optimalBlocksize,
                               double kneePointLatencyIncrease, int totalWorkloads) {
        this.systemName = systemName;
        this.systemProfile = systemProfile;
        this.optimalThroughputMBps = optimalThroughputMBps;
        this.optimalIOPS = optimalIOPS;
        this.optimalLatencyP99 = optimalLatencyP99;
        this.optimalBlocksize = optimalBlocksize;
        this.kneePointLatencyIncrease = kneePointLatencyIncrease;
        this.totalWorkloads = totalWorkloads;
    }
    
    // Getters
    public String getSystemName() { return systemName; }
    public String getSystemProfile() { return systemProfile; }
    public double getOptimalThroughputMBps() { return optimalThroughputMBps; }
    public double getOptimalIOPS() { return optimalIOPS; }
    public double getOptimalLatencyP99() { return optimalLatencyP99; }
    public String getOptimalBlocksize() { return optimalBlocksize; }
    public double getKneePointLatencyIncrease() { return kneePointLatencyIncrease; }
    public int getTotalWorkloads() { return totalWorkloads; }
    
    // Convenience methods
    public double getOptimalThroughputGBps() {
        return optimalThroughputMBps / 1024.0;
    }
    
    public String getPerformanceClass() {
        if (optimalThroughputMBps > 5000) {
            return "High";
        } else if (optimalThroughputMBps > 1000) {
            return "Medium";
        } else {
            return "Low";
        }
    }
}