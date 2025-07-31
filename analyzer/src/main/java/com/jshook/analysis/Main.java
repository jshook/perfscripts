package com.jshook.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Main application entry point for the Report Analysis tool
 */
public class Main {
    
    public static void main(String[] args) {
        try {
            String reportDir = null;
            boolean updateMode = false;
            String rankingFunction = null;
            
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
                    case "--ranking-function":
                        if (i + 1 < args.length) {
                            rankingFunction = args[++i];
                        } else {
                            System.err.println("Error: --ranking-function requires a ranking function name");
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
            if (rankingFunction != null) {
                analyzer.setRankingFunction(rankingFunction);
                System.out.println("Using ranking function: " + rankingFunction);
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
        System.out.println("  --ranking-function NAME Name of ranking function from ranking-functions.json");
        System.out.println("  -h, --help              Show this help message");
        System.out.println();
        System.out.println("Cross-System Analysis tool for comparing perfscripts results.");
    }
}