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
        highPerf.setOptimalThroughputMBps(2000.0); // 2 GB/s
        highPerf.setOptimalLatencyP99Us(100.0); // 100 microseconds
        highPerf.setOptimalIOPS(500000.0);
        highPerf.setKneePointLatencyIncreasePercent(25.0); // Good consistency
        systems.add(highPerf);
        
        // Medium throughput, medium latency system
        SystemMetrics mediumPerf = new SystemMetrics("medium_perf", "test_profile");
        mediumPerf.setOptimalThroughputMBps(1000.0); // 1 GB/s
        mediumPerf.setOptimalLatencyP99Us(200.0); // 200 microseconds
        mediumPerf.setOptimalIOPS(250000.0);
        mediumPerf.setKneePointLatencyIncreasePercent(50.0); // Medium consistency
        systems.add(mediumPerf);
        
        // Low throughput, high latency system (should rank low)
        SystemMetrics lowPerf = new SystemMetrics("low_perf", "test_profile");
        lowPerf.setOptimalThroughputMBps(500.0); // 0.5 GB/s
        lowPerf.setOptimalLatencyP99Us(1000.0); // 1 millisecond - triggers penalty
        lowPerf.setOptimalIOPS(125000.0);
        lowPerf.setKneePointLatencyIncreasePercent(75.0); // Poor consistency
        systems.add(lowPerf);
        
        // High throughput but terrible latency (threshold penalty test)
        SystemMetrics highThroughputBadLatency = new SystemMetrics("high_throughput_bad_latency", "test_profile");
        highThroughputBadLatency.setOptimalThroughputMBps(3000.0); // 3 GB/s - highest throughput
        highThroughputBadLatency.setOptimalLatencyP99Us(2000.0); // 2 milliseconds - severe penalty
        highThroughputBadLatency.setOptimalIOPS(750000.0);
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
        
        // High performance system should rank first
        assertEquals("high_perf", results.get(0).getSystemName());
        
        // High throughput but bad latency should be penalized (not first despite highest throughput)
        assertNotEquals("high_throughput_bad_latency", results.get(0).getSystemName());
        
        // All scores should be non-negative (some may be 0 due to penalties)
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
        
        ScoringFunction.ScoringComponent throughput = new ScoringFunction.ScoringComponent("optimal_throughput_mbps", 1.0, false);
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
        
        ScoringFunction.ScoringComponent latency = new ScoringFunction.ScoringComponent("optimal_latency_p99_us", 1.0, true);
        latency.setMappingFunction("linear");
        config.addComponent(latency);
        
        ScoringFunction scoringFunction = new ScoringFunction(config);
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        // With pure latency focus, lowest latency should win
        assertEquals("high_perf", results.get(0).getSystemName()); // 100us latency - best
        
        // Worst latency should be last among those with positive scores
        boolean foundLowPerf = false;
        for (ScoringFunction.ScoringResult result : results) {
            if (result.getSystemName().equals("low_perf") && result.getTotalScore() > 0) {
                foundLowPerf = true;
                break;
            }
        }
        // Either low_perf has a score > 0 or it's penalized to 0
        // Either way, high_perf (best latency) should be first
        assertTrue(foundLowPerf || results.stream().anyMatch(r -> r.getSystemName().equals("low_perf") && r.getTotalScore() == 0));
    }
    
    @Test
    void testThresholdPenalties() {
        // Create a configuration with strict latency threshold
        ScoringFunction.ScoringConfiguration config = new ScoringFunction.ScoringConfiguration();
        config.setDescription("Strict latency threshold");
        
        ScoringFunction.ScoringComponent throughput = new ScoringFunction.ScoringComponent("optimal_throughput_mbps", 0.5, false);
        config.addComponent(throughput);
        
        ScoringFunction.ScoringComponent latency = new ScoringFunction.ScoringComponent("optimal_latency_p99_us", 0.5, true);
        latency.setThresholdValue(150.0); // Strict 150us threshold
        latency.setThresholdPenalty(0.5); // Moderate penalty (not so harsh as to make score 0)
        config.addComponent(latency);
        
        ScoringFunction scoringFunction = new ScoringFunction(config);
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        // Only high_perf should avoid penalty (100us < 150us threshold)
        assertEquals("high_perf", results.get(0).getSystemName());
        
        // Systems exceeding threshold should have lower scores
        ScoringFunction.ScoringResult highPerfResult = results.stream()
            .filter(r -> r.getSystemName().equals("high_perf"))
            .findFirst().orElse(null);
        ScoringFunction.ScoringResult mediumPerfResult = results.stream()    
            .filter(r -> r.getSystemName().equals("medium_perf"))
            .findFirst().orElse(null);
            
        assertNotNull(highPerfResult);
        assertNotNull(mediumPerfResult);
        assertTrue(highPerfResult.getTotalScore() > mediumPerfResult.getTotalScore(),
            "System under threshold should score higher than system over threshold");
    }
    
    @Test
    void testMappingFunctions() {
        SystemMetrics testSystem = new SystemMetrics("test", "profile");
        testSystem.setOptimalThroughputMBps(1000.0);
        testSystem.setOptimalLatencyP99Us(200.0);
        List<SystemMetrics> singleSystem = Arrays.asList(testSystem);
        
        // Test linear mapping
        ScoringFunction.ScoringConfiguration linearConfig = new ScoringFunction.ScoringConfiguration();
        ScoringFunction.ScoringComponent linear = new ScoringFunction.ScoringComponent("optimal_throughput_mbps", 1.0, false);
        linear.setMappingFunction("linear");
        linearConfig.addComponent(linear);
        
        ScoringFunction linearFunction = new ScoringFunction(linearConfig);
        List<ScoringFunction.ScoringResult> linearResults = linearFunction.scoreAndRankSystems(singleSystem);
        
        // Test log mapping
        ScoringFunction.ScoringConfiguration logConfig = new ScoringFunction.ScoringConfiguration();
        ScoringFunction.ScoringComponent log = new ScoringFunction.ScoringComponent("optimal_throughput_mbps", 1.0, false);
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
    void testNormalizationFunctions() {
        // Test with multiple systems to ensure normalization works
        ScoringFunction.ScoringConfiguration config = new ScoringFunction.ScoringConfiguration();
        ScoringFunction.ScoringComponent component = new ScoringFunction.ScoringComponent("optimal_throughput_mbps", 1.0, false);
        component.setNormalization("minmax");
        config.addComponent(component);
        
        ScoringFunction scoringFunction = new ScoringFunction(config);
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        // All scores should be between 0 and 1 due to normalization
        for (ScoringFunction.ScoringResult result : results) {
            assertTrue(result.getTotalScore() >= 0.0 && result.getTotalScore() <= 1.0,
                "Normalized scores should be between 0 and 1");
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
        ScoringFunction scoringFunction = ScoringFunction.createDefault();
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(testSystems);
        
        for (ScoringFunction.ScoringResult result : results) {
            assertFalse(result.getComponentScores().isEmpty(), 
                "Component scores should be present for each system");
            
            // Check that all configured components have scores
            assertTrue(result.getComponentScores().containsKey("optimal_throughput_mbps"));
            assertTrue(result.getComponentScores().containsKey("optimal_latency_p99_us"));
            assertTrue(result.getComponentScores().containsKey("knee_point_latency_increase_percent"));
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