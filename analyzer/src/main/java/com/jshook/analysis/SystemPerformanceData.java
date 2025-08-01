package com.jshook.analysis;

/**
 * Data class to hold performance metrics from optimal mixed workload components
 * All metrics are extracted from SystemMetrics JSON files
 */
public class SystemPerformanceData {
    private final String systemName;
    private final String systemProfile;
    
    // Random read component metrics from optimal mixed workload
    private final double randreadThroughputMBps;
    private final double randreadIOPS;
    private final double randreadLatencyP99Us;
    private final double randreadLatencyP95Us;
    private final double randreadLatencyP50Us;
    private final double randreadLatencyMeanUs;
    private final double randreadLatencyP99P50Ratio;
    
    // Sequential component metrics from optimal mixed workload  
    private final double seqreadThroughputMBps;
    private final double seqwriteThroughputMBps;
    
    // Analysis metrics
    private final String optimalBlocksize;
    private final double kneePointLatencyIncrease;
    private final double optimalStreamLimitMBps;
    private final String optimalMixedWorkloadName;
    private final int totalWorkloads;
    
    public SystemPerformanceData(String systemName, String systemProfile, 
                               SystemMetrics metrics) {
        this.systemName = systemName;
        this.systemProfile = systemProfile;
        
        // Extract all component metrics from SystemMetrics
        this.randreadThroughputMBps = metrics.getRandreadThroughputMBps();
        this.randreadIOPS = metrics.getRandreadIOPS();
        this.randreadLatencyP99Us = metrics.getRandreadLatencyP99Us();
        this.randreadLatencyP95Us = metrics.getRandreadLatencyP95Us();
        this.randreadLatencyP50Us = metrics.getRandreadLatencyP50Us();
        this.randreadLatencyMeanUs = metrics.getRandreadLatencyMeanUs();
        this.randreadLatencyP99P50Ratio = metrics.getRandreadLatencyP99P50Ratio();
        
        this.seqreadThroughputMBps = metrics.getSeqreadThroughputMBps();
        this.seqwriteThroughputMBps = metrics.getSeqwriteThroughputMBps();
        
        this.optimalBlocksize = metrics.getOptimalBlocksize();
        this.kneePointLatencyIncrease = metrics.getKneePointLatencyIncreasePercent();
        this.optimalStreamLimitMBps = metrics.getOptimalStreamLimitMBps();
        this.optimalMixedWorkloadName = metrics.getOptimalMixedWorkloadName();
        this.totalWorkloads = metrics.getTotalWorkloads();
    }
    
    // Legacy constructor for backward compatibility during transition
    @Deprecated
    public SystemPerformanceData(String systemName, String systemProfile, 
                               double optimalThroughputMBps, double optimalIOPS, 
                               double optimalLatencyP99, String optimalBlocksize,
                               double kneePointLatencyIncrease, int totalWorkloads) {
        this.systemName = systemName;
        this.systemProfile = systemProfile;
        this.randreadThroughputMBps = optimalThroughputMBps;
        this.randreadIOPS = optimalIOPS;
        this.randreadLatencyP99Us = optimalLatencyP99;
        this.randreadLatencyP95Us = 0.0;
        this.randreadLatencyP50Us = 0.0;
        this.randreadLatencyMeanUs = 0.0;
        this.randreadLatencyP99P50Ratio = 0.0;
        this.seqreadThroughputMBps = 0.0;
        this.seqwriteThroughputMBps = 0.0;
        this.optimalBlocksize = optimalBlocksize;
        this.kneePointLatencyIncrease = kneePointLatencyIncrease;
        this.optimalStreamLimitMBps = 0.0;
        this.optimalMixedWorkloadName = "";
        this.totalWorkloads = totalWorkloads;
    }
    
    // Getters
    public String getSystemName() { return systemName; }
    public String getSystemProfile() { return systemProfile; }
    
    // Random read component getters
    public double getRandreadThroughputMBps() { return randreadThroughputMBps; }
    public double getRandreadIOPS() { return randreadIOPS; }
    public double getRandreadLatencyP99Us() { return randreadLatencyP99Us; }
    public double getRandreadLatencyP95Us() { return randreadLatencyP95Us; }
    public double getRandreadLatencyP50Us() { return randreadLatencyP50Us; }
    public double getRandreadLatencyMeanUs() { return randreadLatencyMeanUs; }
    public double getRandreadLatencyP99P50Ratio() { return randreadLatencyP99P50Ratio; }
    
    // Sequential component getters
    public double getSeqreadThroughputMBps() { return seqreadThroughputMBps; }
    public double getSeqwriteThroughputMBps() { return seqwriteThroughputMBps; }
    
    // Analysis getters
    public String getOptimalBlocksize() { return optimalBlocksize; }
    public double getKneePointLatencyIncrease() { return kneePointLatencyIncrease; }
    public double getOptimalStreamLimitMBps() { return optimalStreamLimitMBps; }
    public String getOptimalMixedWorkloadName() { return optimalMixedWorkloadName; }
    public int getTotalWorkloads() { return totalWorkloads; }
    
    // Legacy getters for backward compatibility
    @Deprecated
    public double getOptimalThroughputMBps() { return randreadThroughputMBps; }
    @Deprecated 
    public double getOptimalIOPS() { return randreadIOPS; }
    @Deprecated
    public double getOptimalLatencyP99() { return randreadLatencyP99Us; }
    
    // Convenience methods
    public double getRandreadThroughputGBps() {
        return randreadThroughputMBps / 1024.0;
    }
    
    public double getSeqreadThroughputGBps() {
        return seqreadThroughputMBps / 1024.0;
    }
    
    public double getSeqwriteThroughputGBps() {
        return seqwriteThroughputMBps / 1024.0;
    }
    
    public String getPerformanceClass() {
        // Base performance class on randread throughput from mixed workload
        if (randreadThroughputMBps > 5000) {
            return "High";
        } else if (randreadThroughputMBps > 1000) {
            return "Medium";
        } else {
            return "Low";
        }
    }
    
    /**
     * Gets total combined throughput across all components
     */
    public double getTotalThroughputMBps() {
        return randreadThroughputMBps + seqreadThroughputMBps + seqwriteThroughputMBps;
    }
    
    /**
     * Gets combined sequential throughput (read + write)
     */
    public double getSequentialThroughputMBps() {
        return seqreadThroughputMBps + seqwriteThroughputMBps;
    }
}