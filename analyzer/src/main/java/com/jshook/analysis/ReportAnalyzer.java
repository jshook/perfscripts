package com.jshook.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cross-System Analysis tool for comparing results from different perfscripts runs.
 * Implements the analysis method described in analysis_method.md
 */
public class ReportAnalyzer {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path currentWorkingDirectory;
    
    public ReportAnalyzer() {
        this.currentWorkingDirectory = Paths.get(System.getProperty("user.dir"));
    }
    
    public ReportAnalyzer(Path workingDirectory) {
        this.currentWorkingDirectory = workingDirectory;
    }
    
    /**
     * Stage 1: Prepare Report target
     * Checks for existing report_* directories first, or creates new one with pattern "report_[epoch_time]"
     */
    public Path prepareReportTarget() throws IOException {
        return prepareReportTarget(null, false);
    }
    
    /**
     * Stage 1: Prepare Report target with options
     * @param reportDir Optional specific report directory name
     * @param updateMode If true, allows using existing directory
     */
    public Path prepareReportTarget(String reportDir, boolean updateMode) throws IOException {
        Path targetDir;
        
        if (reportDir != null) {
            targetDir = currentWorkingDirectory.resolve(reportDir);
            // Only require -U flag for non-default "report" directories
            if (Files.exists(targetDir) && !updateMode && !reportDir.equals("report")) {
                throw new IOException("Report directory '" + reportDir + "' already exists. Use -U option to update.");
            }
        } else {
            targetDir = findExistingReportDir();
            if (targetDir == null) {
                // Use "report" as default instead of epoch timestamp
                targetDir = currentWorkingDirectory.resolve("report");
            }
        }
        
        Files.createDirectories(targetDir);
        return targetDir;
    }
    
    /**
     * Finds existing report_* directory, returns null if none found
     */
    private Path findExistingReportDir() throws IOException {
        return Files.list(currentWorkingDirectory)
            .filter(Files::isDirectory)
            .filter(path -> path.getFileName().toString().startsWith("report_"))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Stage 1: Enumerate Results
     * Finds all directories containing *.fio.json files and groups them by system profile
     * Uses leading path matching and derives system names by eliding common leading/trailing components
     */
    public AnalysisManifest enumerateResults() throws IOException {
        Map<String, List<Path>> rawSystemProfileGroups = new HashMap<>();
        
        // First pass: collect all system paths by profile
        Files.walk(currentWorkingDirectory, 3)
            .filter(Files::isDirectory)
            .filter(dir -> !dir.getFileName().toString().startsWith("report_"))
            .filter(dir -> !dir.getFileName().toString().equals("src"))
            .filter(dir -> !dir.getFileName().toString().equals("target"))
            .filter(dir -> !dir.equals(currentWorkingDirectory))
            .filter(this::containsFioJsonFiles)
            .forEach(dir -> {
                String relativePath = currentWorkingDirectory.relativize(dir).toString();
                String systemProfile = sanitizeFilename(extractSystemProfile(relativePath));
                
                rawSystemProfileGroups.computeIfAbsent(systemProfile, k -> new ArrayList<>()).add(dir);
            });
        
        // Second pass: create system profiles with elided system names
        Map<String, AnalysisManifest.SystemProfile> systemProfileGroups = new HashMap<>();
        for (Map.Entry<String, List<Path>> profileEntry : rawSystemProfileGroups.entrySet()) {
            String systemProfileName = profileEntry.getKey();
            List<Path> systemPaths = profileEntry.getValue();
            
            // Calculate system profile path as common prefix
            Path systemProfilePath = findCommonPrefixPath(systemPaths);
            
            Map<String, Path> systemNamesAndPaths = deriveSystemNamesWithElision(systemPaths);
            AnalysisManifest.SystemProfile systemProfile = new AnalysisManifest.SystemProfile(systemNamesAndPaths, systemProfilePath);
            systemProfileGroups.put(systemProfileName, systemProfile);
        }
        
        return new AnalysisManifest(systemProfileGroups);
    }
    
    /**
     * Checks if directory contains any *.fio.json files (recursively)
     * Also checks for logs.tar.gz which indicates compressed fio.json files
     */
    private boolean containsFioJsonFiles(Path directory) {
        try {
            // Check if directory has logs.tar.gz first
            if (Files.exists(directory.resolve("logs.tar.gz"))) {
                return true;
            }
            
            // Check if directory has .fio.json files (follow symlinks but limit depth)
            // We walk with max depth of 2 to avoid parent directories being included
            // when they only contain .fio.json files in subdirectories
            boolean hasFiles = Files.walk(directory, 2, FileVisitOption.FOLLOW_LINKS)
                .filter(Files::isRegularFile)
                .anyMatch(path -> path.getFileName().toString().endsWith(".fio.json"));
            
            return hasFiles;
        } catch (IOException e) {
            System.out.println("Error checking directory " + directory + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts system profile name from relative path using leading path matching
     */
    private String extractSystemProfile(String relativePath) {
        Path path = Paths.get(relativePath);
        if (path.getNameCount() >= 1) {
            return path.getName(0).toString();
        }
        return relativePath;
    }
    
    /**
     * Extracts system name from intermediate path details
     */
    private String extractSystemName(String relativePath) {
        Path path = Paths.get(relativePath);
        if (path.getNameCount() >= 2) {
            return path.getName(1).toString();
        }
        return path.getFileName().toString();
    }
    
    /**
     * Sanitizes filename by replacing special characters with underscores
     */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    /**
     * Derives system names by eliding common leading and trailing path components
     * within a system profile. System paths are retained separately.
     */
    private Map<String, Path> deriveSystemNamesWithElision(List<Path> systemPaths) {
        if (systemPaths.size() <= 1) {
            Map<String, Path> result = new HashMap<>();
            for (Path path : systemPaths) {
                String relativePath = currentWorkingDirectory.relativize(path).toString();
                result.put(relativePath, path);
            }
            return result;
        }
        
        // Convert paths to relative path components
        List<List<String>> allPathComponents = new ArrayList<>();
        for (Path path : systemPaths) {
            String relativePath = currentWorkingDirectory.relativize(path).toString();
            List<String> pathComponents = Arrays.asList(relativePath.split("/"));
            allPathComponents.add(pathComponents);
        }
        
        // Find common leading components
        int commonLeading = findCommonLeadingComponents(allPathComponents);
        
        // Find common trailing components  
        int commonTrailing = findCommonTrailingComponents(allPathComponents);
        
        // Create system names by eliding common leading and trailing components
        Map<String, Path> systemNamesAndPaths = new HashMap<>();
        for (int i = 0; i < systemPaths.size(); i++) {
            Path path = systemPaths.get(i);
            List<String> pathComponents = allPathComponents.get(i);
            
            // Elide common leading and trailing components
            int startIndex = commonLeading;
            int endIndex = pathComponents.size() - commonTrailing;
            
            // Ensure we don't create empty names
            if (startIndex >= endIndex) {
                startIndex = 0;
                endIndex = pathComponents.size();
            }
            
            String systemName = String.join("/", pathComponents.subList(startIndex, endIndex));
            if (systemName.isEmpty()) {
                systemName = pathComponents.get(pathComponents.size() - 1); // fallback to last component
            }
            
            systemNamesAndPaths.put(systemName, path);
        }
        
        return systemNamesAndPaths;
    }
    
    /**
     * Finds the number of common leading path components
     */
    private int findCommonLeadingComponents(List<List<String>> allPathComponents) {
        if (allPathComponents.isEmpty()) return 0;
        
        int minLength = allPathComponents.stream().mapToInt(List::size).min().orElse(0);
        int commonLeading = 0;
        
        for (int i = 0; i < minLength; i++) {
            String firstComponent = allPathComponents.get(0).get(i);
            final int index = i;
            boolean allMatch = allPathComponents.stream()
                .allMatch(components -> components.get(index).equals(firstComponent));
            
            if (allMatch) {
                commonLeading++;
            } else {
                break;
            }
        }
        
        return commonLeading;
    }
    
    /**
     * Finds the number of common trailing path components
     */
    private int findCommonTrailingComponents(List<List<String>> allPathComponents) {
        if (allPathComponents.isEmpty()) return 0;
        
        int minLength = allPathComponents.stream().mapToInt(List::size).min().orElse(0);
        int commonTrailing = 0;
        
        for (int i = 1; i <= minLength; i++) {
            String firstComponent = allPathComponents.get(0).get(allPathComponents.get(0).size() - i);
            final int offset = i;
            boolean allMatch = allPathComponents.stream()
                .allMatch(components -> components.get(components.size() - offset).equals(firstComponent));
            
            if (allMatch) {
                commonTrailing++;
            } else {
                break;
            }
        }
        
        return commonTrailing;
    }
    
    /**
     * Finds the common prefix path among a list of paths
     */
    private Path findCommonPrefixPath(List<Path> paths) {
        if (paths.isEmpty()) return currentWorkingDirectory;
        if (paths.size() == 1) return paths.get(0).getParent();
        
        // Convert to relative paths
        List<Path> relativePaths = paths.stream()
            .map(path -> currentWorkingDirectory.relativize(path))
            .collect(Collectors.toList());
        
        // Find common prefix components
        Path firstPath = relativePaths.get(0);
        int commonComponents = firstPath.getNameCount();
        
        for (int i = 1; i < relativePaths.size(); i++) {
            Path currentPath = relativePaths.get(i);
            int maxComponents = Math.min(commonComponents, currentPath.getNameCount());
            
            for (int j = 0; j < maxComponents; j++) {
                if (!firstPath.getName(j).equals(currentPath.getName(j))) {
                    commonComponents = j;
                    break;
                }
            }
            commonComponents = Math.min(commonComponents, maxComponents);
        }
        
        if (commonComponents == 0) {
            return currentWorkingDirectory;
        }
        
        Path commonPrefix = firstPath.subpath(0, commonComponents);
        return currentWorkingDirectory.resolve(commonPrefix);
    }
    
    /**
     * Finds the common prefix among a list of strings
     */
    private String findCommonPrefix(List<String> strings) {
        if (strings.isEmpty()) return "";
        
        String first = strings.get(0);
        int commonLength = first.length();
        
        for (int i = 1; i < strings.size(); i++) {
            String current = strings.get(i);
            commonLength = Math.min(commonLength, current.length());
            
            for (int j = 0; j < commonLength; j++) {
                if (first.charAt(j) != current.charAt(j)) {
                    commonLength = j;
                    break;
                }
            }
        }
        
        return first.substring(0, commonLength);
    }
    
    /**
     * Finds the common suffix among a list of strings
     */
    private String findCommonSuffix(List<String> strings) {
        if (strings.isEmpty()) return "";
        
        String first = strings.get(0);
        int commonLength = first.length();
        
        for (int i = 1; i < strings.size(); i++) {
            String current = strings.get(i);
            commonLength = Math.min(commonLength, current.length());
            
            for (int j = 0; j < commonLength; j++) {
                int firstIndex = first.length() - 1 - j;
                int currentIndex = current.length() - 1 - j;
                
                if (first.charAt(firstIndex) != current.charAt(currentIndex)) {
                    commonLength = j;
                    break;
                }
            }
        }
        
        return first.substring(first.length() - commonLength);
    }
    
    /**
     * Removes common prefix and suffix from a string
     */
    private String removeCommonElements(String original, String prefix, String suffix) {
        String result = original;
        
        if (!prefix.isEmpty() && result.startsWith(prefix)) {
            result = result.substring(prefix.length());
        }
        
        if (!suffix.isEmpty() && result.endsWith(suffix) && result.length() > suffix.length()) {
            result = result.substring(0, result.length() - suffix.length());
        }
        
        return result;
    }
    
    /**
     * Derives group names with suffix elision - removes common suffix elements
     * that are duplicated within the group
     */
    private Map<String, List<Path>> refineGroupNames(Map<String, List<Path>> rawGroups) {
        Map<String, List<Path>> refinedGroups = new HashMap<>();
        
        for (Map.Entry<String, List<Path>> entry : rawGroups.entrySet()) {
            String groupName = entry.getKey();
            List<Path> directories = entry.getValue();
            
            if (directories.size() > 1) {
                String refinedName = deriveRefinedGroupName(directories);
                refinedGroups.put(refinedName, directories);
            } else {
                refinedGroups.put(groupName, directories);
            }
        }
        
        return refinedGroups;
    }
    
    /**
     * Derives refined group name by finding common leading path elements
     * and eliding duplicated suffix elements
     */
    private String deriveRefinedGroupName(List<Path> directories) {
        if (directories.isEmpty()) return "";
        if (directories.size() == 1) {
            String relativePath = currentWorkingDirectory.relativize(directories.get(0)).toString();
            return Paths.get(relativePath).getName(0).toString();
        }
        
        List<String> relativePaths = directories.stream()
            .map(dir -> currentWorkingDirectory.relativize(dir).toString())
            .collect(Collectors.toList());
        
        String firstPath = relativePaths.get(0);
        Path firstPathObj = Paths.get(firstPath);
        
        if (firstPathObj.getNameCount() >= 1) {
            return firstPathObj.getName(0).toString();
        }
        
        return firstPath;
    }
    
    /**
     * Creates manifest file in the report directory
     */
    public void createManifest(Path reportDir, AnalysisManifest manifest) throws IOException {
        Path manifestFile = reportDir.resolve("manifest.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile.toFile(), manifest);
    }
    
    /**
     * Creates human-friendly markdown manifest file
     */
    public void createMarkdownManifest(Path reportDir, AnalysisManifest manifest) throws IOException {
        Path markdownFile = reportDir.resolve("manifest.md");
        StringBuilder md = new StringBuilder();
        
        md.append("# Cross-System Analysis Manifest\n\n");
        md.append("Generated: ").append(java.time.LocalDateTime.now()).append("\n\n");
        md.append("## Summary\n\n");
        md.append("- **System Profiles Found**: ").append(manifest.getSystemProfiles().size()).append("\n");
        md.append("- **Total Systems**: ").append(manifest.getTotalDirectories()).append("\n\n");
        
        md.append("## System Profile Details\n\n");
        
        for (String systemProfileName : manifest.getSystemProfiles()) {
            AnalysisManifest.SystemProfile systemProfile = manifest.getSystemProfile(systemProfileName);
            Map<String, Path> systemPaths = systemProfile.getSystemPaths();
            
            md.append("### ").append(systemProfileName).append("\n\n");
            md.append("Systems: ").append(systemPaths.size()).append("\n\n");
            
            for (Map.Entry<String, Path> entry : systemPaths.entrySet()) {
                String systemName = entry.getKey();
                Path systemPath = entry.getValue();
                String relativeSystemPath = currentWorkingDirectory.relativize(systemPath).toString();
                md.append("- **System Name**: `").append(systemName).append("`\n");
                md.append("  **System Path**: `").append(relativeSystemPath).append("`\n");
            }
            md.append("\n");
        }
        
        Files.write(markdownFile, md.toString().getBytes());
    }
    
    /**
     * Executes the complete analysis process
     */
    public Path executeAnalysis() throws IOException {
        return executeAnalysis(null, false);
    }
    
    /**
     * Executes the complete analysis process with options
     */
    public Path executeAnalysis(String reportDir, boolean updateMode) throws IOException {
        Path reportPath = prepareReportTarget(reportDir, updateMode);
        AnalysisManifest manifest = enumerateResults();
        createManifest(reportPath, manifest);
        createMarkdownManifest(reportPath, manifest);
        
        // Stage 2: Generate individual system reports
        executeStage2Analysis(reportPath, manifest);
        
        // Stage 3: Generate system performance profile reports
        executeStage3Analysis(reportPath, manifest);
        
        // Stage 4: Generate cross profile comparisons
        executeStage4Analysis(reportPath, manifest);
        
        return reportPath;
    }
    
    /**
     * Stage 2: Single Directory Analysis
     * Analyzes each system separately and generates individual reports
     */
    private void executeStage2Analysis(Path reportPath, AnalysisManifest manifest) throws IOException {
        for (String systemProfile : manifest.getSystemProfiles()) {
            Map<String, Path> systems = manifest.getSystemsForProfile(systemProfile);
            
            for (Map.Entry<String, Path> entry : systems.entrySet()) {
                String systemName = entry.getKey();
                Path systemDir = entry.getValue();
                
                System.out.println("Analyzing system: " + systemProfile + "__" + systemName);
                analyzeSystemDirectory(reportPath, systemProfile, systemName, systemDir);
            }
        }
    }
    
    /**
     * Analyzes a single system directory and generates its report
     */
    private void analyzeSystemDirectory(Path reportPath, String systemProfile, String systemName, Path systemDir) throws IOException {
        // Create report filename: systemProfile__systemName.md
        String reportFilename = systemProfile + "__" + systemName + ".md";
        Path systemReportPath = reportPath.resolve(reportFilename);
        
        StringBuilder report = new StringBuilder();
        report.append("# System Analysis Report\n\n");
        report.append("**System Profile**: ").append(systemProfile).append("\n");
        report.append("**System Name**: ").append(systemName).append("\n");
        report.append("**Generated**: ").append(java.time.LocalDateTime.now()).append("\n\n");
        
        // Find workload files
        List<Path> workloadFiles = findWorkloadFiles(systemDir);
        report.append("## Workload Summary\n\n");
        report.append("Found ").append(workloadFiles.size()).append(" workload files:\n\n");
        
        for (Path workloadFile : workloadFiles) {
            String relativePath = systemDir.relativize(workloadFile).toString();
            report.append("- `").append(relativePath).append("`\n");
        }
        
        // Perform Stage 2 analysis
        report.append("\n## Performance Analysis\n\n");
        
        try {
            WorkloadAnalyzer analyzer = new WorkloadAnalyzer();
            WorkloadAnalyzer.SystemAnalysis analysis = analyzer.analyzeSystem(systemDir, workloadFiles);
            
            generateAnalysisReport(report, analysis);
            
        } catch (Exception e) {
            report.append("*Analysis error: ").append(e.getMessage()).append("*\n");
            e.printStackTrace();
        }
        
        Files.write(systemReportPath, report.toString().getBytes());
    }
    
    /**
     * Generates detailed analysis report from system analysis results
     */
    private void generateAnalysisReport(StringBuilder report, WorkloadAnalyzer.SystemAnalysis analysis) {
        // Step 1: Optimal Blocksize - Enhanced with all data, descriptions, calculations, and results
        List<WorkloadAnalyzer.WorkloadResult> allRandread = analysis.getAllRandreadResults();
        WorkloadAnalyzer.WorkloadResult optimalRandread = analysis.getOptimalRandread();
        
        report.append("### Optimal Blocksize Analysis\n\n");
        report.append("**Methodology**: All randread workloads are analyzed to determine which blocksize achieves the highest throughput.\n\n");
        
        if (allRandread != null && !allRandread.isEmpty()) {
            report.append("#### All Randread Results (Data Used):\n\n");
            report.append("| Workload Name | Workload File | Blocksize | Throughput (KB/s) | IOPS | Latency P99 (μs) |\n");
            report.append("|---------------|---------------|-----------|-------------------|------|------------------|\n");
            
            for (WorkloadAnalyzer.WorkloadResult result : allRandread) {
                try {
                    FioResult.FioJob job = result.getFioResult().getJobs().get(0);
                    FioResult.FioMetrics readMetrics = job.getRead();
                    String workloadName = extractWorkloadNameFromFilename(result.getFilename());
                    report.append("| **").append(workloadName).append("** | ")
                          .append("`").append(result.getFilename()).append("` | ")
                          .append(result.getParameter()).append(" | ")
                          .append(String.format("%.1f", readMetrics.getBandwidth())).append(" | ")
                          .append(String.format("%.1f", readMetrics.getIops())).append(" | ")
                          .append(String.format("%.1f", readMetrics.getCompletionLatency().getP99() / 1000.0)).append(" |\n");
                } catch (Exception e) {
                    String workloadName = extractWorkloadNameFromFilename(result.getFilename());
                    report.append("| **").append(workloadName).append("** | ")
                          .append("`").append(result.getFilename()).append("` | ")
                          .append(result.getParameter()).append(" | *Error reading metrics* | - | - |\n");
                }
            }
            report.append("\n");
            
            // Show calculation process
            report.append("#### Calculation Process:\n\n");
            report.append("**Selection Criteria**: Highest throughput (bandwidth) among all randread workloads.\n\n");
            
            if (optimalRandread != null) {
                report.append("**Selected Optimal Workload**: `").append(optimalRandread.getFilename()).append("`\n\n");
                
                FioResult.FioJob job = optimalRandread.getFioResult().getJobs().get(0);
                FioResult.FioMetrics readMetrics = job.getRead();
                
                report.append("**Optimal Performance Metrics**:\n");
                report.append("- **Throughput**: ").append(String.format("%.1f KB/s (%.2f GB/s)", readMetrics.getBandwidth(), readMetrics.getBandwidth() / 1024.0 / 1024.0)).append("\n");
                report.append("- **IOPS**: ").append(String.format("%.1f", readMetrics.getIops())).append("\n");
                report.append("- **Latency (mean)**: ").append(String.format("%.1f μs", readMetrics.getCompletionLatency().getMean() / 1000.0)).append("\n");
                report.append("- **Latency (P99)**: ").append(String.format("%.1f μs", readMetrics.getCompletionLatency().getP99() / 1000.0)).append("\n");
                report.append("- **Latency (P95)**: ").append(String.format("%.1f μs", readMetrics.getCompletionLatency().getP95() / 1000.0)).append("\n\n");
            }
        } else {
            report.append("*No randread workloads found for analysis.*\n\n");
        }
        
        // Step 2: Matching Mixed Workload Series
        List<WorkloadAnalyzer.WorkloadResult> mixedSeries = analysis.getMatchingMixedSeries();
        report.append("### Matching Mixed Workload Series\n\n");
        report.append("**Methodology**: According to the optimal randread workload, a mixed workload series is selected with the closest average blocksize.\n\n");
        
        if (!mixedSeries.isEmpty()) {
            if (optimalRandread != null) {
                report.append("**Optimal Randread Blocksize**: `").append(optimalRandread.getParameter()).append("`\n\n");
            }
            
            report.append("#### Selected Mixed Workload Series Data:\n\n");
            report.append("Found ").append(mixedSeries.size()).append(" mixed workloads in matching series:\n\n");
            
            report.append("| Workload Name | Workload File | Series | Streaming Limit | Avg Latency P99 (μs) |\n");
            report.append("|---------------|---------------|--------|-----------------|----------------------|\n");
            
            for (WorkloadAnalyzer.WorkloadResult mixed : mixedSeries) {
                try {
                    FioResult.FioJob job = mixed.getFioResult().getJobs().get(0);
                    FioResult.FioMetrics readMetrics = job.getRead();
                    String streamingLimit = mixed.getParameter().contains("uncapped") ? "Uncapped" : 
                                          mixed.getParameter().replaceAll(".*_(\\d+)Mseq.*", "$1 MB/s");
                    String workloadName = extractWorkloadNameFromFilename(mixed.getFilename());
                    
                    report.append("| **").append(workloadName).append("** | ")
                          .append("`").append(mixed.getFilename()).append("` | ")
                          .append(mixed.getTestId().substring(0, 1)).append("xx | ")
                          .append(streamingLimit).append(" | ");
                    
                    if (readMetrics != null && readMetrics.getCompletionLatency() != null) {
                        report.append(String.format("%.1f", readMetrics.getCompletionLatency().getP99() / 1000.0));
                    } else {
                        report.append("N/A");
                    }
                    report.append(" |\n");
                } catch (Exception e) {
                    String workloadName = extractWorkloadNameFromFilename(mixed.getFilename());
                    report.append("| **").append(workloadName).append("** | ")
                          .append("`").append(mixed.getFilename()).append("` | - | - | *Error reading metrics* |\n");
                }
            }
            report.append("\n");
            
            report.append("**Selection Process**: Mixed workloads are grouped by series (e.g., 3xx, 4xx, 5xx), ");
            report.append("and the series with average blocksize closest to the optimal randread blocksize is selected.\n\n");
        } else {
            report.append("*No matching mixed workload series found.*\n\n");
        }
        
        // Step 3: Knee-point Analysis
        WorkloadAnalyzer.KneePointAnalysis kneeAnalysis = analysis.getKneePointAnalysis();
        report.append("### Knee-Point Analysis\n\n");
        report.append("**Methodology**: The selected mixed workload series is analyzed, comparing the knee points for each of the completion latencies, ");
        report.append("slewing across the stream throttling values. The point at which the P99 latency increases more dramatically between two tests ");
        report.append("is the knee point.\n\n");
        report.append("**Analysis Status**: ").append(kneeAnalysis.getMessage()).append("\n\n");
        
        if (kneeAnalysis.getOptimalMixed() != null && kneeAnalysis.getSubOptimalMixed() != null) {
            report.append("#### Calculation Process:\n\n");
            report.append("**Threshold**: P99 latency increase >20% between consecutive tests identifies knee point.\n");
            report.append("**Knee Point Selection**: The mixed workload with higher latency is the sub-optimal (knee point).\n");
            report.append("**Optimal Selection**: The mixed workload with lower latency before the knee point.\n\n");
            
            // Optimal mixed workload
            WorkloadAnalyzer.WorkloadResult optimalMixed = kneeAnalysis.getOptimalMixed();
            report.append("#### Optimal Mixed Workload (Before Knee Point)\n\n");
            report.append("**File**: `").append(optimalMixed.getFilename()).append("`\n");
            report.append("**Streaming Limit**: ").append(extractStreamingLimitDescription(optimalMixed.getParameter())).append("\n\n");
            generateMixedWorkloadMetrics(report, optimalMixed);
            
            // Sub-optimal mixed workload  
            WorkloadAnalyzer.WorkloadResult subOptimalMixed = kneeAnalysis.getSubOptimalMixed();
            report.append("#### Sub-Optimal Mixed Workload (At Knee Point)\n\n");
            report.append("**File**: `").append(subOptimalMixed.getFilename()).append("`\n");
            report.append("**Streaming Limit**: ").append(extractStreamingLimitDescription(subOptimalMixed.getParameter())).append("\n\n");
            generateMixedWorkloadMetrics(report, subOptimalMixed);
            
            // Show knee point calculation
            report.append("#### Knee Point Calculation:\n\n");
            try {
                double optimalP99 = optimalMixed.getFioResult().getJobs().get(0).getRead().getCompletionLatency().getP99() / 1000.0;
                double subOptimalP99 = subOptimalMixed.getFioResult().getJobs().get(0).getRead().getCompletionLatency().getP99() / 1000.0;
                double increase = ((subOptimalP99 - optimalP99) / optimalP99) * 100.0;
                
                report.append("- **Optimal P99 Latency**: ").append(String.format("%.1f μs", optimalP99)).append("\n");
                report.append("- **Sub-optimal P99 Latency**: ").append(String.format("%.1f μs", subOptimalP99)).append("\n");
                report.append("- **Latency Increase**: ").append(String.format("%.1f%%", increase)).append(" (exceeds 20% threshold)\n\n");
            } catch (Exception e) {
                report.append("*Error calculating knee point metrics.*\n\n");
            }
            
            // Generate sparklines for latency progression
            report.append("#### Latency Progression Sparklines\n\n");
            report.append("**Description**: Unicode sparklines showing order of magnitude changes in latency across streaming limits.\n\n");
            generateLatencySparklines(report, mixedSeries);
        }
    }
    
    /**
     * Extracts streaming limit description from parameter
     */
    private String extractStreamingLimitDescription(String parameter) {
        if (parameter.contains("uncapped")) {
            return "Uncapped (no streaming limit)";
        } else if (parameter.contains("Mseq")) {
            String limit = parameter.replaceAll(".*_(\\d+)Mseq.*", "$1");
            return limit + " MB/s sequential streaming limit";
        }
        return parameter;
    }
    
    /**
     * Generates metrics for a mixed workload
     */
    private void generateMixedWorkloadMetrics(StringBuilder report, WorkloadAnalyzer.WorkloadResult mixed) {
        try {
            FioResult.FioJob job = mixed.getFioResult().getJobs().get(0);
            FioResult.FioMetrics readMetrics = job.getRead();
            FioResult.FioMetrics writeMetrics = job.getWrite();
            
            if (readMetrics != null) {
                report.append("**Random Read Metrics**:\n");
                report.append("- Throughput: ").append(String.format("%.1f KB/s", readMetrics.getBandwidth())).append("\n");
                report.append("- IOPS: ").append(String.format("%.1f", readMetrics.getIops())).append("\n");
                report.append("- Latency (P99): ").append(String.format("%.1f μs", readMetrics.getCompletionLatency().getP99() / 1000.0)).append("\n\n");
            }
            
            if (writeMetrics != null) {
                report.append("**Sequential Write Metrics**:\n");
                report.append("- Throughput: ").append(String.format("%.1f KB/s", writeMetrics.getBandwidth())).append("\n\n");
            }
        } catch (Exception e) {
            report.append("*Error extracting metrics: ").append(e.getMessage()).append("*\n\n");
        }
    }
    
    /**
     * Generates enhanced Unicode sparklines with logarithmic scale and quantile panels
     * Requirements: Lines 71-73 - logarithmic scale, quantile panels, grid pattern layout
     */
    private void generateLatencySparklines(StringBuilder report, List<WorkloadAnalyzer.WorkloadResult> mixedSeries) {
        if (mixedSeries.size() < 2) {
            report.append("*Insufficient data for sparkline generation.*\n\n");
            return;
        }
        
        // Sort by streaming limit for proper progression
        List<WorkloadAnalyzer.WorkloadResult> sortedSeries = new ArrayList<>(mixedSeries);
        sortedSeries.sort((a, b) -> {
            double limitA = extractStreamingLimitValue(a.getParameter());
            double limitB = extractStreamingLimitValue(b.getParameter());
            return Double.compare(limitA, limitB);
        });
        
        // Extract multiple quantiles (P50, P95, P99) and streaming limits
        List<Double> p50Latencies = new ArrayList<>();
        List<Double> p95Latencies = new ArrayList<>();
        List<Double> p99Latencies = new ArrayList<>();
        List<String> streamingLimits = new ArrayList<>();
        List<Double> streamingValues = new ArrayList<>();
        
        for (WorkloadAnalyzer.WorkloadResult mixed : sortedSeries) {
            try {
                FioResult.FioMetrics readMetrics = mixed.getFioResult().getJobs().get(0).getRead();
                if (readMetrics != null && readMetrics.getCompletionLatency() != null) {
                    p50Latencies.add(readMetrics.getCompletionLatency().getP50() / 1000.0);
                    p95Latencies.add(readMetrics.getCompletionLatency().getP95() / 1000.0);
                    p99Latencies.add(readMetrics.getCompletionLatency().getP99() / 1000.0);
                    streamingValues.add(extractStreamingLimitValue(mixed.getParameter()));
                    streamingLimits.add(formatStreamingLimit(mixed.getParameter()));
                }
            } catch (Exception e) {
                // Skip workloads with missing data
            }
        }
        
        if (!p99Latencies.isEmpty()) {
            // Generate logarithmic sparklines
            String p50Sparkline = SparklineGenerator.generateLogarithmicSparkline(p50Latencies);
            String p95Sparkline = SparklineGenerator.generateLogarithmicSparkline(p95Latencies);
            String p99Sparkline = SparklineGenerator.generateLogarithmicSparkline(p99Latencies);
            
            // Quantile Panel Layout (Requirement line 72)
            report.append("#### Latency Quantile Panel\n\n");
            report.append("**Logarithmic Scale Progression** (lowest to unlimited streaming limits):\n\n");
            
            // Grid pattern header
            report.append("| Streaming Limit | P50 μs | P95 μs | P99 μs | P50 | P95 | P99 |\n");
            report.append("|----------------|--------|--------|--------|-----|-----|-----|\n");
            
            // Grid pattern data rows
            for (int i = 0; i < streamingLimits.size(); i++) {
                report.append("| ").append(String.format("%-14s", streamingLimits.get(i))).append(" | ");
                report.append(String.format("%6.1f", p50Latencies.get(i))).append(" | ");
                report.append(String.format("%6.1f", p95Latencies.get(i))).append(" | ");
                report.append(String.format("%6.1f", p99Latencies.get(i))).append(" | ");
                report.append(SparklineGenerator.getSingleSparklineChar(p50Latencies, i)).append(" | ");
                report.append(SparklineGenerator.getSingleSparklineChar(p95Latencies, i)).append(" | ");
                report.append(SparklineGenerator.getSingleSparklineChar(p99Latencies, i)).append(" |\n");
            }
            report.append("\n");
            
            // Summary sparklines (requirement line 73)
            report.append("#### Summary Latency Progressions\n\n");
            report.append("**P50 Latency Progression**: ").append(p50Sparkline).append("\n");
            report.append("**P95 Latency Progression**: ").append(p95Sparkline).append("\n");
            report.append("**P99 Latency Progression**: ").append(p99Sparkline).append("\n\n");
            
            // Summary statistics
            double minP99 = p99Latencies.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double maxP99 = p99Latencies.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            double minP50 = p50Latencies.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double maxP50 = p50Latencies.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            
            report.append("**Latency Ranges**:\n");
            report.append("- P50: ").append(String.format("%.1f - %.1f μs", minP50, maxP50)).append("\n");
            report.append("- P99: ").append(String.format("%.1f - %.1f μs", minP99, maxP99)).append("\n");
            report.append("- **Latency Span**: ").append(String.format("%.1fx increase from optimal to unlimited", maxP99 / minP99)).append("\n\n");
        }
    }
    
    /**
     * Extracts numeric streaming limit value for sorting
     */
    private double extractStreamingLimitValue(String parameter) {
        if (parameter.contains("uncapped")) {
            return Double.MAX_VALUE;
        } else if (parameter.contains("Mseq")) {
            try {
                String limit = parameter.replaceAll(".*_(\\d+)Mseq.*", "$1");
                return Double.parseDouble(limit);
            } catch (Exception e) {
                return 0.0;
            }
        }
        return 0.0;
    }
    
    /**
     * Formats streaming limit for display
     */
    private String formatStreamingLimit(String parameter) {
        if (parameter.contains("uncapped")) {
            return "Unlimited";
        } else if (parameter.contains("Mseq")) {
            try {
                String limit = parameter.replaceAll(".*_(\\d+)Mseq.*", "$1");
                return limit + " MB/s";
            } catch (Exception e) {
                return parameter;
            }
        }
        return parameter;
    }
    
    /**
     * Stage 3: System Performance Profile
     * Requirement lines 77-79: Generate summary reports for each system profile
     */
    private void executeStage3Analysis(Path reportPath, AnalysisManifest manifest) throws IOException {
        System.out.println("Executing Stage 3: System Performance Profile generation...");
        
        for (String systemProfileName : manifest.getSystemProfiles()) {
            System.out.println("Creating performance profile for: " + systemProfileName);
            createSystemPerformanceProfile(reportPath, systemProfileName, manifest);
        }
    }
    
    /**
     * Creates a system performance profile report summarizing all systems in a profile
     */
    private void createSystemPerformanceProfile(Path reportPath, String systemProfileName, AnalysisManifest manifest) throws IOException {
        String profileReportFilename = "PROFILE_" + systemProfileName + ".md";
        Path profileReportPath = reportPath.resolve(profileReportFilename);
        
        StringBuilder report = new StringBuilder();
        report.append("# System Performance Profile: ").append(systemProfileName).append("\n\n");
        report.append("**Generated**: ").append(java.time.LocalDateTime.now()).append("\n\n");
        
        Map<String, Path> systems = manifest.getSystemsForProfile(systemProfileName);
        report.append("## Profile Summary\n\n");
        report.append("**System Profile**: ").append(systemProfileName).append("\n");
        report.append("**Total Systems**: ").append(systems.size()).append("\n");
        report.append("**System Profile Path**: `").append(manifest.getSystemProfile(systemProfileName).getSystemProfilePath()).append("`\n\n");
        
        // Collect performance data from individual system reports
        Map<String, SystemPerformanceData> systemPerformanceData = new HashMap<>();
        
        for (Map.Entry<String, Path> entry : systems.entrySet()) {
            String systemName = entry.getKey();
            try {
                SystemPerformanceData perfData = extractPerformanceDataFromSystemReport(reportPath, systemProfileName, systemName);
                systemPerformanceData.put(systemName, perfData);
            } catch (Exception e) {
                System.err.println("Error extracting performance data for " + systemName + ": " + e.getMessage());
            }
        }
        
        // Generate summary statistics
        generateProfileSummaryStatistics(report, systemPerformanceData);
        
        // Generate comparative analysis within profile
        generateWithinProfileComparison(report, systemPerformanceData);
        
        Files.write(profileReportPath, report.toString().getBytes());
    }
    
    /**
     * Stage 4: Cross Profile Comparisons
     * Requirement lines 81-86: Comparative study with KPIs and rankings
     */
    private void executeStage4Analysis(Path reportPath, AnalysisManifest manifest) throws IOException {
        System.out.println("Executing Stage 4: Cross Profile Comparisons...");
        createCrossProfileComparison(reportPath, manifest);
    }
    
    /**
     * Creates cross-profile comparison report
     */
    private void createCrossProfileComparison(Path reportPath, AnalysisManifest manifest) throws IOException {
        String comparisonReportFilename = "CROSS_PROFILE_COMPARISON.md";
        Path comparisonReportPath = reportPath.resolve(comparisonReportFilename);
        
        StringBuilder report = new StringBuilder();
        report.append("# Cross-System Profile Comparison\n\n");
        report.append("**Generated**: ").append(java.time.LocalDateTime.now()).append("\n\n");
        
        // Collect performance data from all profiles
        Map<String, Map<String, SystemPerformanceData>> allProfileData = new HashMap<>();
        
        for (String systemProfileName : manifest.getSystemProfiles()) {
            Map<String, Path> systems = manifest.getSystemsForProfile(systemProfileName);
            Map<String, SystemPerformanceData> profileData = new HashMap<>();
            
            for (Map.Entry<String, Path> entry : systems.entrySet()) {
                String systemName = entry.getKey();
                try {
                    SystemPerformanceData perfData = extractPerformanceDataFromSystemReport(reportPath, systemProfileName, systemName);
                    profileData.put(systemName, perfData);
                } catch (Exception e) {
                    System.err.println("Error extracting performance data for " + systemName + ": " + e.getMessage());
                }
            }
            allProfileData.put(systemProfileName, profileData);
        }
        
        // Generate key performance indicators
        generateCrossProfileKPIs(report, allProfileData);
        
        // Generate rankings
        generateSystemRankings(report, allProfileData);
        
        Files.write(comparisonReportPath, report.toString().getBytes());
    }
    
    /**
     * Extracts performance data from an individual system report
     */
    private SystemPerformanceData extractPerformanceDataFromSystemReport(Path reportPath, String systemProfile, String systemName) throws IOException {
        String reportFilename = systemProfile + "__" + systemName + ".md";
        Path systemReportPath = reportPath.resolve(reportFilename);
        
        if (!Files.exists(systemReportPath)) {
            throw new IOException("System report not found: " + reportFilename);
        }
        
        String reportContent = Files.readString(systemReportPath);
        
        // Extract key metrics using regex patterns
        double throughput = extractMetricFromReport(reportContent, "Throughput.*?([0-9.]+)\\s+KB/s", 1.0 / 1024.0); // Convert to MB/s
        double iops = extractMetricFromReport(reportContent, "IOPS.*?([0-9.]+)", 1.0);
        double latencyP99 = extractMetricFromReport(reportContent, "Latency \\(P99\\).*?([0-9.]+)\\s+μs", 1.0);
        String blocksize = extractBlocksizeFromReport(reportContent);
        double kneeIncrease = extractMetricFromReport(reportContent, "Latency Increase.*?([0-9.]+)%", 1.0);
        int totalWorkloads = extractWorkloadCount(reportContent);
        
        return new SystemPerformanceData(systemName, systemProfile, throughput, iops, latencyP99, blocksize, kneeIncrease, totalWorkloads);
    }
    
    /**
     * Extracts a numeric metric from report content using regex
     */
    private double extractMetricFromReport(String content, String pattern, double conversionFactor) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(content);
            if (m.find()) {
                return Double.parseDouble(m.group(1)) * conversionFactor;
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return 0.0;
    }
    
    /**
     * Extracts blocksize from report content
     */
    private String extractBlocksizeFromReport(String content) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("Selected Optimal Workload.*?`randread-[0-9]+-([^.]+)\\.fio\\.json`");
            java.util.regex.Matcher m = p.matcher(content);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return "unknown";
    }
    
    /**
     * Extracts workload count from report content
     */
    private int extractWorkloadCount(String content) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("Found (\\d+) workload files:");
            java.util.regex.Matcher m = p.matcher(content);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return 0;
    }
    
    /**
     * Generates summary statistics for a system profile
     */
    private void generateProfileSummaryStatistics(StringBuilder report, Map<String, SystemPerformanceData> systemData) {
        if (systemData.isEmpty()) {
            report.append("*No performance data available.*\n\n");
            return;
        }
        
        report.append("## Performance Statistics\n\n");
        
        // Calculate statistics
        double avgThroughput = systemData.values().stream().mapToDouble(SystemPerformanceData::getOptimalThroughputMBps).average().orElse(0.0);
        double maxThroughput = systemData.values().stream().mapToDouble(SystemPerformanceData::getOptimalThroughputMBps).max().orElse(0.0);
        double minThroughput = systemData.values().stream().mapToDouble(SystemPerformanceData::getOptimalThroughputMBps).min().orElse(0.0);
        
        double avgLatency = systemData.values().stream().mapToDouble(SystemPerformanceData::getOptimalLatencyP99).average().orElse(0.0);
        double maxLatency = systemData.values().stream().mapToDouble(SystemPerformanceData::getOptimalLatencyP99).max().orElse(0.0);
        double minLatency = systemData.values().stream().mapToDouble(SystemPerformanceData::getOptimalLatencyP99).min().orElse(0.0);
        
        report.append("### Throughput Analysis\n\n");
        report.append("| Metric | Value |\n");
        report.append("|--------|-------|\n");
        report.append("| **Average Throughput** | ").append(String.format("%.1f MB/s (%.2f GB/s)", avgThroughput, avgThroughput / 1024.0)).append(" |\n");
        report.append("| **Maximum Throughput** | ").append(String.format("%.1f MB/s (%.2f GB/s)", maxThroughput, maxThroughput / 1024.0)).append(" |\n");
        report.append("| **Minimum Throughput** | ").append(String.format("%.1f MB/s (%.2f GB/s)", minThroughput, minThroughput / 1024.0)).append(" |\n");
        report.append("| **Performance Range** | ").append(String.format("%.1fx variation", maxThroughput / minThroughput)).append(" |\n\n");
        
        report.append("### Latency Analysis\n\n");
        report.append("| Metric | Value |\n");
        report.append("|--------|-------|\n");
        report.append("| **Average P99 Latency** | ").append(String.format("%.1f μs", avgLatency)).append(" |\n");
        report.append("| **Best P99 Latency** | ").append(String.format("%.1f μs", minLatency)).append(" |\n");
        report.append("| **Worst P99 Latency** | ").append(String.format("%.1f μs", maxLatency)).append(" |\n");
        report.append("| **Latency Range** | ").append(String.format("%.1fx variation", maxLatency / minLatency)).append(" |\n\n");
    }
    
    /**
     * Generates within-profile comparison
     */
    private void generateWithinProfileComparison(StringBuilder report, Map<String, SystemPerformanceData> systemData) {
        if (systemData.isEmpty()) return;
        
        report.append("## System Comparison Within Profile\n\n");
        
        // Sort systems by throughput
        List<Map.Entry<String, SystemPerformanceData>> sortedSystems = systemData.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue().getOptimalThroughputMBps(), a.getValue().getOptimalThroughputMBps()))
            .collect(Collectors.toList());
        
        report.append("| Rank | System | Throughput | IOPS | P99 Latency | Blocksize | Performance Class |\n");
        report.append("|------|--------|------------|------|-------------|-----------|------------------|\n");
        
        int rank = 1;
        for (Map.Entry<String, SystemPerformanceData> entry : sortedSystems) {
            SystemPerformanceData data = entry.getValue();
            report.append("| ").append(rank++).append(" | ");
            report.append("`").append(data.getSystemName()).append("` | ");
            report.append(String.format("%.1f MB/s", data.getOptimalThroughputMBps())).append(" | ");
            report.append(String.format("%.1f", data.getOptimalIOPS())).append(" | ");
            report.append(String.format("%.1f μs", data.getOptimalLatencyP99())).append(" | ");
            report.append("`").append(data.getOptimalBlocksize()).append("` | ");
            report.append("**").append(data.getPerformanceClass()).append("** |\n");
        }
        report.append("\n");
    }
    
    /**
     * Generates cross-profile KPIs
     */
    private void generateCrossProfileKPIs(StringBuilder report, Map<String, Map<String, SystemPerformanceData>> allProfileData) {
        report.append("## Key Performance Indicators by Profile\n\n");
        
        report.append("| Profile | Systems | Avg Throughput | Max Throughput | Avg Latency | Best System |\n");
        report.append("|---------|---------|----------------|----------------|-------------|-------------|\n");
        
        for (Map.Entry<String, Map<String, SystemPerformanceData>> profileEntry : allProfileData.entrySet()) {
            String profileName = profileEntry.getKey();
            Map<String, SystemPerformanceData> systems = profileEntry.getValue();
            
            if (systems.isEmpty()) continue;
            
            double avgThroughput = systems.values().stream().mapToDouble(SystemPerformanceData::getOptimalThroughputMBps).average().orElse(0.0);
            double maxThroughput = systems.values().stream().mapToDouble(SystemPerformanceData::getOptimalThroughputMBps).max().orElse(0.0);
            double avgLatency = systems.values().stream().mapToDouble(SystemPerformanceData::getOptimalLatencyP99).average().orElse(0.0);
            
            String bestSystem = systems.entrySet().stream()
                .max((a, b) -> Double.compare(a.getValue().getOptimalThroughputMBps(), b.getValue().getOptimalThroughputMBps()))
                .map(Map.Entry::getKey)
                .orElse("unknown");
            
            report.append("| **").append(profileName).append("** | ");
            report.append(systems.size()).append(" | ");
            report.append(String.format("%.1f MB/s", avgThroughput)).append(" | ");
            report.append(String.format("%.1f MB/s", maxThroughput)).append(" | ");
            report.append(String.format("%.1f μs", avgLatency)).append(" | ");
            report.append("`").append(bestSystem).append("` |\n");
        }
        report.append("\n");
    }
    
    /**
     * Generates system rankings across all profiles
     */
    private void generateSystemRankings(StringBuilder report, Map<String, Map<String, SystemPerformanceData>> allProfileData) {
        report.append("## Overall System Rankings\n\n");
        
        // Flatten all systems across profiles
        List<SystemPerformanceData> allSystems = allProfileData.values().stream()
            .flatMap(systems -> systems.values().stream())
            .sorted((a, b) -> Double.compare(b.getOptimalThroughputMBps(), a.getOptimalThroughputMBps()))
            .collect(Collectors.toList());
        
        report.append("### Top Performers by Throughput\n\n");
        report.append("| Rank | System | Profile | Throughput | Latency | Performance Class |\n");
        report.append("|------|--------|---------|------------|---------|------------------|\n");
        
        int rank = 1;
        for (SystemPerformanceData system : allSystems) {
            report.append("| ").append(rank++).append(" | ");
            report.append("`").append(system.getSystemName()).append("` | ");
            report.append("**").append(system.getSystemProfile()).append("** | ");
            report.append(String.format("%.1f MB/s (%.2f GB/s)", system.getOptimalThroughputMBps(), system.getOptimalThroughputGBps())).append(" | ");
            report.append(String.format("%.1f μs", system.getOptimalLatencyP99())).append(" | ");
            report.append("**").append(system.getPerformanceClass()).append("** |\n");
        }
        report.append("\n");
        
        // Profile rankings
        report.append("### Profile Performance Summary\n\n");
        Map<String, Double> profileAvgThroughput = allProfileData.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().values().stream().mapToDouble(SystemPerformanceData::getOptimalThroughputMBps).average().orElse(0.0)
            ));
        
        List<Map.Entry<String, Double>> sortedProfiles = profileAvgThroughput.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());
        
        rank = 1;
        for (Map.Entry<String, Double> entry : sortedProfiles) {
            report.append("**").append(rank++).append(". ").append(entry.getKey()).append("**");
            report.append(" - Average: ").append(String.format("%.1f MB/s (%.2f GB/s)", entry.getValue(), entry.getValue() / 1024.0)).append("\n");
        }
        report.append("\n");
    }
    
    /**
     * Finds all workload files (*.fio.json) in a system directory
     */
    private List<Path> findWorkloadFiles(Path systemDir) throws IOException {
        return Files.walk(systemDir, FileVisitOption.FOLLOW_LINKS)
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(".fio.json"))
            .collect(Collectors.toList());
    }
    
    /**
     * Extracts workload name from filename (requirement line 76)
     * Examples: randread-005-16k.fio.json -> Random Read 16k
     *          mixed-602-128to256k_20Mseq.fio.json -> Mixed 128-256k (20MB/s)
     */
    private String extractWorkloadNameFromFilename(String filename) {
        String basename = filename.replace(".fio.json", "");
        
        if (basename.startsWith("randread-")) {
            String blocksize = basename.replaceAll("randread-\\d+-(.+)", "$1");
            return "Random Read " + blocksize.toUpperCase();
        } else if (basename.startsWith("seqread-")) {
            String filesize = basename.replaceAll("seqread-\\d+-(.+)", "$1");
            return "Sequential Read " + filesize.toUpperCase();
        } else if (basename.startsWith("seqwrite-")) {
            String filesize = basename.replaceAll("seqwrite-\\d+-(.+)", "$1");
            return "Sequential Write " + filesize.toUpperCase();
        } else if (basename.startsWith("mixed-")) {
            try {
                String params = basename.replaceAll("mixed-\\d+-(.+)", "$1");
                String[] parts = params.split("_");
                String blockrange = parts[0].replace("to", "-");
                String limit = parts.length > 1 && !parts[1].equals("uncapped") ? 
                    " (" + parts[1].replace("Mseq", " MB/s") + ")" : 
                    " (Uncapped)";
                return "Mixed " + blockrange + limit;
            } catch (Exception e) {
                return "Mixed Workload";
            }
        }
        
        // Fallback for unrecognized patterns
        return basename.replaceAll("-", " ").toUpperCase();
    }
}