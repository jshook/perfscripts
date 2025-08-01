package com.jshook.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Function;

/**
 * Customizable and composable scoring function for system ranking.
 * Implements a "maximal value" function where higher scores are better.
 * 
 * The scoring function is the product of several components, each based on:
 * - A specific metric available from SystemMetrics
 * - A mapping function to convert into "positive inflective" form
 * - A normalizing function to bring metrics into normalized scale
 * - Possible threshold values and roll-off functions
 */
public class ScoringFunction {
    
    /**
     * Configuration for the scoring function
     */
    public static class ScoringConfiguration {
        @JsonProperty("components")
        private List<ScoringComponent> components = new ArrayList<>();
        
        @JsonProperty("description")
        private String description = "Default scoring function";
        
        public List<ScoringComponent> getComponents() { return components; }
        public void setComponents(List<ScoringComponent> components) { this.components = components; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public void addComponent(ScoringComponent component) {
            components.add(component);
        }
    }
    
    /**
     * Individual component of the scoring function
     */
    public static class ScoringComponent {
        @JsonProperty("metric_name")
        private String metricName;
        
        @JsonProperty("weight")
        private double weight = 1.0;
        
        @JsonProperty("mapping_function")
        private String mappingFunction = "linear"; // linear, log, inverse, threshold
        
        @JsonProperty("normalization")
        private String normalization = "minmax"; // minmax, zscore, none
        
        @JsonProperty("threshold_value")
        private Double thresholdValue;
        
        @JsonProperty("threshold_penalty")
        private double thresholdPenalty = 0.1; // Massive reduction factor when threshold triggered
        
        @JsonProperty("easing_function")
        private String easingFunction = "linear"; // linear, exponential, sigmoid
        
        @JsonProperty("invert_better")
        private boolean invertBetter = false; // true for latency (lower is better)
        
        // Constructors
        public ScoringComponent() {}
        
        public ScoringComponent(String metricName, double weight, boolean invertBetter) {
            this.metricName = metricName;
            this.weight = weight;
            this.invertBetter = invertBetter;
        }
        
        // Getters and setters
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        
        public String getMappingFunction() { return mappingFunction; }
        public void setMappingFunction(String mappingFunction) { this.mappingFunction = mappingFunction; }
        
        public String getNormalization() { return normalization; }
        public void setNormalization(String normalization) { this.normalization = normalization; }
        
        public Double getThresholdValue() { return thresholdValue; }
        public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }
        
        public double getThresholdPenalty() { return thresholdPenalty; }
        public void setThresholdPenalty(double thresholdPenalty) { this.thresholdPenalty = thresholdPenalty; }
        
        public String getEasingFunction() { return easingFunction; }
        public void setEasingFunction(String easingFunction) { this.easingFunction = easingFunction; }
        
        public boolean isInvertBetter() { return invertBetter; }
        public void setInvertBetter(boolean invertBetter) { this.invertBetter = invertBetter; }
    }
    
    /**
     * Result of scoring calculation
     */
    public static class ScoringResult {
        private final String systemName;
        private final double totalScore;
        private final Map<String, Double> componentScores;
        private final String explanation;
        
        public ScoringResult(String systemName, double totalScore, Map<String, Double> componentScores, String explanation) {
            this.systemName = systemName;
            this.totalScore = totalScore;
            this.componentScores = new HashMap<>(componentScores);
            this.explanation = explanation;
        }
        
        public String getSystemName() { return systemName; }
        public double getTotalScore() { return totalScore; }
        public Map<String, Double> getComponentScores() { return componentScores; }
        public String getExplanation() { return explanation; }
    }
    
    private final ScoringConfiguration configuration;
    
    public ScoringFunction(ScoringConfiguration configuration) {
        this.configuration = configuration;
    }
    
    /**
     * Creates a default scoring function using the first function in ranking-functions.json
     * NOTE: This method is deprecated - use createFromRankingFunctions() directly instead
     */
    @Deprecated
    public static ScoringFunction createDefault() {
        return createFromRankingFunctions(getFirstRankingFunction());
    }
    
    /**
     * Creates a scoring function from the ranking-functions.json file
     */
    public static ScoringFunction createFromRankingFunctions(String functionName) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            java.io.InputStream inputStream = null;
            
            // First try current directory, then classpath
            java.nio.file.Path rankingFunctionsPath = java.nio.file.Paths.get("ranking-functions.json");
            if (java.nio.file.Files.exists(rankingFunctionsPath)) {
                System.out.println("Loading ranking-functions.json from: " + rankingFunctionsPath.toAbsolutePath());
                inputStream = java.nio.file.Files.newInputStream(rankingFunctionsPath);
            } else {
                System.out.println("Loading ranking-functions.json from classpath");
                inputStream = ScoringFunction.class.getClassLoader()
                    .getResourceAsStream("ranking-functions.json");
            }
            
            if (inputStream == null) {
                System.err.println("Warning: ranking-functions.json not found, using hardcoded default");
                return createHardcodedDefault();
            }
            
            java.util.Map<String, ScoringConfiguration> rankingFunctions = mapper.readValue(
                inputStream, 
                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, ScoringConfiguration>>() {}
            );
            
            ScoringConfiguration config = rankingFunctions.get(functionName);
            if (config == null) {
                System.err.println("Warning: ranking function '" + functionName + "' not found, using 'default'");
                config = rankingFunctions.get("default");
                if (config == null) {
                    System.err.println("Warning: 'default' ranking function not found, using hardcoded default");
                    return createHardcodedDefault();
                }
            }
            
            return new ScoringFunction(config);
            
        } catch (Exception e) {
            System.err.println("Error loading ranking functions: " + e.getMessage());
            System.err.println("Using hardcoded default ranking function");
            return createHardcodedDefault();
        }
    }
    
    /**
     * Creates a hardcoded default scoring function as fallback
     */
    private static ScoringFunction createHardcodedDefault() {
        ScoringConfiguration config = new ScoringConfiguration();
        config.setDescription("Default balanced scoring: 60% throughput, 30% latency, 10% consistency");
        
        // Throughput component (higher is better)
        ScoringComponent throughput = new ScoringComponent("randread_throughput_mbps", 0.6, false);
        throughput.setMappingFunction("log");
        config.addComponent(throughput);
        
        // Latency component (lower is better)
        ScoringComponent latency = new ScoringComponent("randread_latency_p99_us", 0.3, true);
        latency.setMappingFunction("log");
        latency.setThresholdValue(1000.0); // Penalty for >1ms latency
        latency.setThresholdPenalty(0.5);
        config.addComponent(latency);
        
        // Consistency component - knee point increase (lower is better)
        ScoringComponent consistency = new ScoringComponent("knee_point_latency_increase_percent", 0.1, true);
        consistency.setThresholdValue(50.0); // Penalty for >50% latency increase
        consistency.setThresholdPenalty(0.3);
        config.addComponent(consistency);
        
        return new ScoringFunction(config);
    }
    
    /**
     * Lists available ranking functions from ranking-functions.json
     */
    public static java.util.Set<String> getAvailableRankingFunctions() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            java.io.InputStream inputStream = null;
            
            // First try current directory, then classpath
            java.nio.file.Path rankingFunctionsPath = java.nio.file.Paths.get("ranking-functions.json");
            if (java.nio.file.Files.exists(rankingFunctionsPath)) {
                inputStream = java.nio.file.Files.newInputStream(rankingFunctionsPath);
            } else {
                inputStream = ScoringFunction.class.getClassLoader()
                    .getResourceAsStream("ranking-functions.json");
            }
            
            if (inputStream == null) {
                return java.util.Set.of("default");
            }
            
            java.util.Map<String, ScoringConfiguration> rankingFunctions = mapper.readValue(
                inputStream, 
                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, ScoringConfiguration>>() {}
            );
            
            return rankingFunctions.keySet();
            
        } catch (Exception e) {
            return java.util.Set.of("default");
        }
    }
    
    /**
     * Lists available ranking functions excluding any with 'example' in the name
     */
    public static java.util.Set<String> getNonExampleRankingFunctions() {
        return getAvailableRankingFunctions().stream()
            .filter(name -> !name.toLowerCase().contains("example"))
            .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * Lists all available ranking functions including examples
     */
    public static java.util.Set<String> getAllRankingFunctions() {
        return getAvailableRankingFunctions();
    }
    
    /**
     * Gets the first ranking function name from ranking-functions.json (used as default)
     */
    public static String getFirstRankingFunction() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            java.io.InputStream inputStream = null;
            
            // First try current directory, then classpath
            java.nio.file.Path rankingFunctionsPath = java.nio.file.Paths.get("ranking-functions.json");
            if (java.nio.file.Files.exists(rankingFunctionsPath)) {
                inputStream = java.nio.file.Files.newInputStream(rankingFunctionsPath);
            } else {
                inputStream = ScoringFunction.class.getClassLoader()
                    .getResourceAsStream("ranking-functions.json");
            }
            
            if (inputStream == null) {
                return "default";
            }
            
            // Use LinkedHashMap to preserve order
            java.util.Map<String, ScoringConfiguration> rankingFunctions = mapper.readValue(
                inputStream, 
                new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, ScoringConfiguration>>() {}
            );
            
            return rankingFunctions.keySet().iterator().next();
            
        } catch (Exception e) {
            return "default";
        }
    }
    
    /**
     * Scores a list of systems and returns ranked results
     */
    public List<ScoringResult> scoreAndRankSystems(List<SystemMetrics> systems) {
        if (systems.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Separate qualified and disqualified systems
        List<SystemMetrics> qualifiedSystems = new ArrayList<>();
        List<SystemMetrics> disqualifiedSystems = new ArrayList<>();
        
        for (SystemMetrics system : systems) {
            if (isSystemQualified(system)) {
                qualifiedSystems.add(system);
            } else {
                disqualifiedSystems.add(system);
            }
        }
        
        // Score qualified systems (no normalization needed)
        List<ScoringResult> results = new ArrayList<>();
        for (SystemMetrics system : qualifiedSystems) {
            ScoringResult result = scoreSystem(system, null);
            results.add(result);
        }
        
        // Add disqualified systems with zero scores and explanations
        for (SystemMetrics system : disqualifiedSystems) {
            ScoringResult disqualifiedResult = createDisqualifiedResult(system);
            results.add(disqualifiedResult);
        }
        
        // Sort by total score (descending - higher is better)
        results.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        
        return results;
    }
    
    /**
     * Checks if a system is qualified for ranking (has all required metrics with non-zero values)
     */
    private boolean isSystemQualified(SystemMetrics system) {
        for (ScoringComponent component : configuration.getComponents()) {
            double value = extractMetricValue(system, component.getMetricName());
            if (value == 0.0) {
                return false; // Missing or zero metric disqualifies the system
            }
        }
        return true;
    }
    
    /**
     * Creates a result for a disqualified system with explanation
     */
    private ScoringResult createDisqualifiedResult(SystemMetrics system) {
        Map<String, Double> componentScores = new HashMap<>();
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("DISQUALIFIED - ").append(system.getSystemName()).append(":\\n");
        explanation.append("System disqualified due to missing or zero values for required metrics:\\n");
        
        for (ScoringComponent component : configuration.getComponents()) {
            double value = extractMetricValue(system, component.getMetricName());
            componentScores.put(component.getMetricName(), 0.0); // All component scores are 0
            
            if (value == 0.0) {
                explanation.append(String.format("- %s: MISSING/ZERO (required for ranking)\\n", 
                    component.getMetricName()));
            } else {
                explanation.append(String.format("- %s: %.3f (weight: %.1f)\\n", 
                    component.getMetricName(), value, component.getWeight()));
            }
        }
        
        explanation.append("Total Score: 0.000 (DISQUALIFIED)");
        
        return new ScoringResult(system.getSystemName(), 0.0, componentScores, explanation.toString());
    }
    
    /**
     * Scores a single system
     */
    private ScoringResult scoreSystem(SystemMetrics system, Map<String, MetricStats> metricStats) {
        Map<String, Double> componentScores = new HashMap<>();
        double totalScore = 1.0; // Product starts at 1
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("Scoring breakdown for ").append(system.getSystemName()).append(":\\n");
        
        for (ScoringComponent component : configuration.getComponents()) {
            double rawValue = extractMetricValue(system, component.getMetricName());
            // At this point we know the system is qualified, so all metrics should be non-zero
            
            double componentScore = calculateComponentScore(rawValue, component, null);
            componentScores.put(component.getMetricName(), componentScore);
            totalScore *= Math.pow(componentScore, component.getWeight());
            
            explanation.append(String.format("- %s: %.6f (weight: %.1f, raw: %.1f)\\n", 
                component.getMetricName(), componentScore, component.getWeight(), rawValue));
        }
        
        explanation.append(String.format("Total Score: %.6f", totalScore));
        
        return new ScoringResult(system.getSystemName(), totalScore, componentScores, explanation.toString());
    }
    
    /**
     * Calculates score for a single component
     */
    private double calculateComponentScore(double rawValue, ScoringComponent component, MetricStats stats) {
        // Step 1: Apply mapping function to convert to positive inflective form
        double mappedValue = applyMappingFunction(rawValue, component.getMappingFunction(), component.isInvertBetter());
        
        // Step 2: Apply threshold penalty if configured
        double finalValue = applyThresholdPenalty(rawValue, mappedValue, component);
        
        return finalValue;
    }
    
    /**
     * Applies mapping function to convert metric to positive inflective form
     */
    private double applyMappingFunction(double value, String function, boolean invertBetter) {
        if (invertBetter) {
            // For "lower is better" metrics, invert before applying function
            value = 1.0 / (1.0 + value);
        }
        
        switch (function.toLowerCase()) {
            case "log":
                return Math.log(1.0 + value);
            case "inverse":
                return 1.0 / (1.0 + value);
            case "threshold":
                return value > 0 ? 1.0 : 0.0;
            case "linear":
            default:
                return value;
        }
    }
    
    /**
     * Applies normalization to bring metrics into [0, 1] scale
     */
    private double applyNormalization(double value, String normalization, MetricStats stats) {
        switch (normalization.toLowerCase()) {
            case "minmax":
                if (stats.range == 0) return 0.5; // All values are the same
                return (value - stats.min) / stats.range;
            case "zscore":
                if (stats.stdDev == 0) return 0.5; // All values are the same
                double zscore = (value - stats.mean) / stats.stdDev;
                // Convert z-score to [0, 1] using sigmoid
                return 1.0 / (1.0 + Math.exp(-zscore));
            case "none":
            default:
                return value;
        }
    }
    
    /**
     * Applies threshold penalty if conditions are triggered
     */
    private double applyThresholdPenalty(double rawValue, double mappedValue, ScoringComponent component) {
        if (component.getThresholdValue() == null) {
            return mappedValue;
        }
        
        boolean thresholdTriggered;
        if (component.isInvertBetter()) {
            // For "lower is better", trigger if value exceeds threshold
            thresholdTriggered = rawValue > component.getThresholdValue();
        } else {
            // For "higher is better", trigger if value is below threshold
            thresholdTriggered = rawValue < component.getThresholdValue();
        }
        
        if (thresholdTriggered) {
            return mappedValue * component.getThresholdPenalty();
        }
        
        return mappedValue;
    }
    
    /**
     * Extracts metric values from all systems for normalization
     * Applies mapping function to get values in the same space for normalization
     */
    private Map<String, List<Double>> extractMetricValues(List<SystemMetrics> systems) {
        Map<String, List<Double>> metricValues = new HashMap<>();
        
        for (ScoringComponent component : configuration.getComponents()) {
            List<Double> values = new ArrayList<>();
            for (SystemMetrics system : systems) {
                double rawValue = extractMetricValue(system, component.getMetricName());
                // All systems passed here should be qualified (non-zero values)
                // Apply mapping function to get values in comparable space
                double mappedValue = applyMappingFunction(rawValue, component.getMappingFunction(), component.isInvertBetter());
                values.add(mappedValue);
            }
            metricValues.put(component.getMetricName(), values);
        }
        
        return metricValues;
    }
    
    /**
     * Extracts a specific metric value from SystemMetrics using reflection-like approach
     */
    private double extractMetricValue(SystemMetrics system, String metricName) {
        switch (metricName.toLowerCase()) {
            case "optimal_throughput_mbps":
                return system.getRandreadThroughputMBps();
            case "optimal_throughput_gbps":
                return system.getRandreadThroughputMBps() / 1024.0;
            case "optimal_iops":
                return system.getRandreadIOPS();
            case "optimal_latency_mean_ms":
            case "optimal_latency_mean_us":
                return system.getRandreadLatencyMeanUs();
            case "optimal_latency_p50_ms":
            case "optimal_latency_p50_us":
                return system.getRandreadLatencyP50Us();
            case "optimal_latency_p95_ms":
            case "optimal_latency_p95_us":
                return system.getRandreadLatencyP95Us();
            case "optimal_latency_p99_ms":
            case "optimal_latency_p99_us":
                return system.getRandreadLatencyP99Us();
            case "optimal_latency_p99_p50_ratio":
                return system.getRandreadLatencyP99P50Ratio();
            case "knee_point_latency_increase_percent":
                return system.getKneePointLatencyIncreasePercent();
            // Direct access to component metrics from optimal mixed workload
            case "randread_throughput_mbps":
                return system.getRandreadThroughputMBps();
            case "randread_iops":
                return system.getRandreadIOPS();
            case "randread_latency_mean_ms":
            case "randread_latency_mean_us":
                return system.getRandreadLatencyMeanUs();
            case "randread_latency_p50_ms":
            case "randread_latency_p50_us":
                return system.getRandreadLatencyP50Us();
            case "randread_latency_p95_ms":
            case "randread_latency_p95_us":
                return system.getRandreadLatencyP95Us();
            case "randread_latency_p99_ms":
            case "randread_latency_p99_us":
                return system.getRandreadLatencyP99Us();
            case "randread_latency_p99_p50_ratio":
                return system.getRandreadLatencyP99P50Ratio();
            case "seqread_throughput_mbps":
                return system.getSeqreadThroughputMBps();
            case "seqwrite_throughput_mbps":
                return system.getSeqwriteThroughputMBps();
            case "optimal_stream_limit_mbps":
                return system.getOptimalStreamLimitMBps();
            case "total_workloads":
                return system.getTotalWorkloads();
            default:
                return 0.0;
        }
    }
    
    /**
     * Calculates statistics for normalization
     */
    private Map<String, MetricStats> calculateMetricStats(Map<String, List<Double>> metricValues) {
        Map<String, MetricStats> stats = new HashMap<>();
        
        for (Map.Entry<String, List<Double>> entry : metricValues.entrySet()) {
            List<Double> values = entry.getValue();
            if (values.isEmpty()) continue;
            
            double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0.0);
            double stdDev = Math.sqrt(variance);
            
            stats.put(entry.getKey(), new MetricStats(min, max, mean, stdDev));
        }
        
        return stats;
    }
    
    /**
     * Statistics for a metric
     */
    private static class MetricStats {
        final double min, max, mean, stdDev, range;
        
        MetricStats(double min, double max, double mean, double stdDev) {
            this.min = min;
            this.max = max;
            this.mean = mean;
            this.stdDev = stdDev;
            this.range = max - min;
        }
    }
    
    public ScoringConfiguration getConfiguration() {
        return configuration;
    }
}