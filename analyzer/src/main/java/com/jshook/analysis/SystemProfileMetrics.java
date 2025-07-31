package com.jshook.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * System profile performance metrics for value function scoring
 */
public class SystemProfileMetrics {
    @JsonProperty("profile_name")
    private String profileName;
    
    @JsonProperty("total_systems")
    private int totalSystems;
    
    @JsonProperty("average_throughput_mbps")
    private double averageThroughputMBps;
    
    @JsonProperty("average_throughput_gbps")
    private double averageThroughputGBps;
    
    @JsonProperty("maximum_throughput_mbps")
    private double maximumThroughputMBps;
    
    @JsonProperty("minimum_throughput_mbps")
    private double minimumThroughputMBps;
    
    @JsonProperty("throughput_range_factor")
    private double throughputRangeFactor;
    
    @JsonProperty("average_latency_p99_us")
    private double averageLatencyP99Us;
    
    @JsonProperty("best_latency_p99_us")
    private double bestLatencyP99Us;
    
    @JsonProperty("worst_latency_p99_us")
    private double worstLatencyP99Us;
    
    @JsonProperty("latency_range_factor")
    private double latencyRangeFactor;
    
    @JsonProperty("best_system_name")
    private String bestSystemName;
    
    @JsonProperty("best_system_throughput_mbps")
    private double bestSystemThroughputMBps;
    
    @JsonProperty("system_names")
    private List<String> systemNames;
    
    @JsonProperty("analysis_timestamp")
    private String analysisTimestamp;
    
    // Default constructor for Jackson
    public SystemProfileMetrics() {
        this.systemNames = new ArrayList<>();
    }
    
    public SystemProfileMetrics(String profileName) {
        this.profileName = profileName;
        this.systemNames = new ArrayList<>();
        this.analysisTimestamp = java.time.LocalDateTime.now().toString();
    }
    
    // Getters and setters
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    
    public int getTotalSystems() { return totalSystems; }
    public void setTotalSystems(int totalSystems) { this.totalSystems = totalSystems; }
    
    public double getAverageThroughputMBps() { return averageThroughputMBps; }
    public void setAverageThroughputMBps(double averageThroughputMBps) { 
        this.averageThroughputMBps = averageThroughputMBps;
        this.averageThroughputGBps = averageThroughputMBps / 1024.0;
    }
    
    public double getAverageThroughputGBps() { return averageThroughputGBps; }
    
    public double getMaximumThroughputMBps() { return maximumThroughputMBps; }
    public void setMaximumThroughputMBps(double maximumThroughputMBps) { this.maximumThroughputMBps = maximumThroughputMBps; }
    
    public double getMinimumThroughputMBps() { return minimumThroughputMBps; }
    public void setMinimumThroughputMBps(double minimumThroughputMBps) { this.minimumThroughputMBps = minimumThroughputMBps; }
    
    public double getThroughputRangeFactor() { return throughputRangeFactor; }
    public void setThroughputRangeFactor(double throughputRangeFactor) { this.throughputRangeFactor = throughputRangeFactor; }
    
    public double getAverageLatencyP99Us() { return averageLatencyP99Us; }
    public void setAverageLatencyP99Us(double averageLatencyP99Us) { this.averageLatencyP99Us = averageLatencyP99Us; }
    
    public double getBestLatencyP99Us() { return bestLatencyP99Us; }
    public void setBestLatencyP99Us(double bestLatencyP99Us) { this.bestLatencyP99Us = bestLatencyP99Us; }
    
    public double getWorstLatencyP99Us() { return worstLatencyP99Us; }
    public void setWorstLatencyP99Us(double worstLatencyP99Us) { this.worstLatencyP99Us = worstLatencyP99Us; }
    
    public double getLatencyRangeFactor() { return latencyRangeFactor; }
    public void setLatencyRangeFactor(double latencyRangeFactor) { this.latencyRangeFactor = latencyRangeFactor; }
    
    public String getBestSystemName() { return bestSystemName; }
    public void setBestSystemName(String bestSystemName) { this.bestSystemName = bestSystemName; }
    
    public double getBestSystemThroughputMBps() { return bestSystemThroughputMBps; }
    public void setBestSystemThroughputMBps(double bestSystemThroughputMBps) { this.bestSystemThroughputMBps = bestSystemThroughputMBps; }
    
    public List<String> getSystemNames() { return systemNames; }
    public void setSystemNames(List<String> systemNames) { this.systemNames = systemNames; }
    
    public String getAnalysisTimestamp() { return analysisTimestamp; }
    public void setAnalysisTimestamp(String analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }
}