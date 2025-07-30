package com.jshook.analysis.workload;

/**
 * Key metrics extracted from fio.json files
 */
public class FioMetrics {
    private final double throughputMBps;
    private final double latencyP99Ms;
    private final double iops;
    
    public FioMetrics(double throughputMBps, double latencyP99Ms, double iops) {
        this.throughputMBps = throughputMBps;
        this.latencyP99Ms = latencyP99Ms;
        this.iops = iops;
    }
    
    public double getThroughputMBps() { return throughputMBps; }
    public double getLatencyP99Ms() { return latencyP99Ms; }
    public double getIops() { return iops; }
    
    @Override
    public String toString() {
        return String.format("FioMetrics{throughput=%.2f MB/s, p99=%.2f ms, iops=%.0f}", 
                throughputMBps, latencyP99Ms, iops);
    }
}