package com.jshook.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/// FIO JSON result structure for parsing performance test results
@JsonIgnoreProperties(ignoreUnknown = true)
public class FioResult {
    
    @JsonProperty("fio version")
    private String fioVersion;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("jobs")
    private List<FioJob> jobs;
    
    // Getters
    public String getFioVersion() { return fioVersion; }
    public long getTimestamp() { return timestamp; }
    public List<FioJob> getJobs() { return jobs; }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FioJob {
        
        @JsonProperty("jobname") 
        private String jobname;
        
        @JsonProperty("read")
        private FioMetrics read;
        
        @JsonProperty("write") 
        private FioMetrics write;
        
        // Getters
        public String getJobname() { return jobname; }
        public FioMetrics getRead() { return read; }
        public FioMetrics getWrite() { return write; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FioMetrics {
        
        @JsonProperty("io_bytes")
        private long ioBytes;
        
        @JsonProperty("bw")
        private double bandwidth; // KB/s
        
        @JsonProperty("iops")
        private double iops;
        
        @JsonProperty("runtime")
        private long runtime; // milliseconds
        
        @JsonProperty("clat_ns")
        private LatencyStats completionLatency;
        
        // Getters
        public long getIoBytes() { return ioBytes; }
        public double getBandwidth() { return bandwidth; }
        public double getIops() { return iops; }
        public long getRuntime() { return runtime; }
        public LatencyStats getCompletionLatency() { return completionLatency; }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LatencyStats {
        
        @JsonProperty("mean")
        private double mean; // nanoseconds
        
        @JsonProperty("percentile")
        private Map<String, Double> percentiles;
        
        // Getters  
        public double getMean() { return mean; }
        public Map<String, Double> getPercentiles() { return percentiles; }
        
        /// Get specific percentile value (e.g., "99.000000" for p99)
        public double getPercentile(String percentile) {
            return percentiles != null ? percentiles.getOrDefault(percentile, 0.0) : 0.0;
        }
        
        /// Get p99 latency in nanoseconds
        public double getP99() {
            return getPercentile("99.000000");
        }
        
        /// Get p95 latency in nanoseconds
        public double getP95() {
            return getPercentile("95.000000");
        }
        
        /// Get p50 latency in nanoseconds
        public double getP50() {
            return getPercentile("50.000000");
        }
    }
}