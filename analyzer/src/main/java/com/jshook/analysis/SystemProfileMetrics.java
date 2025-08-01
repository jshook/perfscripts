package com.jshook.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * System profile performance metrics aggregated from optimal mixed workload components
 * All metrics are derived from the winning mixed workload results across systems
 */
public class SystemProfileMetrics {
    @JsonProperty("profile_name")
    private String profileName;
    
    @JsonProperty("total_systems")
    private int totalSystems;
    
    // Random read component aggregations from optimal mixed workloads
    @JsonProperty("average_randread_throughput_mbps")
    private double averageRandreadThroughputMBps;
    
    @JsonProperty("maximum_randread_throughput_mbps")
    private double maximumRandreadThroughputMBps;
    
    @JsonProperty("minimum_randread_throughput_mbps")
    private double minimumRandreadThroughputMBps;
    
    @JsonProperty("randread_throughput_range_factor")
    private double randreadThroughputRangeFactor;
    
    @JsonProperty("average_randread_iops")
    private double averageRandreadIOPS;
    
    @JsonProperty("maximum_randread_iops")
    private double maximumRandreadIOPS;
    
    @JsonProperty("average_randread_latency_p99_us")
    private double averageRandreadLatencyP99Us;
    
    @JsonProperty("best_randread_latency_p99_us")
    private double bestRandreadLatencyP99Us;
    
    @JsonProperty("worst_randread_latency_p99_us")
    private double worstRandreadLatencyP99Us;
    
    @JsonProperty("randread_latency_range_factor")
    private double randreadLatencyRangeFactor;
    
    @JsonProperty("average_randread_latency_p95_us")
    private double averageRandreadLatencyP95Us;
    
    @JsonProperty("average_randread_latency_p50_us")
    private double averageRandreadLatencyP50Us;
    
    @JsonProperty("average_randread_latency_p99_p50_ratio")
    private double averageRandreadLatencyP99P50Ratio;
    
    // Sequential component aggregations from optimal mixed workloads
    @JsonProperty("average_seqread_throughput_mbps")
    private double averageSeqreadThroughputMBps;
    
    @JsonProperty("maximum_seqread_throughput_mbps")
    private double maximumSeqreadThroughputMBps;
    
    @JsonProperty("average_seqwrite_throughput_mbps")
    private double averageSeqwriteThroughputMBps;
    
    @JsonProperty("maximum_seqwrite_throughput_mbps")
    private double maximumSeqwriteThroughputMBps;
    
    // Best system identification
    @JsonProperty("best_system_name")
    private String bestSystemName;
    
    @JsonProperty("best_system_randread_throughput_mbps")
    private double bestSystemRandreadThroughputMBps;
    
    @JsonProperty("system_names")
    private List<String> systemNames;
    
    @JsonProperty("analysis_timestamp")
    private String analysisTimestamp;
    
    // Legacy fields for backward compatibility
    @JsonProperty("average_throughput_mbps")
    @Deprecated
    private double averageThroughputMBps;
    
    @JsonProperty("average_throughput_gbps")
    @Deprecated
    private double averageThroughputGBps;
    
    @JsonProperty("maximum_throughput_mbps")
    @Deprecated
    private double maximumThroughputMBps;
    
    @JsonProperty("minimum_throughput_mbps")
    @Deprecated
    private double minimumThroughputMBps;
    
    @JsonProperty("throughput_range_factor")
    @Deprecated
    private double throughputRangeFactor;
    
    @JsonProperty("average_latency_p99_us")
    @Deprecated
    private double averageLatencyP99Us;
    
    @JsonProperty("best_latency_p99_us")
    @Deprecated
    private double bestLatencyP99Us;
    
    @JsonProperty("worst_latency_p99_us")
    @Deprecated
    private double worstLatencyP99Us;
    
    @JsonProperty("latency_range_factor")
    @Deprecated
    private double latencyRangeFactor;
    
    @JsonProperty("best_system_throughput_mbps")
    @Deprecated
    private double bestSystemThroughputMBps;
    
    // Default constructor for Jackson
    public SystemProfileMetrics() {
        this.systemNames = new ArrayList<>();
    }
    
    public SystemProfileMetrics(String profileName) {
        this.profileName = profileName;
        this.systemNames = new ArrayList<>();
        this.analysisTimestamp = java.time.LocalDateTime.now().toString();
    }
    
    // Getters and setters for new component-based metrics
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    
    public int getTotalSystems() { return totalSystems; }
    public void setTotalSystems(int totalSystems) { this.totalSystems = totalSystems; }
    
    // Random read component getters/setters
    public double getAverageRandreadThroughputMBps() { return averageRandreadThroughputMBps; }
    public void setAverageRandreadThroughputMBps(double averageRandreadThroughputMBps) { 
        this.averageRandreadThroughputMBps = averageRandreadThroughputMBps;
        // Update legacy fields for compatibility
        this.averageThroughputMBps = averageRandreadThroughputMBps;
        this.averageThroughputGBps = averageRandreadThroughputMBps / 1024.0;
    }
    
    public double getMaximumRandreadThroughputMBps() { return maximumRandreadThroughputMBps; }
    public void setMaximumRandreadThroughputMBps(double maximumRandreadThroughputMBps) { 
        this.maximumRandreadThroughputMBps = maximumRandreadThroughputMBps;
        this.maximumThroughputMBps = maximumRandreadThroughputMBps;
    }
    
    public double getMinimumRandreadThroughputMBps() { return minimumRandreadThroughputMBps; }
    public void setMinimumRandreadThroughputMBps(double minimumRandreadThroughputMBps) { 
        this.minimumRandreadThroughputMBps = minimumRandreadThroughputMBps;
        this.minimumThroughputMBps = minimumRandreadThroughputMBps;
    }
    
    public double getRandreadThroughputRangeFactor() { return randreadThroughputRangeFactor; }
    public void setRandreadThroughputRangeFactor(double randreadThroughputRangeFactor) { 
        this.randreadThroughputRangeFactor = randreadThroughputRangeFactor;
        this.throughputRangeFactor = randreadThroughputRangeFactor;
    }
    
    public double getAverageRandreadIOPS() { return averageRandreadIOPS; }
    public void setAverageRandreadIOPS(double averageRandreadIOPS) { this.averageRandreadIOPS = averageRandreadIOPS; }
    
    public double getMaximumRandreadIOPS() { return maximumRandreadIOPS; }
    public void setMaximumRandreadIOPS(double maximumRandreadIOPS) { this.maximumRandreadIOPS = maximumRandreadIOPS; }
    
    public double getAverageRandreadLatencyP99Us() { return averageRandreadLatencyP99Us; }
    public void setAverageRandreadLatencyP99Us(double averageRandreadLatencyP99Us) { 
        this.averageRandreadLatencyP99Us = averageRandreadLatencyP99Us;
        this.averageLatencyP99Us = averageRandreadLatencyP99Us;
    }
    
    public double getBestRandreadLatencyP99Us() { return bestRandreadLatencyP99Us; }
    public void setBestRandreadLatencyP99Us(double bestRandreadLatencyP99Us) { 
        this.bestRandreadLatencyP99Us = bestRandreadLatencyP99Us;
        this.bestLatencyP99Us = bestRandreadLatencyP99Us;
    }
    
    public double getWorstRandreadLatencyP99Us() { return worstRandreadLatencyP99Us; }
    public void setWorstRandreadLatencyP99Us(double worstRandreadLatencyP99Us) { 
        this.worstRandreadLatencyP99Us = worstRandreadLatencyP99Us;
        this.worstLatencyP99Us = worstRandreadLatencyP99Us;
    }
    
    public double getRandreadLatencyRangeFactor() { return randreadLatencyRangeFactor; }
    public void setRandreadLatencyRangeFactor(double randreadLatencyRangeFactor) { 
        this.randreadLatencyRangeFactor = randreadLatencyRangeFactor;
        this.latencyRangeFactor = randreadLatencyRangeFactor;
    }
    
    public double getAverageRandreadLatencyP95Us() { return averageRandreadLatencyP95Us; }
    public void setAverageRandreadLatencyP95Us(double averageRandreadLatencyP95Us) { this.averageRandreadLatencyP95Us = averageRandreadLatencyP95Us; }
    
    public double getAverageRandreadLatencyP50Us() { return averageRandreadLatencyP50Us; }
    public void setAverageRandreadLatencyP50Us(double averageRandreadLatencyP50Us) { this.averageRandreadLatencyP50Us = averageRandreadLatencyP50Us; }
    
    public double getAverageRandreadLatencyP99P50Ratio() { return averageRandreadLatencyP99P50Ratio; }
    public void setAverageRandreadLatencyP99P50Ratio(double averageRandreadLatencyP99P50Ratio) { this.averageRandreadLatencyP99P50Ratio = averageRandreadLatencyP99P50Ratio; }
    
    // Sequential component getters/setters
    public double getAverageSeqreadThroughputMBps() { return averageSeqreadThroughputMBps; }
    public void setAverageSeqreadThroughputMBps(double averageSeqreadThroughputMBps) { this.averageSeqreadThroughputMBps = averageSeqreadThroughputMBps; }
    
    public double getMaximumSeqreadThroughputMBps() { return maximumSeqreadThroughputMBps; }
    public void setMaximumSeqreadThroughputMBps(double maximumSeqreadThroughputMBps) { this.maximumSeqreadThroughputMBps = maximumSeqreadThroughputMBps; }
    
    public double getAverageSeqwriteThroughputMBps() { return averageSeqwriteThroughputMBps; }
    public void setAverageSeqwriteThroughputMBps(double averageSeqwriteThroughputMBps) { this.averageSeqwriteThroughputMBps = averageSeqwriteThroughputMBps; }
    
    public double getMaximumSeqwriteThroughputMBps() { return maximumSeqwriteThroughputMBps; }
    public void setMaximumSeqwriteThroughputMBps(double maximumSeqwriteThroughputMBps) { this.maximumSeqwriteThroughputMBps = maximumSeqwriteThroughputMBps; }
    
    // System identification getters/setters
    public String getBestSystemName() { return bestSystemName; }
    public void setBestSystemName(String bestSystemName) { this.bestSystemName = bestSystemName; }
    
    public double getBestSystemRandreadThroughputMBps() { return bestSystemRandreadThroughputMBps; }
    public void setBestSystemRandreadThroughputMBps(double bestSystemRandreadThroughputMBps) { 
        this.bestSystemRandreadThroughputMBps = bestSystemRandreadThroughputMBps;
        this.bestSystemThroughputMBps = bestSystemRandreadThroughputMBps;
    }
    
    public List<String> getSystemNames() { return systemNames; }
    public void setSystemNames(List<String> systemNames) { this.systemNames = systemNames; }
    
    public String getAnalysisTimestamp() { return analysisTimestamp; }
    public void setAnalysisTimestamp(String analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }
    
    // Legacy getters for backward compatibility
    @Deprecated
    public double getAverageThroughputMBps() { return averageThroughputMBps; }
    @Deprecated
    public double getAverageThroughputGBps() { return averageThroughputGBps; }
    @Deprecated
    public double getMaximumThroughputMBps() { return maximumThroughputMBps; }
    @Deprecated
    public double getMinimumThroughputMBps() { return minimumThroughputMBps; }
    @Deprecated
    public double getThroughputRangeFactor() { return throughputRangeFactor; }
    @Deprecated
    public double getAverageLatencyP99Us() { return averageLatencyP99Us; }
    @Deprecated
    public double getBestLatencyP99Us() { return bestLatencyP99Us; }
    @Deprecated
    public double getWorstLatencyP99Us() { return worstLatencyP99Us; }
    @Deprecated
    public double getLatencyRangeFactor() { return latencyRangeFactor; }
    @Deprecated
    public double getBestSystemThroughputMBps() { return bestSystemThroughputMBps; }
    
    // Convenience methods
    public double getAverageRandreadThroughputGBps() {
        return averageRandreadThroughputMBps / 1024.0;
    }
    
    public double getAverageSeqreadThroughputGBps() {
        return averageSeqreadThroughputMBps / 1024.0;
    }
    
    public double getAverageSeqwriteThroughputGBps() {
        return averageSeqwriteThroughputMBps / 1024.0;
    }
    
    /**
     * Gets total combined average throughput across all components
     */
    public double getAverageTotalThroughputMBps() {
        return averageRandreadThroughputMBps + averageSeqreadThroughputMBps + averageSeqwriteThroughputMBps;
    }
}