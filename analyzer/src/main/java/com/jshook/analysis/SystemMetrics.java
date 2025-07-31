package com.jshook.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * System performance metrics for value function scoring
 */
public class SystemMetrics {
    @JsonProperty("system_name")
    private String systemName;
    
    @JsonProperty("system_profile")
    private String systemProfile;
    
    @JsonProperty("optimal_throughput_mbps")
    private double optimalThroughputMBps;
    
    @JsonProperty("optimal_throughput_gbps")
    private double optimalThroughputGBps;
    
    @JsonProperty("optimal_iops")
    private double optimalIOPS;
    
    @JsonProperty("optimal_blocksize")
    private String optimalBlocksize;
    
    @JsonProperty("optimal_latency_mean_us")
    private double optimalLatencyMeanUs;
    
    @JsonProperty("optimal_latency_p95_us")
    private double optimalLatencyP95Us;
    
    @JsonProperty("optimal_latency_p99_us")
    private double optimalLatencyP99Us;
    
    @JsonProperty("knee_point_latency_increase_percent")
    private double kneePointLatencyIncreasePercent;
    
    @JsonProperty("mixed_workload_optimal_throughput_mbps")
    private double mixedWorkloadOptimalThroughputMBps;
    
    @JsonProperty("mixed_workload_optimal_iops")
    private double mixedWorkloadOptimalIOPS;
    
    @JsonProperty("mixed_workload_optimal_latency_p99_us")
    private double mixedWorkloadOptimalLatencyP99Us;
    
    @JsonProperty("mixed_workload_suboptimal_throughput_mbps")
    private double mixedWorkloadSubOptimalThroughputMBps;
    
    @JsonProperty("mixed_workload_suboptimal_latency_p99_us")
    private double mixedWorkloadSubOptimalLatencyP99Us;
    
    @JsonProperty("total_workloads")
    private int totalWorkloads;
    
    @JsonProperty("analysis_timestamp")
    private String analysisTimestamp;
    
    // Default constructor for Jackson
    public SystemMetrics() {}
    
    public SystemMetrics(String systemName, String systemProfile) {
        this.systemName = systemName;
        this.systemProfile = systemProfile;
        this.analysisTimestamp = java.time.LocalDateTime.now().toString();
    }
    
    // Getters and setters
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }
    
    public String getSystemProfile() { return systemProfile; }
    public void setSystemProfile(String systemProfile) { this.systemProfile = systemProfile; }
    
    public double getOptimalThroughputMBps() { return optimalThroughputMBps; }
    public void setOptimalThroughputMBps(double optimalThroughputMBps) { 
        this.optimalThroughputMBps = optimalThroughputMBps;
        this.optimalThroughputGBps = optimalThroughputMBps / 1024.0;
    }
    
    public double getOptimalThroughputGBps() { return optimalThroughputGBps; }
    
    public double getOptimalIOPS() { return optimalIOPS; }
    public void setOptimalIOPS(double optimalIOPS) { this.optimalIOPS = optimalIOPS; }
    
    public String getOptimalBlocksize() { return optimalBlocksize; }
    public void setOptimalBlocksize(String optimalBlocksize) { this.optimalBlocksize = optimalBlocksize; }
    
    public double getOptimalLatencyMeanUs() { return optimalLatencyMeanUs; }
    public void setOptimalLatencyMeanUs(double optimalLatencyMeanUs) { this.optimalLatencyMeanUs = optimalLatencyMeanUs; }
    
    public double getOptimalLatencyP95Us() { return optimalLatencyP95Us; }
    public void setOptimalLatencyP95Us(double optimalLatencyP95Us) { this.optimalLatencyP95Us = optimalLatencyP95Us; }
    
    public double getOptimalLatencyP99Us() { return optimalLatencyP99Us; }
    public void setOptimalLatencyP99Us(double optimalLatencyP99Us) { this.optimalLatencyP99Us = optimalLatencyP99Us; }
    
    public double getKneePointLatencyIncreasePercent() { return kneePointLatencyIncreasePercent; }
    public void setKneePointLatencyIncreasePercent(double kneePointLatencyIncreasePercent) { this.kneePointLatencyIncreasePercent = kneePointLatencyIncreasePercent; }
    
    public double getMixedWorkloadOptimalThroughputMBps() { return mixedWorkloadOptimalThroughputMBps; }
    public void setMixedWorkloadOptimalThroughputMBps(double mixedWorkloadOptimalThroughputMBps) { this.mixedWorkloadOptimalThroughputMBps = mixedWorkloadOptimalThroughputMBps; }
    
    public double getMixedWorkloadOptimalIOPS() { return mixedWorkloadOptimalIOPS; }
    public void setMixedWorkloadOptimalIOPS(double mixedWorkloadOptimalIOPS) { this.mixedWorkloadOptimalIOPS = mixedWorkloadOptimalIOPS; }
    
    public double getMixedWorkloadOptimalLatencyP99Us() { return mixedWorkloadOptimalLatencyP99Us; }
    public void setMixedWorkloadOptimalLatencyP99Us(double mixedWorkloadOptimalLatencyP99Us) { this.mixedWorkloadOptimalLatencyP99Us = mixedWorkloadOptimalLatencyP99Us; }
    
    public double getMixedWorkloadSubOptimalThroughputMBps() { return mixedWorkloadSubOptimalThroughputMBps; }
    public void setMixedWorkloadSubOptimalThroughputMBps(double mixedWorkloadSubOptimalThroughputMBps) { this.mixedWorkloadSubOptimalThroughputMBps = mixedWorkloadSubOptimalThroughputMBps; }
    
    public double getMixedWorkloadSubOptimalLatencyP99Us() { return mixedWorkloadSubOptimalLatencyP99Us; }
    public void setMixedWorkloadSubOptimalLatencyP99Us(double mixedWorkloadSubOptimalLatencyP99Us) { this.mixedWorkloadSubOptimalLatencyP99Us = mixedWorkloadSubOptimalLatencyP99Us; }
    
    public int getTotalWorkloads() { return totalWorkloads; }
    public void setTotalWorkloads(int totalWorkloads) { this.totalWorkloads = totalWorkloads; }
    
    public String getAnalysisTimestamp() { return analysisTimestamp; }
    public void setAnalysisTimestamp(String analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }
}