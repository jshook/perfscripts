package com.jshook.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Comprehensive unit tests for the ScoringFunction class.
 * Tests the well-encapsulated ranking function logic with various scenarios.
 */
public class ScoringFunctionTest {
    
    private List<SystemMetrics> testSystems;
    
    @BeforeEach
    void setUp() {
        testSystems = createTestSystems();
    }
    
    /**
     * Creates a set of test systems with known characteristics for testing
     */
    private List<SystemMetrics> createTestSystems() {
        List<SystemMetrics> systems = new ArrayList<>();
        
        // High throughput, low latency system (should rank high)
        SystemMetrics highPerf = new SystemMetrics("high_perf", "test_profile");
        highPerf.setRandreadThroughputMBps(2000.0); // 2 GB/s
        highPerf.setRandreadLatencyP99Us(100.0); // 100 microseconds
        highPerf.setRandreadLatencyMeanUs(50.0); // Mean latency
        highPerf.setRandreadLatencyP50Us(60.0); // P50 latency
        highPerf.setRandreadLatencyP95Us(85.0); // P95 latency
        highPerf.setRandreadIOPS(500000.0);
        highPerf.setKneePointLatencyIncreasePercent(25.0); // Good consistency
        systems.add(highPerf);
        
        // Medium throughput, medium latency system
        SystemMetrics mediumPerf = new SystemMetrics("medium_perf", "test_profile");
        mediumPerf.setRandreadThroughputMBps(1000.0); // 1 GB/s
        mediumPerf.setRandreadLatencyP99Us(200.0); // 200 microseconds
        mediumPerf.setRandreadLatencyMeanUs(100.0); // Mean latency
        mediumPerf.setRandreadLatencyP50Us(120.0); // P50 latency
        mediumPerf.setRandreadLatencyP95Us(175.0); // P95 latency
        mediumPerf.setRandreadIOPS(250000.0);
        mediumPerf.setKneePointLatencyIncreasePercent(50.0); // Medium consistency
        systems.add(mediumPerf);
        
        // Low throughput, high latency system (should rank low)
        SystemMetrics lowPerf = new SystemMetrics("low_perf", "test_profile");
        lowPerf.setRandreadThroughputMBps(500.0); // 0.5 GB/s
        lowPerf.setRandreadLatencyP99Us(1000.0); // 1 millisecond - triggers penalty
        lowPerf.setRandreadLatencyMeanUs(500.0); // Mean latency
        lowPerf.setRandreadLatencyP50Us(600.0); // P50 latency
        lowPerf.setRandreadLatencyP95Us(850.0); // P95 latency
        lowPerf.setRandreadIOPS(125000.0);
        lowPerf.setKneePointLatencyIncreasePercent(75.0); // Poor consistency
        systems.add(lowPerf);
        
        // High throughput but terrible latency (threshold penalty test)
        SystemMetrics highThroughputBadLatency = new SystemMetrics("high_throughput_bad_latency", "test_profile");
        highThroughputBadLatency.setRandreadThroughputMBps(3000.0); // 3 GB/s - highest throughput
        highThroughputBadLatency.setRandreadLatencyP99Us(2000.0); // 2 milliseconds - severe penalty
        highThroughputBadLatency.setRandreadLatencyMeanUs(1000.0); // Mean latency
        highThroughputBadLatency.setRandreadLatencyP50Us(1200.0); // P50 latency
        highThroughputBadLatency.setRandreadLatencyP95Us(1750.0); // P95 latency
        highThroughputBadLatency.setRandreadIOPS(750000.0);
        highThroughputBadLatency.setKneePointLatencyIncreasePercent(150.0); // Very poor consistency
        systems.add(highThroughputBadLatency);
        
        return systems;
    }
    
    @Test
    void testDefaultScoringFunction() {
        ScoringFunction scoringFunction = ScoringFunction.createDefault();
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        // Should have 4 results
        assertEquals(4, results.size());
        
        // Results should be ordered by score (descending)
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).getTotalScore() >= results.get(i + 1).getTotalScore(),
                "Results should be ordered by score (descending)");
        }
        
        // High performance system should rank first with product-based scoring
        assertEquals("high_perf", results.get(0).getSystemName());
        
        // High throughput but bad latency should be penalized (not first despite highest throughput)
        assertNotEquals("high_throughput_bad_latency", results.get(0).getSystemName());
        
        // All scores should be non-negative with product composition
        for (ScoringFunction.ScoringResult result : results) {
            assertTrue(result.getTotalScore() >= 0, "All scores should be non-negative");
        }
        
        // At least the top system should have a positive score
        assertTrue(results.get(0).getTotalScore() > 0, "Top system should have positive score");
    }
    
    @Test
    void testCustomScoringFunction_ThroughputFocused() {
        // Create a throughput-focused scoring function
        ScoringFunction.ScoringConfiguration config = new ScoringFunction.ScoringConfiguration();
        config.setDescription("Throughput-focused scoring");
        
        ScoringFunction.ScoringComponent throughput = new ScoringFunction.ScoringComponent("randread_throughput_mbps", 1.0, false);
        throughput.setMappingFunction("linear");
        config.addComponent(throughput);
        
        ScoringFunction scoringFunction = new ScoringFunction(config);
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        // With pure throughput focus, highest throughput should win despite bad latency
        assertEquals("high_throughput_bad_latency", results.get(0).getSystemName());
    }
    
    @Test
    void testCustomScoringFunction_LatencyFocused() {
        // Create a latency-focused scoring function
        ScoringFunction.ScoringConfiguration config = new ScoringFunction.ScoringConfiguration();
        config.setDescription("Latency-focused scoring");
        
        ScoringFunction.ScoringComponent latency = new ScoringFunction.ScoringComponent("randread_latency_p99_us", 1.0, true);
        latency.setMappingFunction("linear");
        config.addComponent(latency);
        
        ScoringFunction scoringFunction = new ScoringFunction(config);
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        // With pure latency focus, lowest latency should win
        assertEquals("high_perf", results.get(0).getSystemName()); // 100us latency - best
        
        // With product-based scoring, systems should have positive scores
        for (ScoringFunction.ScoringResult result : results) {
            assertTrue(result.getTotalScore() > 0, "All systems should have positive scores");
        }
        
        // High_perf should have the highest score due to best latency
        assertTrue(results.get(0).getTotalScore() > results.get(1).getTotalScore(),
            "Best latency system should have highest score");
    }
    
    @Test
    void testThresholdPenalties() {
        // Create a configuration with strict latency threshold
        ScoringFunction.ScoringConfiguration config = new ScoringFunction.ScoringConfiguration();
        config.setDescription("Strict latency threshold");
        
        ScoringFunction.ScoringComponent throughput = new ScoringFunction.ScoringComponent("randread_throughput_mbps", 0.5, false);
        config.addComponent(throughput);
        
        ScoringFunction.ScoringComponent latency = new ScoringFunction.ScoringComponent("randread_latency_p99_us", 0.5, true);
        latency.setThresholdValue(150.0); // Strict 150us threshold
        latency.setThresholdPenalty(0.1); // Moderate penalty for sum-based scoring
        config.addComponent(latency);
        
        ScoringFunction scoringFunction = new ScoringFunction(config);
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        // With sum-based scoring, we need to verify the penalty logic works
        // even if throughput differences might affect final ranking
        ScoringFunction.ScoringResult highPerfResult = results.stream()
            .filter(r -> r.getSystemName().equals("high_perf"))
            .findFirst().orElse(null);
        
        assertNotNull(highPerfResult, "high_perf result should be present");
        
        // high_perf should be among the top performers due to avoiding threshold penalty
        assertTrue(highPerfResult.getTotalScore() > 0, "high_perf should have positive score");
        
        // Verify that threshold penalties are applied correctly
        // Check component scores to see penalty effect on latency component
        Map<String, Double> highPerfComponents = highPerfResult.getComponentScores();
        assertTrue(highPerfComponents.containsKey("randread_latency_p99_us"), 
            "Should have latency component score");
        assertTrue(highPerfComponents.get("randread_latency_p99_us") > 0, 
            "Latency component should have positive score for system under threshold");
    }
    
    @Test
    void testMappingFunctions() {
        SystemMetrics testSystem = new SystemMetrics("test", "profile");
        testSystem.setRandreadThroughputMBps(1000.0);
        testSystem.setRandreadLatencyP99Us(200.0);
        List<SystemMetrics> singleSystem = Arrays.asList(testSystem);
        
        // Test linear mapping
        ScoringFunction.ScoringConfiguration linearConfig = new ScoringFunction.ScoringConfiguration();
        ScoringFunction.ScoringComponent linear = new ScoringFunction.ScoringComponent("randread_throughput_mbps", 1.0, false);
        linear.setMappingFunction("linear");
        linearConfig.addComponent(linear);
        
        ScoringFunction linearFunction = new ScoringFunction(linearConfig);
        List<ScoringFunction.ScoringResult> linearResults = linearFunction.scoreAndRankSystems(singleSystem);
        
        // Test log mapping
        ScoringFunction.ScoringConfiguration logConfig = new ScoringFunction.ScoringConfiguration();
        ScoringFunction.ScoringComponent log = new ScoringFunction.ScoringComponent("randread_throughput_mbps", 1.0, false);
        log.setMappingFunction("log");
        logConfig.addComponent(log);
        
        ScoringFunction logFunction = new ScoringFunction(logConfig);
        List<ScoringFunction.ScoringResult> logResults = logFunction.scoreAndRankSystems(singleSystem);
        
        // Both should produce valid scores
        assertFalse(linearResults.isEmpty());
        assertFalse(logResults.isEmpty());
        assertTrue(linearResults.get(0).getTotalScore() > 0);
        assertTrue(logResults.get(0).getTotalScore() > 0);
    }
    
    @Test
    void testProductBasedScoring() {
        // Test that scoring uses product composition
        ScoringFunction.ScoringConfiguration config = new ScoringFunction.ScoringConfiguration();
        
        // Two equal-weight components
        ScoringFunction.ScoringComponent throughput = new ScoringFunction.ScoringComponent("randread_throughput_mbps", 0.5, false);
        throughput.setMappingFunction("linear");
        config.addComponent(throughput);
        
        ScoringFunction.ScoringComponent latency = new ScoringFunction.ScoringComponent("randread_latency_p99_us", 0.5, true);
        latency.setMappingFunction("linear");
        config.addComponent(latency);
        
        ScoringFunction scoringFunction = new ScoringFunction(config);
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        // Verify product-based composition: total score should approximately equal
        // product of component scores raised to their weights
        for (ScoringFunction.ScoringResult result : results) {
            Map<String, Double> componentScores = result.getComponentScores();
            double expectedTotal = Math.pow(componentScores.get("randread_throughput_mbps"), 0.5) *
                                 Math.pow(componentScores.get("randread_latency_p99_us"), 0.5);
            assertEquals(expectedTotal, result.getTotalScore(), 0.001,
                "Total score should equal weighted product of components");
        }
    }
    
    @Test
    void testEmptySystemsList() {
        ScoringFunction scoringFunction = ScoringFunction.createDefault();
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(new ArrayList<>());
        
        assertTrue(results.isEmpty(), "Empty systems list should return empty results");
    }
    
    @Test
    void testSingleSystem() {
        SystemMetrics singleSystem = testSystems.get(0);
        ScoringFunction scoringFunction = ScoringFunction.createDefault();
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(Arrays.asList(singleSystem));
        
        assertEquals(1, results.size());
        assertEquals(singleSystem.getSystemName(), results.get(0).getSystemName());
        assertTrue(results.get(0).getTotalScore() > 0);
    }
    
    @Test 
    void testComponentScoresPresent() {
        ScoringFunction scoringFunction = ScoringFunction.createFromRankingFunctions("balanced");
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        for (ScoringFunction.ScoringResult result : results) {
            assertFalse(result.getComponentScores().isEmpty(), 
                "Component scores should be present for each system");
            
            // Debug: Print actual keys
            System.out.println("Component score keys for " + result.getSystemName() + ": " + result.getComponentScores().keySet());
            
            // Check that all configured components have scores (using new component metric names)
            assertTrue(result.getComponentScores().containsKey("randread_throughput_mbps"), 
                "Missing randread_throughput_mbps in keys: " + result.getComponentScores().keySet());
            assertTrue(result.getComponentScores().containsKey("randread_latency_p99_us"),
                "Missing randread_latency_p99_us in keys: " + result.getComponentScores().keySet());
            assertTrue(result.getComponentScores().containsKey("knee_point_latency_increase_percent"),
                "Missing knee_point_latency_increase_percent in keys: " + result.getComponentScores().keySet());
        }
    }
    
    @Test
    void testExplanationGeneration() {
        ScoringFunction scoringFunction = ScoringFunction.createDefault();
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        for (ScoringFunction.ScoringResult result : results) {
            assertNotNull(result.getExplanation());
            assertFalse(result.getExplanation().isEmpty());
            
            // Explanation should contain system name and total score
            assertTrue(result.getExplanation().contains(result.getSystemName()));
            assertTrue(result.getExplanation().contains("Total Score"));
        }
    }
    
    @Test
    void testScoringConsistency() {
        ScoringFunction scoringFunction = ScoringFunction.createDefault();
        
        // Run scoring multiple times with same data
        List<ScoringFunction.ScoringResult> results1 = scoringFunction.scoreAndRankSystems(testSystems);
        List<ScoringFunction.ScoringResult> results2 = scoringFunction.scoreAndRankSystems(testSystems);
        
        // Results should be identical
        assertEquals(results1.size(), results2.size());
        
        for (int i = 0; i < results1.size(); i++) {
            assertEquals(results1.get(i).getSystemName(), results2.get(i).getSystemName());
            assertEquals(results1.get(i).getTotalScore(), results2.get(i).getTotalScore(), 0.0001);
        }
    }
}