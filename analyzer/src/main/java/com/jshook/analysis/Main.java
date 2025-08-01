package com.jshook.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Main application entry point for the Report Analysis tool
 */
public class Main {
    
    public static void main(String[] args) {
        try {
            String reportDir = null;
            boolean updateMode = false;
            Set<String> rankingFunctions = new LinkedHashSet<>();
            
            // Parse command line arguments
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--report-dir":
                        if (i + 1 < args.length) {
                            reportDir = args[++i];
                        } else {
                            System.err.println("Error: --report-dir requires a directory name");
                            System.exit(1);
                        }
                        break;
                    case "-U":
                        updateMode = true;
                        break;
                    case "--ranking-functions":
                        if (i + 1 < args.length) {
                            String functionArg = args[++i];
                            // Support comma-separated values
                            String[] functions = functionArg.split(",");
                            for (String function : functions) {
                                String trimmed = function.trim();
                                if (!trimmed.isEmpty()) {
                                    rankingFunctions.add(trimmed);
                                }
                            }
                        } else {
                            System.err.println("Error: --ranking-functions requires one or more ranking function names");
                            System.exit(1);
                        }
                        break;
                    case "--help":
                    case "-h":
                        printUsage();
                        System.exit(0);
                        break;
                    default:
                        System.err.println("Unknown option: " + args[i]);
                        printUsage();
                        System.exit(1);
                }
            }
            
            ReportAnalyzer analyzer = new ReportAnalyzer();
            if (!rankingFunctions.isEmpty()) {
                analyzer.setRankingFunctions(rankingFunctions);
                System.out.println("Using ranking functions: " + String.join(", ", rankingFunctions));
            }
            
            System.out.println("Starting Cross-System Analysis...");
            
            Path reportPath = analyzer.executeAnalysis(reportDir, updateMode);
            
            System.out.println("Analysis completed successfully!");
            System.out.println("Report directory: " + reportPath);
            
            AnalysisManifest manifest = analyzer.enumerateResults();
            System.out.println("Found " + manifest.getSystemProfiles().size() + " system profiles:");
            
            for (String systemProfile : manifest.getSystemProfiles()) {
                Map<String, Path> systems = manifest.getSystemsForProfile(systemProfile);
                System.out.println("  - " + systemProfile + " (" + systems.size() + " systems)");
                for (String systemName : systems.keySet()) {
                    System.out.println("    - " + systemName);
                }
            }
            
            System.out.println("Total systems with workload files: " + manifest.getTotalDirectories());
            
        } catch (IOException e) {
            System.err.println("Error during analysis: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: java com.jshook.analysis.Main [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --report-dir DIR        Specify report directory name");
        System.out.println("  -U                      Update mode - allow overwriting existing report");
        System.out.println("  --ranking-functions NAME Name(s) of ranking function(s) from ranking-functions.json");
        System.out.println("                           Supports comma-separated values and multiple occurrences");
        System.out.println("                           Examples: --ranking-functions default,throughput-oriented");
        System.out.println("                                    --ranking-functions realtime --ranking-functions balanced");
        System.out.println("  -h, --help              Show this help message");
        System.out.println();
        System.out.println("Cross-System Analysis tool for comparing perfscripts results.");
    }
}