package com.jshook.analysis;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the automatic disqualification of systems with missing or zero metrics
 */
public class DisqualificationTest {
    
    @Test
    public void testSystemsWithZeroMetricsAreDisqualified() {
        // Create a scoring function that requires throughput and latency
        ScoringFunction.ScoringConfiguration config = new ScoringFunction.ScoringConfiguration();
        config.setDescription("Test disqualification with zero metrics");
        
        ScoringFunction.ScoringComponent throughput = new ScoringFunction.ScoringComponent("randread_throughput_mbps", 0.6, false);
        config.addComponent(throughput);
        
        ScoringFunction.ScoringComponent latency = new ScoringFunction.ScoringComponent("randread_latency_p99_us", 0.4, true);
        config.addComponent(latency);
        
        ScoringFunction scoringFunction = new ScoringFunction(config);
        
        // Create test systems - one with zero throughput (should be disqualified)
        SystemMetrics qualifiedSystem = new SystemMetrics();
        qualifiedSystem.setSystemName("qualified_system");
        qualifiedSystem.setRandreadThroughputMBps(1500.0); // Non-zero
        qualifiedSystem.setRandreadLatencyP99Us(200.0);    // Non-zero
        
        SystemMetrics disqualifiedSystem = new SystemMetrics();
        disqualifiedSystem.setSystemName("disqualified_system");
        disqualifiedSystem.setRandreadThroughputMBps(0.0);   // Zero - should disqualify
        disqualifiedSystem.setRandreadLatencyP99Us(100.0);   // Non-zero
        
        List<SystemMetrics> systems = Arrays.asList(qualifiedSystem, disqualifiedSystem);
        
        // Score the systems
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(systems);
        
        // Should have 2 results
        assertEquals(2, results.size());
        
        // Find results by system name
        ScoringFunction.ScoringResult qualifiedResult = results.stream()
            .filter(r -> r.getSystemName().equals("qualified_system"))
            .findFirst()
            .orElse(null);
            
        ScoringFunction.ScoringResult disqualifiedResult = results.stream()
            .filter(r -> r.getSystemName().equals("disqualified_system"))
            .findFirst()
            .orElse(null);
        
        assertNotNull(qualifiedResult);
        assertNotNull(disqualifiedResult);
        
        // Qualified system should have non-zero score
        assertTrue(qualifiedResult.getTotalScore() > 0.0, 
            "Qualified system should have non-zero score, got: " + qualifiedResult.getTotalScore());
        
        // Disqualified system should have zero score
        assertEquals(0.0, disqualifiedResult.getTotalScore(), 
            "Disqualified system should have zero score, got: " + disqualifiedResult.getTotalScore());
        
        // Disqualified system should have explanation mentioning disqualification
        String explanation = disqualifiedResult.getExplanation();
        assertNotNull(explanation);
        assertTrue(explanation.contains("DISQUALIFIED"), 
            "Disqualified system explanation should mention disqualification");
        assertTrue(explanation.contains("randread_throughput_mbps"), 
            "Disqualified system explanation should mention the zero metric");
    }
    
    @Test
    public void testSystemsWithAllZeroMetricsAreDisqualified() {
        // Create a scoring function that requires multiple metrics
        ScoringFunction.ScoringConfiguration config = new ScoringFunction.ScoringConfiguration();
        config.setDescription("Test disqualification with all zero metrics");
        
        config.addComponent(new ScoringFunction.ScoringComponent("randread_throughput_mbps", 0.5, false));
        config.addComponent(new ScoringFunction.ScoringComponent("randread_latency_p99_us", 0.5, true));
        
        ScoringFunction scoringFunction = new ScoringFunction(config);
        
        // Create system with all zero metrics
        SystemMetrics allZeroSystem = new SystemMetrics();
        allZeroSystem.setSystemName("all_zero_system");
        allZeroSystem.setRandreadThroughputMBps(0.0);
        allZeroSystem.setRandreadLatencyP99Us(0.0);
        
        List<SystemMetrics> systems = Arrays.asList(allZeroSystem);
        
        // Score the systems
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(systems);
        
        assertEquals(1, results.size());
        ScoringFunction.ScoringResult result = results.get(0);
        
        // Should be disqualified with zero score
        assertEquals(0.0, result.getTotalScore());
        assertTrue(result.getExplanation().contains("DISQUALIFIED"));
    }
    
    @Test
    public void testQualifiedSystemsAreNotDisqualified() {
        // Create a simple scoring function with just one metric to avoid edge cases
        ScoringFunction.ScoringConfiguration config = new ScoringFunction.ScoringConfiguration();
        config.setDescription("Test qualified systems are not disqualified");
        
        // Use only throughput to keep it simple
        ScoringFunction.ScoringComponent throughput = new ScoringFunction.ScoringComponent("randread_throughput_mbps", 1.0, false);
        config.addComponent(throughput);
        
        ScoringFunction scoringFunction = new ScoringFunction(config);
        
        // Create two qualified systems with different throughput values
        SystemMetrics system1 = new SystemMetrics();
        system1.setSystemName("system1");
        system1.setRandreadThroughputMBps(2000.0);  // Higher throughput
        
        SystemMetrics system2 = new SystemMetrics();
        system2.setSystemName("system2");
        system2.setRandreadThroughputMBps(1000.0);  // Lower throughput
        
        List<SystemMetrics> systems = Arrays.asList(system1, system2);
        
        // Score the systems
        List<ScoringFunction.ScoringResult> results = scoringFunction.scoreAndRankSystems(systems);
        
        assertEquals(2, results.size());
        
        // Both should not be marked as disqualified (even if one scores 0.0 due to normalization)
        for (ScoringFunction.ScoringResult result : results) {
            assertFalse(result.getExplanation().contains("DISQUALIFIED"), 
                "Qualified system should not be marked as disqualified: " + result.getExplanation());
        }
        
        // At least one system should have a positive score (the better performer)
        boolean hasPositiveScore = results.stream().anyMatch(r -> r.getTotalScore() > 0.0);
        assertTrue(hasPositiveScore, "At least one qualified system should have a positive score");
        
        // System1 should rank better than system2 (higher throughput)
        ScoringFunction.ScoringResult system1Result = results.stream()
            .filter(r -> r.getSystemName().equals("system1"))
            .findFirst().orElse(null);
        ScoringFunction.ScoringResult system2Result = results.stream()
            .filter(r -> r.getSystemName().equals("system2"))
            .findFirst().orElse(null);
            
        assertNotNull(system1Result);
        assertNotNull(system2Result);
        assertTrue(system1Result.getTotalScore() >= system2Result.getTotalScore(), 
            "System1 (higher throughput) should score >= system2");
    }
}