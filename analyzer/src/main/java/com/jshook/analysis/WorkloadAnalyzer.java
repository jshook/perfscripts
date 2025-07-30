package com.jshook.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Analyzes workload files and extracts performance metrics
public class WorkloadAnalyzer {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /// Pattern to parse workload filenames
    private static final Pattern WORKLOAD_PATTERN = 
        Pattern.compile("(\\w+)-(\\d+)-(\\w+)\\.fio\\.json");
    
    /// Analyzes all workload files in a system directory
    public SystemAnalysis analyzeSystem(Path systemDir, List<Path> workloadFiles) throws IOException {
        List<WorkloadResult> results = new ArrayList<>();
        
        for (Path workloadFile : workloadFiles) {
            try {
                WorkloadResult result = parseWorkloadFile(workloadFile);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                System.err.println("Error parsing " + workloadFile + ": " + e.getMessage());
            }
        }
        
        return performSystemAnalysis(results);
    }
    
    /// Parses a single workload file
    private WorkloadResult parseWorkloadFile(Path workloadFile) throws IOException {
        String filename = workloadFile.getFileName().toString();
        Matcher matcher = WORKLOAD_PATTERN.matcher(filename);
        
        if (!matcher.find()) {
            System.err.println("Invalid workload filename format: " + filename);
            return null;
        }
        
        String workloadType = matcher.group(1);  // randread, seqread, seqwrite, mixed
        String testId = matcher.group(2);        // 001, 007, 100, etc.
        String parameter = matcher.group(3);     // 1k, 64k, 32g, 1to4k_10Mseq, etc.
        
        FioResult fioResult = objectMapper.readValue(workloadFile.toFile(), FioResult.class);
        
        return new WorkloadResult(filename, workloadType, testId, parameter, fioResult);
    }
    
    /// Performs comprehensive system analysis
    private SystemAnalysis performSystemAnalysis(List<WorkloadResult> results) {
        // Step 1: Collect all randread results for detailed reporting
        List<WorkloadResult> allRandreadResults = results.stream()
            .filter(r -> "randread".equals(r.getWorkloadType()))
            .filter(r -> r.getFioResult().getJobs() != null && !r.getFioResult().getJobs().isEmpty())
            .filter(r -> r.getFioResult().getJobs().get(0).getRead() != null)
            .sorted(Comparator.comparing(r -> r.getFioResult().getJobs().get(0).getRead().getBandwidth(), Comparator.reverseOrder()))
            .collect(Collectors.toList());
        
        // Step 2: Determine optimal blocksize from randread workloads
        WorkloadResult optimalRandread = findOptimalRandreadBlocksize(results);
        
        // Step 3: Find matching mixed workload series
        List<WorkloadResult> matchingMixedSeries = findMatchingMixedWorkloads(results, optimalRandread);
        
        // Step 4: Perform knee-point analysis on mixed workloads
        KneePointAnalysis kneePointAnalysis = performKneePointAnalysis(matchingMixedSeries);
        
        return new SystemAnalysis(optimalRandread, allRandreadResults, matchingMixedSeries, kneePointAnalysis);
    }
    
    /// Finds the randread workload with highest throughput
    private WorkloadResult findOptimalRandreadBlocksize(List<WorkloadResult> results) {
        return results.stream()
            .filter(r -> "randread".equals(r.getWorkloadType()))
            .filter(r -> r.getFioResult().getJobs() != null && !r.getFioResult().getJobs().isEmpty())
            .filter(r -> r.getFioResult().getJobs().get(0).getRead() != null)
            .max(Comparator.comparing(r -> r.getFioResult().getJobs().get(0).getRead().getBandwidth()))
            .orElse(null);
    }
    
    /// Finds mixed workloads with closest average blocksize to optimal randread
    private List<WorkloadResult> findMatchingMixedWorkloads(List<WorkloadResult> results, WorkloadResult optimalRandread) {
        if (optimalRandread == null) {
            return new ArrayList<>();
        }
        
        // Extract blocksize from optimal randread parameter (e.g., "1k", "64k")  
        String optimalBlocksize = optimalRandread.getParameter();
        double optimalBlocksizeBytes = parseBlocksize(optimalBlocksize);
        
        // Find mixed workload series with closest average blocksize
        Map<String, List<WorkloadResult>> mixedSeries = results.stream()
            .filter(r -> "mixed".equals(r.getWorkloadType()))
            .collect(Collectors.groupingBy(r -> r.getTestId().substring(0, 1))); // Group by series (3xx)
        
        String bestSeries = null;
        double closestDifference = Double.MAX_VALUE;
        
        for (Map.Entry<String, List<WorkloadResult>> entry : mixedSeries.entrySet()) {
            String seriesPrefix = entry.getKey();
            List<WorkloadResult> seriesWorkloads = entry.getValue();
            
            // Calculate average blocksize for this series
            double avgBlocksize = calculateAverageBlocksize(seriesWorkloads);
            double difference = Math.abs(avgBlocksize - optimalBlocksizeBytes);
            
            if (difference < closestDifference) {
                closestDifference = difference;
                bestSeries = seriesPrefix;
            }
        }
        
        return bestSeries != null ? mixedSeries.get(bestSeries) : new ArrayList<>();
    }
    
    /// Parses blocksize string to bytes (e.g., "1k" -> 1024, "64k" -> 65536)
    private double parseBlocksize(String blocksize) {
        try {
            // Handle range patterns first
            if (blocksize.matches("\\d+to\\d+k.*")) {
                // Handle range like "1to4k" - use average
                Pattern rangePattern = Pattern.compile("(\\d+)to(\\d+)k");
                Matcher matcher = rangePattern.matcher(blocksize);
                if (matcher.find()) {
                    double start = Double.parseDouble(matcher.group(1)) * 1024;
                    double end = Double.parseDouble(matcher.group(2)) * 1024;
                    return (start + end) / 2.0;
                }
            } else if (blocksize.matches("\\d+[kK]to\\d+[mM]")) {
                // Handle range like "512Kto1M" - use average
                Pattern rangePattern = Pattern.compile("(\\d+)[kK]to(\\d+)[mM]");
                Matcher matcher = rangePattern.matcher(blocksize);
                if (matcher.find()) {
                    double start = Double.parseDouble(matcher.group(1)) * 1024;
                    double end = Double.parseDouble(matcher.group(2)) * 1024 * 1024;
                    return (start + end) / 2.0;
                }
            } else if (blocksize.matches("\\d+to\\d+")) {
                // Handle cases like "1to4" without 'k' suffix
                Pattern rangePattern = Pattern.compile("(\\d+)to(\\d+)");
                Matcher matcher = rangePattern.matcher(blocksize);
                if (matcher.find()) {
                    double start = Double.parseDouble(matcher.group(1));
                    double end = Double.parseDouble(matcher.group(2));
                    return (start + end) / 2.0;
                }
            } else if (blocksize.endsWith("k") || blocksize.endsWith("K")) {
                String numStr = blocksize.substring(0, blocksize.length() - 1);
                return Double.parseDouble(numStr) * 1024;
            } else if (blocksize.endsWith("M") || blocksize.endsWith("m")) {
                String numStr = blocksize.substring(0, blocksize.length() - 1);
                return Double.parseDouble(numStr) * 1024 * 1024;
            } else {
                // Try to parse as raw number
                return Double.parseDouble(blocksize);
            }
        } catch (NumberFormatException e) {
            System.err.println("Unable to parse blocksize: " + blocksize);
            return 0.0;
        }
        return 0.0; // Fallback return
    }
    
    /// Calculates average blocksize for a series of mixed workloads
    private double calculateAverageBlocksize(List<WorkloadResult> workloads) {
        return workloads.stream()
            .mapToDouble(w -> parseBlocksize(w.getParameter().split("_")[0])) // Extract blocksize part
            .average()
            .orElse(0.0);
    }
    
    /// Performs knee-point analysis on mixed workload series
    private KneePointAnalysis performKneePointAnalysis(List<WorkloadResult> mixedWorkloads) {
        if (mixedWorkloads.size() < 2) {
            return new KneePointAnalysis(null, null, "Insufficient mixed workload data");
        }
        
        // Sort by streaming limit (extracted from parameter like "1to4k_10Mseq")
        List<WorkloadResult> sortedWorkloads = mixedWorkloads.stream()
            .sorted(Comparator.comparing(this::extractStreamingLimit))
            .collect(Collectors.toList());
        
        // Find knee point - where p99 latency increases dramatically
        WorkloadResult kneePoint = findKneePoint(sortedWorkloads);
        
        if (kneePoint == null) {
            return new KneePointAnalysis(null, null, "No clear knee point found");
        }
        
        // Find optimal (before knee) and sub-optimal (at knee) workloads
        int kneeIndex = sortedWorkloads.indexOf(kneePoint);
        WorkloadResult optimalMixed = kneeIndex > 0 ? sortedWorkloads.get(kneeIndex - 1) : null;
        WorkloadResult subOptimalMixed = kneePoint;
        
        return new KneePointAnalysis(optimalMixed, subOptimalMixed, "Knee point analysis complete");
    }
    
    /// Extracts streaming limit from parameter string (e.g., "1to4k_10Mseq" -> 10)
    private double extractStreamingLimit(WorkloadResult workload) {
        String param = workload.getParameter();
        Pattern pattern = Pattern.compile("_(\\d+)Mseq");
        Matcher matcher = pattern.matcher(param);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return Double.MAX_VALUE; // "uncapped" goes to end
    }
    
    /// Finds knee point where p99 latency increases dramatically
    private WorkloadResult findKneePoint(List<WorkloadResult> sortedWorkloads) {
        if (sortedWorkloads.size() < 3) return null;
        
        double maxIncrease = 0.0;
        WorkloadResult kneePoint = null;
        
        for (int i = 1; i < sortedWorkloads.size(); i++) {
            WorkloadResult prev = sortedWorkloads.get(i - 1);
            WorkloadResult curr = sortedWorkloads.get(i);
            
            double prevP99 = getP99Latency(prev);
            double currP99 = getP99Latency(curr);
            
            if (prevP99 > 0 && currP99 > 0) {
                double increase = (currP99 - prevP99) / prevP99; // Percentage increase
                if (increase > maxIncrease && increase > 0.2) { // 20% threshold
                    maxIncrease = increase;
                    kneePoint = curr;
                }
            }
        }
        
        return kneePoint;
    }
    
    /// Extracts P99 latency from workload result
    private double getP99Latency(WorkloadResult workload) {
        try {
            return workload.getFioResult().getJobs().get(0).getRead().getCompletionLatency().getP99();
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /// Result classes
    public static class WorkloadResult {
        private final String filename;
        private final String workloadType;
        private final String testId;
        private final String parameter;
        private final FioResult fioResult;
        
        public WorkloadResult(String filename, String workloadType, String testId, String parameter, FioResult fioResult) {
            this.filename = filename;
            this.workloadType = workloadType;
            this.testId = testId;
            this.parameter = parameter;
            this.fioResult = fioResult;
        }
        
        public String getFilename() { return filename; }
        public String getWorkloadType() { return workloadType; }
        public String getTestId() { return testId; }
        public String getParameter() { return parameter; }
        public FioResult getFioResult() { return fioResult; }
    }
    
    public static class SystemAnalysis {
        private final WorkloadResult optimalRandread;
        private final List<WorkloadResult> allRandreadResults;
        private final List<WorkloadResult> matchingMixedSeries;
        private final KneePointAnalysis kneePointAnalysis;
        
        public SystemAnalysis(WorkloadResult optimalRandread, List<WorkloadResult> allRandreadResults, List<WorkloadResult> matchingMixedSeries, KneePointAnalysis kneePointAnalysis) {
            this.optimalRandread = optimalRandread;
            this.allRandreadResults = allRandreadResults;
            this.matchingMixedSeries = matchingMixedSeries;
            this.kneePointAnalysis = kneePointAnalysis;
        }
        
        public WorkloadResult getOptimalRandread() { return optimalRandread; }
        public List<WorkloadResult> getAllRandreadResults() { return allRandreadResults; }
        public List<WorkloadResult> getMatchingMixedSeries() { return matchingMixedSeries; }
        public KneePointAnalysis getKneePointAnalysis() { return kneePointAnalysis; }
    }
    
    public static class KneePointAnalysis {
        private final WorkloadResult optimalMixed;
        private final WorkloadResult subOptimalMixed;
        private final String message;
        
        public KneePointAnalysis(WorkloadResult optimalMixed, WorkloadResult subOptimalMixed, String message) {
            this.optimalMixed = optimalMixed;
            this.subOptimalMixed = subOptimalMixed;
            this.message = message;
        }
        
        public WorkloadResult getOptimalMixed() { return optimalMixed; }
        public WorkloadResult getSubOptimalMixed() { return subOptimalMixed; }
        public String getMessage() { return message; }
    }
}