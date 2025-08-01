package com.jshook.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * System performance metrics from the optimal mixed workload
 * All metrics are derived from the winning mixed workload result after knee-point analysis
 */
public class SystemMetrics {
    @JsonProperty("system_name")
    private String systemName;
    
    @JsonProperty("system_profile")
    private String systemProfile;
    
    // Random read component metrics from optimal mixed workload
    @JsonProperty("randread_throughput_mbps")
    private double randreadThroughputMBps;
    
    @JsonProperty("randread_iops")
    private double randreadIOPS;
    
    @JsonProperty("randread_latency_mean_ms")
    private double randreadLatencyMeanUs; // Note: field name kept for backward compatibility, but now stores milliseconds
    
    @JsonProperty("randread_latency_p50_ms")
    private double randreadLatencyP50Us; // Note: field name kept for backward compatibility, but now stores milliseconds
    
    @JsonProperty("randread_latency_p95_ms")
    private double randreadLatencyP95Us; // Note: field name kept for backward compatibility, but now stores milliseconds
    
    @JsonProperty("randread_latency_p99_ms")
    private double randreadLatencyP99Us; // Note: field name kept for backward compatibility, but now stores milliseconds
    
    @JsonProperty("randread_latency_p99_p50_ratio")
    private double randreadLatencyP99P50Ratio;
    
    // Sequential read component metrics from optimal mixed workload
    @JsonProperty("seqread_throughput_mbps")
    private double seqreadThroughputMBps;
    
    // Sequential write component metrics from optimal mixed workload
    @JsonProperty("seqwrite_throughput_mbps")
    private double seqwriteThroughputMBps;
    
    // Mixed workload optimal metrics
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
    
    // Analysis metrics
    @JsonProperty("knee_point_latency_increase_percent")
    private double kneePointLatencyIncreasePercent;
    
    @JsonProperty("optimal_stream_limit_mbps")
    private double optimalStreamLimitMBps;
    
    @JsonProperty("optimal_mixed_workload_name")
    private String optimalMixedWorkloadName;
    
    @JsonProperty("optimal_blocksize")
    private String optimalBlocksize;
    
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
    
    public double getRandreadThroughputMBps() { return randreadThroughputMBps; }
    public void setRandreadThroughputMBps(double randreadThroughputMBps) { 
        this.randreadThroughputMBps = randreadThroughputMBps;
    }
    
    public double getRandreadIOPS() { return randreadIOPS; }
    public void setRandreadIOPS(double randreadIOPS) { this.randreadIOPS = randreadIOPS; }
    
    public double getRandreadLatencyMeanUs() { return randreadLatencyMeanUs; }
    public void setRandreadLatencyMeanUs(double randreadLatencyMeanUs) { this.randreadLatencyMeanUs = randreadLatencyMeanUs; }
    
    public double getRandreadLatencyP50Us() { return randreadLatencyP50Us; }
    public void setRandreadLatencyP50Us(double randreadLatencyP50Us) { 
        this.randreadLatencyP50Us = randreadLatencyP50Us; 
        updateLatencyRatio();
    }
    
    public double getRandreadLatencyP95Us() { return randreadLatencyP95Us; }
    public void setRandreadLatencyP95Us(double randreadLatencyP95Us) { this.randreadLatencyP95Us = randreadLatencyP95Us; }
    
    public double getRandreadLatencyP99Us() { return randreadLatencyP99Us; }
    public void setRandreadLatencyP99Us(double randreadLatencyP99Us) { 
        this.randreadLatencyP99Us = randreadLatencyP99Us; 
        updateLatencyRatio();
    }
    
    public double getRandreadLatencyP99P50Ratio() { return randreadLatencyP99P50Ratio; }
    public void setRandreadLatencyP99P50Ratio(double randreadLatencyP99P50Ratio) { this.randreadLatencyP99P50Ratio = randreadLatencyP99P50Ratio; }
    
    private void updateLatencyRatio() {
        if (randreadLatencyP50Us > 0 && randreadLatencyP99Us > 0) {
            this.randreadLatencyP99P50Ratio = randreadLatencyP99Us / randreadLatencyP50Us;
        }
    }
    
    public double getSeqreadThroughputMBps() { return seqreadThroughputMBps; }
    public void setSeqreadThroughputMBps(double seqreadThroughputMBps) { this.seqreadThroughputMBps = seqreadThroughputMBps; }
    
    public double getSeqwriteThroughputMBps() { return seqwriteThroughputMBps; }
    public void setSeqwriteThroughputMBps(double seqwriteThroughputMBps) { this.seqwriteThroughputMBps = seqwriteThroughputMBps; }
    
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
    
    public double getKneePointLatencyIncreasePercent() { return kneePointLatencyIncreasePercent; }
    public void setKneePointLatencyIncreasePercent(double kneePointLatencyIncreasePercent) { this.kneePointLatencyIncreasePercent = kneePointLatencyIncreasePercent; }
    
    public double getOptimalStreamLimitMBps() { return optimalStreamLimitMBps; }
    public void setOptimalStreamLimitMBps(double optimalStreamLimitMBps) { this.optimalStreamLimitMBps = optimalStreamLimitMBps; }
    
    public String getOptimalMixedWorkloadName() { return optimalMixedWorkloadName; }
    public void setOptimalMixedWorkloadName(String optimalMixedWorkloadName) { this.optimalMixedWorkloadName = optimalMixedWorkloadName; }
    
    public String getOptimalBlocksize() { return optimalBlocksize; }
    public void setOptimalBlocksize(String optimalBlocksize) { this.optimalBlocksize = optimalBlocksize; }
    
    public int getTotalWorkloads() { return totalWorkloads; }
    public void setTotalWorkloads(int totalWorkloads) { this.totalWorkloads = totalWorkloads; }
    
    public String getAnalysisTimestamp() { return analysisTimestamp; }
    public void setAnalysisTimestamp(String analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }
}