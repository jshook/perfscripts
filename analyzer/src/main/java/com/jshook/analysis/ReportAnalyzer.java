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
    private String rankingFunctionName;
    private Set<String> rankingFunctionNames;
    
    public ReportAnalyzer() {
        this.currentWorkingDirectory = Paths.get(System.getProperty("user.dir"));
        ensureRankingFunctionsFile();
    }
    
    public ReportAnalyzer(Path workingDirectory) {
        this.currentWorkingDirectory = workingDirectory;
        ensureRankingFunctionsFile();
    }
    
    /**
     * Sets a custom ranking function name for system rankings (backward compatibility)
     */
    public void setRankingFunction(String rankingFunctionName) {
        this.rankingFunctionName = rankingFunctionName;
        this.rankingFunctionNames = null; // Clear multiple functions when single is set
    }
    
    /**
     * Sets multiple custom ranking function names for system rankings
     */
    public void setRankingFunctions(Set<String> rankingFunctionNames) {
        this.rankingFunctionNames = new LinkedHashSet<>(rankingFunctionNames);
        this.rankingFunctionName = null; // Clear single function when multiple are set
    }
    
    /**
     * Ensures ranking-functions.json exists in the local directory, copying from classpath if needed
     */
    private void ensureRankingFunctionsFile() {
        Path localRankingFunctions = currentWorkingDirectory.resolve("ranking-functions.json");
        
        // If the file already exists locally, don't overwrite it
        if (Files.exists(localRankingFunctions)) {
            return;
        }
        
        // Copy from classpath resources
        try (java.io.InputStream inputStream = getClass().getClassLoader().getResourceAsStream("ranking-functions.json")) {
            if (inputStream != null) {
                Files.copy(inputStream, localRankingFunctions);
                System.out.println("Copied ranking-functions.json from embedded resources to local directory");
            } else {
                System.err.println("Warning: Could not find ranking-functions.json in classpath resources");
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to copy ranking-functions.json from classpath: " + e.getMessage());
        }
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
            .filter(dir -> !dir.getFileName().toString().equals("report"))
            .filter(dir -> !dir.getFileName().toString().equals("src"))
            .filter(dir -> !dir.getFileName().toString().equals("target"))
            .filter(dir -> !dir.getFileName().toString().equals("perfscripts"))
            .filter(dir -> !dir.getFileName().toString().equals("docs"))
            .filter(dir -> !dir.equals(currentWorkingDirectory))
            .filter(dir -> {
                String relativePath = currentWorkingDirectory.relativize(dir).toString();
                return !relativePath.startsWith("perfscripts/") && !relativePath.startsWith("perfscripts\\");
            })
            .filter(this::shouldScanDirectory)
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
     * Checks if directory should be scanned (not excluded by .noscan file)
     * Item #10 from analysis_method.md: directories with .noscan files are excluded
     */
    private boolean shouldScanDirectory(Path directory) {
        try {
            // Check if the directory itself contains a .noscan file
            if (Files.exists(directory.resolve(".noscan"))) {
                return false;
            }
            
            // Check parent directories up to current working directory for .noscan files
            Path current = directory.getParent();
            while (current != null && !current.equals(currentWorkingDirectory)) {
                if (Files.exists(current.resolve(".noscan"))) {
                    return false;
                }
                current = current.getParent();
            }
            
            return true;
        } catch (Exception e) {
            // On error, allow scanning (conservative approach)
            return true;
        }
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
        
        SystemMetrics systemMetrics = new SystemMetrics(systemName, systemProfile);
        systemMetrics.setTotalWorkloads(workloadFiles.size());
        
        try {
            WorkloadAnalyzer analyzer = new WorkloadAnalyzer();
            WorkloadAnalyzer.SystemAnalysis analysis = analyzer.analyzeSystem(systemDir, workloadFiles);
            
            generateAnalysisReport(report, analysis);
            extractSystemMetrics(systemMetrics, analysis);
            
        } catch (Exception e) {
            report.append("*Analysis error: ").append(e.getMessage()).append("*\n");
            e.printStackTrace();
        }
        
        Files.write(systemReportPath, report.toString().getBytes());
        
        // Save system metrics to JSON file adjacent to report
        String metricsFilename = systemProfile + "__" + systemName + ".json";
        Path metricsPath = reportPath.resolve(metricsFilename);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metricsPath.toFile(), systemMetrics);
        } catch (Exception e) {
            System.err.println("Error saving system metrics to JSON: " + e.getMessage());
        }
    }
    
    /**
     * Extracts system metrics from optimal mixed workload results for JSON storage
     * All metrics come from the winning mixed workload after knee-point analysis
     */
    private void extractSystemMetrics(SystemMetrics metrics, WorkloadAnalyzer.SystemAnalysis analysis) {
        try {
            // Get optimal blocksize from randread analysis (for reference only)
            WorkloadAnalyzer.WorkloadResult optimalRandread = analysis.getOptimalRandread();
            if (optimalRandread != null) {
                metrics.setOptimalBlocksize(optimalRandread.getParameter());
            }
            
            WorkloadAnalyzer.KneePointAnalysis kneeAnalysis = analysis.getKneePointAnalysis();
            if (kneeAnalysis.getOptimalMixed() != null && kneeAnalysis.getSubOptimalMixed() != null) {
                WorkloadAnalyzer.WorkloadResult optimalMixed = kneeAnalysis.getOptimalMixed();
                WorkloadAnalyzer.WorkloadResult subOptimalMixed = kneeAnalysis.getSubOptimalMixed();
                
                // Extract ALL component metrics from optimal mixed workload ONLY
                extractOptimalMixedWorkloadMetrics(metrics, optimalMixed);
                
                // Calculate knee point metrics
                FioResult.FioMetrics optimalReadMetrics = extractRandreadMetrics(optimalMixed);
                FioResult.FioMetrics subOptimalReadMetrics = extractRandreadMetrics(subOptimalMixed);
                
                if (optimalReadMetrics != null && subOptimalReadMetrics != null) {
                    double optimalP99 = optimalReadMetrics.getCompletionLatency().getP99() / 1000000.0;
                    double subOptimalP99 = subOptimalReadMetrics.getCompletionLatency().getP99() / 1000000.0;
                    double increase = ((subOptimalP99 - optimalP99) / optimalP99) * 100.0;
                    metrics.setKneePointLatencyIncreasePercent(increase);
                }
                
                // Store metadata about the optimal mixed workload
                metrics.setOptimalMixedWorkloadName(optimalMixed.getFilename());
                String streamLimit = extractStreamLimitFromWorkloadName(optimalMixed.getFilename());
                if (streamLimit != null) {
                    try {
                        double limitMBps = parseStreamLimit(streamLimit);
                        metrics.setOptimalStreamLimitMBps(limitMBps);
                    } catch (NumberFormatException e) {
                        // Ignore parsing errors
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting system metrics: " + e.getMessage());
        }
    }
    
    /**
     * Extracts all component metrics from the optimal mixed workload
     */
    private void extractOptimalMixedWorkloadMetrics(SystemMetrics metrics, WorkloadAnalyzer.WorkloadResult optimalMixed) {
        try {
            // Validate input parameters
            if (optimalMixed == null) {
                System.err.println("Warning: optimalMixed workload is null, skipping component metric extraction");
                return;
            }
            
            FioResult fioResult = optimalMixed.getFioResult();
            if (fioResult == null) {
                System.err.println("Warning: FIO result is null for workload " + optimalMixed.getFilename());
                return;
            }
            
            if (fioResult.getJobs() == null || fioResult.getJobs().isEmpty()) {
                System.err.println("Warning: No jobs found in FIO result for workload " + optimalMixed.getFilename());
                return;
            }
            
            // Validate mixed workload contains expected components
            validateMixedWorkloadComponents(fioResult, optimalMixed.getFilename());
            
            // Process each job component in the mixed workload
            for (FioResult.FioJob job : fioResult.getJobs()) {
                try {
                    if (job == null || job.getJobname() == null) {
                        System.err.println("Warning: Skipping null job or job with null name in " + optimalMixed.getFilename());
                        continue;
                    }
                    
                    String jobName = job.getJobname().toLowerCase();
                    
                    if (jobName.contains("randread") || jobName.contains("random_read")) {
                        extractRandreadMetrics(job, metrics, optimalMixed.getFilename());
                    } else if (jobName.contains("seqread") || jobName.contains("sequential_read")) {
                        extractSeqreadMetrics(job, metrics, optimalMixed.getFilename());
                    } else if (jobName.contains("seqwrite") || jobName.contains("sequential_write")) {
                        extractSeqwriteMetrics(job, metrics, optimalMixed.getFilename());
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Error processing job '" + (job != null && job.getJobname() != null ? job.getJobname() : "unknown") + 
                                     "' in workload " + optimalMixed.getFilename() + ": " + e.getMessage());
                }
            }
            
            // If no specific job names found, try to extract from first job with read metrics (fallback)
            if (metrics.getRandreadThroughputMBps() == 0.0) {
                tryFallbackMetricExtraction(fioResult, metrics, optimalMixed.getFilename());
            }
            
        } catch (Exception e) {
            System.err.println("Error extracting metrics from mixed workload " + 
                             (optimalMixed != null ? optimalMixed.getFilename() : "unknown") + ": " + e.getMessage());
        }
    }
    
    private void extractRandreadMetrics(FioResult.FioJob job, SystemMetrics metrics, String filename) {
        try {
            FioResult.FioMetrics readMetrics = job.getRead();
            if (readMetrics == null) {
                System.err.println("Warning: No read metrics found for randread job in " + filename);
                return;
            }
            
            // Validate and extract bandwidth/IOPS
            if (readMetrics.getBandwidth() >= 0) {
                metrics.setRandreadThroughputMBps(readMetrics.getBandwidth() / 1024.0);
            }
            if (readMetrics.getIops() >= 0) {
                metrics.setRandreadIOPS(readMetrics.getIops());
            }
            
            // Extract latency metrics with validation
            if (readMetrics.getCompletionLatency() != null) {
                extractLatencyMetrics(readMetrics.getCompletionLatency(), metrics, filename);
            } else {
                System.err.println("Warning: No completion latency data found for randread job in " + filename);
            }
        } catch (Exception e) {
            System.err.println("Warning: Error extracting randread metrics from " + filename + ": " + e.getMessage());
        }
    }
    
    private void extractSeqreadMetrics(FioResult.FioJob job, SystemMetrics metrics, String filename) {
        try {
            FioResult.FioMetrics readMetrics = job.getRead();
            if (readMetrics != null && readMetrics.getBandwidth() >= 0) {
                metrics.setSeqreadThroughputMBps(readMetrics.getBandwidth() / 1024.0);
            } else {
                System.err.println("Warning: Invalid or missing seqread metrics in " + filename);
            }
        } catch (Exception e) {
            System.err.println("Warning: Error extracting seqread metrics from " + filename + ": " + e.getMessage());
        }
    }
    
    private void extractSeqwriteMetrics(FioResult.FioJob job, SystemMetrics metrics, String filename) {
        try {
            FioResult.FioMetrics writeMetrics = job.getWrite();
            if (writeMetrics != null && writeMetrics.getBandwidth() >= 0) {
                metrics.setSeqwriteThroughputMBps(writeMetrics.getBandwidth() / 1024.0);
            } else {
                System.err.println("Warning: Invalid or missing seqwrite metrics in " + filename);
            }
        } catch (Exception e) {
            System.err.println("Warning: Error extracting seqwrite metrics from " + filename + ": " + e.getMessage());
        }
    }
    
    private void extractLatencyMetrics(FioResult.LatencyStats latencyStats, SystemMetrics metrics, String filename) {
        try {
            double mean = latencyStats.getMean();
            double p50 = latencyStats.getP50();
            double p95 = latencyStats.getP95();
            double p99 = latencyStats.getP99();
            
            // Convert from nanoseconds to fractional milliseconds as per analysis_method.md
            if (mean >= 0 && mean < 1e10) { // Less than 10 seconds in nanoseconds
                metrics.setRandreadLatencyMeanUs(mean / 1000000.0);
            }
            if (p50 >= 0 && p50 < 1e10) {
                metrics.setRandreadLatencyP50Us(p50 / 1000000.0);
            }
            if (p95 >= 0 && p95 < 1e10) {
                metrics.setRandreadLatencyP95Us(p95 / 1000000.0);
            }
            if (p99 >= 0 && p99 < 1e10) {
                metrics.setRandreadLatencyP99Us(p99 / 1000000.0);
            }
            
            // Calculate P99/P50 ratio if both values are valid
            if (p50 > 0 && p99 > 0) {
                metrics.setRandreadLatencyP99P50Ratio(p99 / p50);
            }
            
        } catch (Exception e) {
            System.err.println("Warning: Error extracting latency metrics from " + filename + ": " + e.getMessage());
        }
    }
    
    private void tryFallbackMetricExtraction(FioResult fioResult, SystemMetrics metrics, String filename) {
        try {
            FioResult.FioJob firstJob = fioResult.getJobs().get(0);
            if (firstJob == null) {
                System.err.println("Warning: First job is null in fallback extraction for " + filename);
                return;
            }
            
            FioResult.FioMetrics readMetrics = firstJob.getRead();
            if (readMetrics != null) {
                System.err.println("Info: Using fallback metric extraction from first job for " + filename);
                extractRandreadMetrics(firstJob, metrics, filename);
            } else {
                System.err.println("Warning: No read metrics found in fallback extraction for " + filename);
            }
        } catch (Exception e) {
            System.err.println("Warning: Error in fallback metric extraction for " + filename + ": " + e.getMessage());
        }
    }
    
    /**
     * Validates that a mixed workload contains expected component types
     */
    private void validateMixedWorkloadComponents(FioResult fioResult, String filename) {
        try {
            boolean hasRandread = false;
            boolean hasSeqread = false;
            boolean hasSeqwrite = false;
            int totalJobs = fioResult.getJobs().size();
            
            // Check what components are present
            for (FioResult.FioJob job : fioResult.getJobs()) {
                if (job != null && job.getJobname() != null) {
                    String jobName = job.getJobname().toLowerCase();
                    
                    if (jobName.contains("randread") || jobName.contains("random_read")) {
                        hasRandread = true;
                    } else if (jobName.contains("seqread") || jobName.contains("sequential_read")) {
                        hasSeqread = true;
                    } else if (jobName.contains("seqwrite") || jobName.contains("sequential_write")) {
                        hasSeqwrite = true;
                    }
                }
            }
            
            // Report validation results
            if (!hasRandread && !hasSeqread && !hasSeqwrite) {
                System.err.println("Warning: Mixed workload " + filename + " has " + totalJobs + 
                                 " jobs but no recognizable component types (randread/seqread/seqwrite). Using fallback extraction.");
            } else {
                // Report which components are present/missing
                StringBuilder componentInfo = new StringBuilder();
                componentInfo.append("Mixed workload ").append(filename).append(" components: ");
                
                if (hasRandread) componentInfo.append("randread ");
                if (hasSeqread) componentInfo.append("seqread ");
                if (hasSeqwrite) componentInfo.append("seqwrite ");
                
                if (!hasRandread) componentInfo.append("(missing randread) ");
                if (!hasSeqread) componentInfo.append("(missing seqread) ");
                if (!hasSeqwrite) componentInfo.append("(missing seqwrite) ");
                
                // Only log as info if we're missing some expected components
                if (!hasRandread || !hasSeqread || !hasSeqwrite) {
                    System.err.println("Info: " + componentInfo.toString().trim());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Warning: Error validating mixed workload components for " + filename + ": " + e.getMessage());
        }
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
            report.append("| Workload Name | Workload File | Blocksize | Throughput (KB/s) | IOPS | Latency P99 (ms) |\n");
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
                          .append(String.format("%.1f", readMetrics.getCompletionLatency().getP99() / 1000000.0)).append(" |\n");
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
                report.append("- **Latency (mean)**: ").append(String.format("%.1f ms", readMetrics.getCompletionLatency().getMean() / 1000000.0)).append("\n");
                report.append("- **Latency (P99)**: ").append(String.format("%.1f ms", readMetrics.getCompletionLatency().getP99() / 1000000.0)).append("\n");
                report.append("- **Latency (P95)**: ").append(String.format("%.1f ms", readMetrics.getCompletionLatency().getP95() / 1000000.0)).append("\n\n");
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
            
            report.append("| Workload Name | Workload File | Series | Streaming Limit | Avg Latency P99 (ms) |\n");
            report.append("|---------------|---------------|--------|-----------------|----------------------|\n");
            
            for (WorkloadAnalyzer.WorkloadResult mixed : mixedSeries) {
                try {
                    FioResult.FioMetrics readMetrics = extractRandreadMetrics(mixed);
                    String streamingLimit = mixed.getParameter().contains("uncapped") ? "Uncapped" : 
                                          mixed.getParameter().replaceAll(".*_(\\d+)Mseq.*", "$1 MB/s");
                    String workloadName = extractWorkloadNameFromFilename(mixed.getFilename());
                    
                    report.append("| **").append(workloadName).append("** | ")
                          .append("`").append(mixed.getFilename()).append("` | ")
                          .append(mixed.getTestId().substring(0, 1)).append("xx | ")
                          .append(streamingLimit).append(" | ");
                    
                    if (readMetrics != null && readMetrics.getCompletionLatency() != null) {
                        report.append(String.format("%.1f", readMetrics.getCompletionLatency().getP99() / 1000000.0));
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
                // Extract P99 latency from randread component of optimal mixed workload
                double optimalP99 = extractRandreadP99Latency(optimalMixed);
                double subOptimalP99 = extractRandreadP99Latency(subOptimalMixed);
                double increase = ((subOptimalP99 - optimalP99) / optimalP99) * 100.0;
                
                report.append("- **Optimal P99 Latency**: ").append(String.format("%.1f ms", optimalP99)).append("\n");
                report.append("- **Sub-optimal P99 Latency**: ").append(String.format("%.1f ms", subOptimalP99)).append("\n");
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
            List<FioResult.FioJob> jobs = mixed.getFioResult().getJobs();
            
            // Process each job in the mixed workload
            for (FioResult.FioJob job : jobs) {
                String jobName = job.getJobname();
                
                // Identify the workload component type based on job name
                if (jobName != null && jobName.toLowerCase().contains("randread")) {
                    FioResult.FioMetrics readMetrics = job.getRead();
                    if (readMetrics != null) {
                        report.append("**Random Read Metrics**:\n");
                        report.append("- Throughput: ").append(String.format("%.1f KB/s", readMetrics.getBandwidth())).append("\n");
                        report.append("- IOPS: ").append(String.format("%.1f", readMetrics.getIops())).append("\n");
                        report.append("- Latency (P99): ").append(String.format("%.1f ms", readMetrics.getCompletionLatency().getP99() / 1000000.0)).append("\n");
                        report.append("- Latency (P95): ").append(String.format("%.1f ms", readMetrics.getCompletionLatency().getP95() / 1000000.0)).append("\n");
                        report.append("- Latency (P50): ").append(String.format("%.1f ms", readMetrics.getCompletionLatency().getP50() / 1000000.0)).append("\n\n");
                    }
                } else if (jobName != null && jobName.toLowerCase().contains("seqread")) {
                    FioResult.FioMetrics readMetrics = job.getRead();
                    if (readMetrics != null) {
                        report.append("**Sequential Read Metrics**:\n");
                        report.append("- Throughput: ").append(String.format("%.1f KB/s (%.2f MB/s)", readMetrics.getBandwidth(), readMetrics.getBandwidth() / 1024.0)).append("\n");
                        report.append("- IOPS: ").append(String.format("%.1f", readMetrics.getIops())).append("\n\n");
                    }
                } else if (jobName != null && jobName.toLowerCase().contains("seqwrite")) {
                    FioResult.FioMetrics writeMetrics = job.getWrite();
                    if (writeMetrics != null) {
                        report.append("**Sequential Write Metrics**:\n");
                        report.append("- Throughput: ").append(String.format("%.1f KB/s (%.2f MB/s)", writeMetrics.getBandwidth(), writeMetrics.getBandwidth() / 1024.0)).append("\n");
                        report.append("- IOPS: ").append(String.format("%.1f", writeMetrics.getIops())).append("\n\n");
                    }
                } else {
                    // Handle generic job or unknown type
                    FioResult.FioMetrics readMetrics = job.getRead();
                    FioResult.FioMetrics writeMetrics = job.getWrite();
                    
                    if (readMetrics != null && readMetrics.getIoBytes() > 0) {
                        report.append("**").append(jobName != null ? jobName : "Read").append(" Metrics**:\n");
                        report.append("- Throughput: ").append(String.format("%.1f KB/s", readMetrics.getBandwidth())).append("\n");
                        report.append("- IOPS: ").append(String.format("%.1f", readMetrics.getIops())).append("\n");
                        if (readMetrics.getCompletionLatency() != null) {
                            report.append("- Latency (P99): ").append(String.format("%.1f ms", readMetrics.getCompletionLatency().getP99() / 1000000.0)).append("\n");
                        }
                        report.append("\n");
                    }
                    
                    if (writeMetrics != null && writeMetrics.getIoBytes() > 0) {
                        report.append("**").append(jobName != null ? jobName + " Write" : "Write").append(" Metrics**:\n");
                        report.append("- Throughput: ").append(String.format("%.1f KB/s", writeMetrics.getBandwidth())).append("\n");
                        report.append("- IOPS: ").append(String.format("%.1f", writeMetrics.getIops())).append("\n\n");
                    }
                }
            }
            
            // Calculate and display aggregate metrics
            double totalReadBandwidth = 0;
            double totalWriteBandwidth = 0;
            double totalReadIOPS = 0;
            double totalWriteIOPS = 0;
            
            for (FioResult.FioJob job : jobs) {
                if (job.getRead() != null && job.getRead().getIoBytes() > 0) {
                    totalReadBandwidth += job.getRead().getBandwidth();
                    totalReadIOPS += job.getRead().getIops();
                }
                if (job.getWrite() != null && job.getWrite().getIoBytes() > 0) {
                    totalWriteBandwidth += job.getWrite().getBandwidth();
                    totalWriteIOPS += job.getWrite().getIops();
                }
            }
            
            if (jobs.size() > 1) {
                report.append("**Aggregate Metrics**:\n");
                if (totalReadBandwidth > 0) {
                    report.append("- Total Read Throughput: ").append(String.format("%.1f KB/s (%.2f MB/s)", totalReadBandwidth, totalReadBandwidth / 1024.0)).append("\n");
                    report.append("- Total Read IOPS: ").append(String.format("%.1f", totalReadIOPS)).append("\n");
                }
                if (totalWriteBandwidth > 0) {
                    report.append("- Total Write Throughput: ").append(String.format("%.1f KB/s (%.2f MB/s)", totalWriteBandwidth, totalWriteBandwidth / 1024.0)).append("\n");
                    report.append("- Total Write IOPS: ").append(String.format("%.1f", totalWriteIOPS)).append("\n");
                }
                report.append("\n");
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
                // Find the randread job in the mixed workload
                FioResult.FioMetrics readMetrics = null;
                for (FioResult.FioJob job : mixed.getFioResult().getJobs()) {
                    String jobName = job.getJobname();
                    if (jobName != null && jobName.toLowerCase().contains("randread")) {
                        readMetrics = job.getRead();
                        break;
                    }
                }
                
                // If no randread job found, try the first job with read metrics
                if (readMetrics == null && !mixed.getFioResult().getJobs().isEmpty()) {
                    readMetrics = mixed.getFioResult().getJobs().get(0).getRead();
                }
                
                if (readMetrics != null && readMetrics.getCompletionLatency() != null) {
                    p50Latencies.add(readMetrics.getCompletionLatency().getP50() / 1000000.0);
                    p95Latencies.add(readMetrics.getCompletionLatency().getP95() / 1000000.0);
                    p99Latencies.add(readMetrics.getCompletionLatency().getP99() / 1000000.0);
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
            report.append("| Streaming Limit | P50 ms | P95 ms | P99 ms | P50 | P95 | P99 |\n");
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
            report.append("- P50: ").append(String.format("%.1f - %.1f ms", minP50, maxP50)).append("\n");
            report.append("- P99: ").append(String.format("%.1f - %.1f ms", minP99, maxP99)).append("\n");
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
        
        // Create and populate profile metrics
        SystemProfileMetrics profileMetrics = createProfileMetrics(systemProfileName, systemPerformanceData);
        
        // Generate summary statistics
        generateProfileSummaryStatistics(report, systemPerformanceData);
        
        // Generate comparative analysis within profile
        generateWithinProfileComparison(report, systemPerformanceData);
        
        // Generate ranking function analysis for systems within this profile
        generateProfileRankingAnalysis(report, reportPath, systemProfileName, manifest);
        
        Files.write(profileReportPath, report.toString().getBytes());
        
        // Save profile metrics to JSON file adjacent to report
        String metricsFilename = "PROFILE_" + systemProfileName + ".json";
        Path metricsPath = reportPath.resolve(metricsFilename);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metricsPath.toFile(), profileMetrics);
        } catch (Exception e) {
            System.err.println("Error saving profile metrics to JSON: " + e.getMessage());
        }
    }
    
    /**
     * Creates profile metrics from system performance data
     */
    private SystemProfileMetrics createProfileMetrics(String profileName, Map<String, SystemPerformanceData> systemData) {
        SystemProfileMetrics metrics = new SystemProfileMetrics(profileName);
        
        if (systemData.isEmpty()) {
            return metrics;
        }
        
        metrics.setTotalSystems(systemData.size());
        
        // Collect randread component metrics from optimal mixed workloads
        List<Double> randreadThroughputValues = systemData.values().stream()
            .mapToDouble(SystemPerformanceData::getRandreadThroughputMBps)
            .boxed()
            .collect(Collectors.toList());
        
        List<Double> randreadIOPSValues = systemData.values().stream()
            .mapToDouble(SystemPerformanceData::getRandreadIOPS)
            .boxed()
            .collect(Collectors.toList());
        
        List<Double> randreadLatencyP99Values = systemData.values().stream()
            .mapToDouble(SystemPerformanceData::getRandreadLatencyP99Us)
            .boxed()
            .collect(Collectors.toList());
        
        List<Double> randreadLatencyP95Values = systemData.values().stream()
            .mapToDouble(SystemPerformanceData::getRandreadLatencyP95Us)
            .boxed()
            .collect(Collectors.toList());
        
        List<Double> randreadLatencyP50Values = systemData.values().stream()
            .mapToDouble(SystemPerformanceData::getRandreadLatencyP50Us)
            .boxed()
            .collect(Collectors.toList());
        
        List<Double> randreadLatencyRatioValues = systemData.values().stream()
            .mapToDouble(SystemPerformanceData::getRandreadLatencyP99P50Ratio)
            .filter(ratio -> ratio > 0.0) // Only include valid ratios
            .boxed()
            .collect(Collectors.toList());
        
        // Collect sequential component metrics from optimal mixed workloads
        List<Double> seqreadThroughputValues = systemData.values().stream()
            .mapToDouble(SystemPerformanceData::getSeqreadThroughputMBps)
            .filter(value -> value > 0.0) // Only include systems that have sequential components
            .boxed()
            .collect(Collectors.toList());
        
        List<Double> seqwriteThroughputValues = systemData.values().stream()
            .mapToDouble(SystemPerformanceData::getSeqwriteThroughputMBps)
            .filter(value -> value > 0.0) // Only include systems that have sequential components
            .boxed()
            .collect(Collectors.toList());
        
        // Calculate randread component statistics
        double avgRandreadThroughput = randreadThroughputValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxRandreadThroughput = randreadThroughputValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double minRandreadThroughput = randreadThroughputValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        
        metrics.setAverageRandreadThroughputMBps(avgRandreadThroughput);
        metrics.setMaximumRandreadThroughputMBps(maxRandreadThroughput);
        metrics.setMinimumRandreadThroughputMBps(minRandreadThroughput);
        metrics.setRandreadThroughputRangeFactor(minRandreadThroughput > 0 ? maxRandreadThroughput / minRandreadThroughput : 0.0);
        
        double avgRandreadIOPS = randreadIOPSValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxRandreadIOPS = randreadIOPSValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        metrics.setAverageRandreadIOPS(avgRandreadIOPS);
        metrics.setMaximumRandreadIOPS(maxRandreadIOPS);
        
        // Calculate randread latency statistics
        double avgRandreadLatencyP99 = randreadLatencyP99Values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxRandreadLatencyP99 = randreadLatencyP99Values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double minRandreadLatencyP99 = randreadLatencyP99Values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        
        metrics.setAverageRandreadLatencyP99Us(avgRandreadLatencyP99);
        metrics.setBestRandreadLatencyP99Us(minRandreadLatencyP99);
        metrics.setWorstRandreadLatencyP99Us(maxRandreadLatencyP99);
        metrics.setRandreadLatencyRangeFactor(minRandreadLatencyP99 > 0 ? maxRandreadLatencyP99 / minRandreadLatencyP99 : 0.0);
        
        double avgRandreadLatencyP95 = randreadLatencyP95Values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgRandreadLatencyP50 = randreadLatencyP50Values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgRandreadLatencyRatio = randreadLatencyRatioValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        metrics.setAverageRandreadLatencyP95Us(avgRandreadLatencyP95);
        metrics.setAverageRandreadLatencyP50Us(avgRandreadLatencyP50);
        metrics.setAverageRandreadLatencyP99P50Ratio(avgRandreadLatencyRatio);
        
        // Calculate sequential component statistics
        double avgSeqreadThroughput = seqreadThroughputValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxSeqreadThroughput = seqreadThroughputValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        metrics.setAverageSeqreadThroughputMBps(avgSeqreadThroughput);
        metrics.setMaximumSeqreadThroughputMBps(maxSeqreadThroughput);
        
        double avgSeqwriteThroughput = seqwriteThroughputValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxSeqwriteThroughput = seqwriteThroughputValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        metrics.setAverageSeqwriteThroughputMBps(avgSeqwriteThroughput);
        metrics.setMaximumSeqwriteThroughputMBps(maxSeqwriteThroughput);
        
        // Find best system (highest randread throughput from mixed workload)
        String bestSystem = systemData.entrySet().stream()
            .max((a, b) -> Double.compare(a.getValue().getRandreadThroughputMBps(), b.getValue().getRandreadThroughputMBps()))
            .map(Map.Entry::getKey)
            .orElse("unknown");
        
        metrics.setBestSystemName(bestSystem);
        metrics.setBestSystemRandreadThroughputMBps(maxRandreadThroughput);
        
        // Set system names
        metrics.setSystemNames(new ArrayList<>(systemData.keySet()));
        
        return metrics;
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
        
        // Generate cross-system analysis for each non-example ranking function
        generateMultipleRankingFunctionAnalyses(report, reportPath, manifest);
        
        // Generate traditional rankings (for comparison)
        generateSystemRankings(report, allProfileData);
        
        Files.write(comparisonReportPath, report.toString().getBytes());
    }
    
    /**
     * Extracts performance data from SystemMetrics JSON file
     * All metrics come from optimal mixed workload components
     */
    private SystemPerformanceData extractPerformanceDataFromSystemReport(Path reportPath, String systemProfile, String systemName) throws IOException {
        String jsonFilename = systemProfile + "__" + systemName + ".json";
        Path systemMetricsPath = reportPath.resolve(jsonFilename);
        
        if (!Files.exists(systemMetricsPath)) {
            throw new IOException("System metrics JSON not found: " + jsonFilename);
        }
        
        // Load SystemMetrics directly from JSON
        SystemMetrics metrics = objectMapper.readValue(systemMetricsPath.toFile(), SystemMetrics.class);
        
        // Create SystemPerformanceData from the mixed workload component metrics
        return new SystemPerformanceData(systemName, systemProfile, metrics);
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
        report.append("| **Average P99 Latency** | ").append(String.format("%.3f ms", avgLatency)).append(" |\n");
        report.append("| **Best P99 Latency** | ").append(String.format("%.3f ms", minLatency)).append(" |\n");
        report.append("| **Worst P99 Latency** | ").append(String.format("%.3f ms", maxLatency)).append(" |\n");
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
            report.append(String.format("%.1f MB/s", data.getRandreadThroughputMBps())).append(" | ");
            report.append(String.format("%.1f", data.getRandreadIOPS())).append(" | ");
            report.append(String.format("%.1f ms", data.getRandreadLatencyP99Us())).append(" | ");
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
            report.append(String.format("%.1f ms", avgLatency)).append(" | ");
            report.append("`").append(bestSystem).append("` |\n");
        }
        report.append("\n");
    }
    
    /**
     * Generates cross-system analysis for each non-example ranking function
     */
    private void generateMultipleRankingFunctionAnalyses(StringBuilder report, Path reportPath, AnalysisManifest manifest) {
        Set<String> functionsToUse;
        boolean usingAllFunctions = false;
        
        // Determine which ranking functions to use
        if (rankingFunctionNames != null && !rankingFunctionNames.isEmpty()) {
            // Use the explicitly specified multiple functions
            functionsToUse = rankingFunctionNames;
        } else if (rankingFunctionName != null) {
            // Use the single specified function (backward compatibility)
            functionsToUse = Set.of(rankingFunctionName);
        } else {
            // Use all available ranking functions (default behavior)
            functionsToUse = ScoringFunction.getAllRankingFunctions();
            usingAllFunctions = true;
        }
        
        // Add note about ranking function selection when using all functions
        if (usingAllFunctions) {
            report.append("## Cross-System Analysis Overview\n\n");
            report.append("**Note**: This report includes analysis using all available ranking functions. ");
            report.append("To focus on specific ranking functions, use the `--ranking-functions` option:\n\n");
            report.append("```bash\n");
            report.append("# Single function\n");
            report.append("./analyze --ranking-functions realtime\n\n");
            report.append("# Multiple functions (comma-separated)\n");
            report.append("./analyze --ranking-functions realtime,throughput-oriented\n\n");
            report.append("# Multiple functions (multiple options)\n");
            report.append("./analyze --ranking-functions realtime --ranking-functions balanced\n");
            report.append("```\n\n");
            report.append("**Available ranking functions**: ").append(String.join(", ", functionsToUse)).append("\n\n");
            report.append("---\n\n");
        }
        
        // Generate a complete cross-system analysis for each ranking function
        for (String functionName : functionsToUse) {
            generateSingleRankingFunctionAnalysis(report, reportPath, manifest, functionName);
        }
    }
    
    /**
     * Generates cross-system analysis for a single ranking function
     */
    private void generateSingleRankingFunctionAnalysis(StringBuilder report, Path reportPath, AnalysisManifest manifest, String functionName) {
        // Load all system metrics from JSON files
        List<SystemMetrics> allSystemMetrics = new ArrayList<>();
        for (String systemProfileName : manifest.getSystemProfiles()) {
            Map<String, Path> systems = manifest.getSystemsForProfile(systemProfileName);
            
            for (String systemName : systems.keySet()) {
                String metricsFilename = systemProfileName + "__" + systemName + ".json";
                Path metricsPath = reportPath.resolve(metricsFilename);
                
                try {
                    if (Files.exists(metricsPath)) {
                        SystemMetrics metrics = objectMapper.readValue(metricsPath.toFile(), SystemMetrics.class);
                        allSystemMetrics.add(metrics);
                    }
                } catch (Exception e) {
                    System.err.println("Error loading system metrics from " + metricsFilename + ": " + e.getMessage());
                }
            }
        }
        
        if (allSystemMetrics.isEmpty()) {
            report.append("*No system metrics available for cross-system comparison.*\n\n");
            return;
        }
        
        // Generate cross-system view for this specific ranking function
        generateCrossSystemViewForRankingFunction(report, allSystemMetrics, functionName);
    }
    
    /**
     * Generates a complete cross-system view for a specific ranking function
     */
    private void generateCrossSystemViewForRankingFunction(StringBuilder report, List<SystemMetrics> allSystemMetrics, String functionName) {
        ScoringFunction scoringFunction = ScoringFunction.createFromRankingFunctions(functionName);
        List<ScoringFunction.ScoringResult> scoringResults = scoringFunction.scoreAndRankSystems(allSystemMetrics);
        
        // Create section header with proper formatting
        String sectionTitle = formatRankingFunctionName(functionName) + " Cross-System Analysis";
        report.append("## ").append(sectionTitle).append("\n\n");
        
        // Add ranking function description and interpretation
        report.append("**Description**: ").append(scoringFunction.getConfiguration().getDescription()).append("\n\n");
        generateRankingFunctionInterpretation(report, functionName, scoringFunction);
        
        // Add component breakdown
        report.append("**Scoring Components**:\n");
        for (ScoringFunction.ScoringComponent component : scoringFunction.getConfiguration().getComponents()) {
            report.append("- **").append(component.getMetricName()).append("** (weight: ").append(String.format("%.1f", component.getWeight()));
            report.append(", mapping: ").append(component.getMappingFunction());
            if (component.isInvertBetter()) {
                report.append(", lower is better");
            }
            if (component.getThresholdValue() != null) {
                report.append(", threshold: ").append(String.format("%.1f", component.getThresholdValue()));
                report.append(", penalty: ").append(String.format("%.1f", component.getThresholdPenalty()));
            }
            report.append(")\n");
        }
        report.append("\n");
        
        // Generate cross-system rankings table
        report.append("### Cross-System Rankings\n\n");
        report.append("| Rank | System | Profile | Score | Throughput | Latency | Knee Point | Component Scores |\n");
        report.append("|------|--------|---------|-------|------------|---------|------------|------------------|\n");
        
        int rank = 1;
        for (ScoringFunction.ScoringResult result : scoringResults) {
            // Find system profile for this system
            String systemProfile = allSystemMetrics.stream()
                .filter(m -> m.getSystemName().equals(result.getSystemName()))
                .map(SystemMetrics::getSystemProfile)
                .findFirst()
                .orElse("unknown");
                
            SystemMetrics metrics = allSystemMetrics.stream()
                .filter(m -> m.getSystemName().equals(result.getSystemName()))
                .findFirst()
                .orElse(null);
            
            boolean isDisqualified = result.getTotalScore() == 0.0;
            
            report.append("| ").append(rank++).append(" | ");
            
            // Annotate disqualified systems
            if (isDisqualified) {
                report.append("`").append(result.getSystemName()).append("` ❌ DISQUALIFIED | ");
            } else {
                report.append("`").append(result.getSystemName()).append("` | ");
            }
            
            report.append("**").append(systemProfile).append("** | ");
            report.append(String.format("%.3f", result.getTotalScore())).append(" | ");
            
            if (metrics != null && !isDisqualified) {
                report.append(String.format("%.1f MB/s", metrics.getRandreadThroughputMBps())).append(" | ");
                report.append(String.format("%.1f ms", metrics.getRandreadLatencyP99Us())).append(" | ");
                report.append(String.format("%.1f%%", metrics.getKneePointLatencyIncreasePercent())).append(" | ");
            } else if (isDisqualified) {
                report.append("DISQUALIFIED | DISQUALIFIED | DISQUALIFIED | ");
            } else {
                report.append("N/A | N/A | N/A | ");
            }
            
            // Add component scores in compact format
            StringBuilder details = new StringBuilder();
            if (isDisqualified) {
                details.append("Missing/zero required metrics");
            } else {
                for (Map.Entry<String, Double> componentEntry : result.getComponentScores().entrySet()) {
                    if (details.length() > 0) details.append(", ");
                    String shortMetricName = componentEntry.getKey().replace("optimal_", "").replace("_mbps", "").replace("_us", "");
                    details.append(shortMetricName).append(":").append(String.format("%.2f", componentEntry.getValue()));
                }
            }
            report.append(details.toString()).append(" |\n");
        }
        report.append("\n");
        
        // Add top performers analysis
        report.append("### Top Performers Analysis\n\n");
        for (int i = 0; i < Math.min(5, scoringResults.size()); i++) {
            ScoringFunction.ScoringResult result = scoringResults.get(i);
            String systemProfile = allSystemMetrics.stream()
                .filter(m -> m.getSystemName().equals(result.getSystemName()))
                .map(SystemMetrics::getSystemProfile)
                .findFirst()
                .orElse("unknown");
                
            report.append("**").append(i + 1).append(". ").append(result.getSystemName())
                  .append("** (").append(systemProfile).append(") - Score: ").append(String.format("%.3f", result.getTotalScore())).append("\n");
            
            // Add brief explanation of why this system scored well
            SystemMetrics metrics = allSystemMetrics.stream()
                .filter(m -> m.getSystemName().equals(result.getSystemName()))
                .findFirst()
                .orElse(null);
                
            if (metrics != null) {
                report.append("   - Throughput: ").append(String.format("%.1f MB/s", metrics.getRandreadThroughputMBps()));
                report.append(", Latency: ").append(String.format("%.1f ms", metrics.getRandreadLatencyP99Us()));
                report.append(", Consistency: ").append(String.format("%.1f%% knee-point increase", metrics.getKneePointLatencyIncreasePercent()));
                report.append("\n");
            }
        }
        report.append("\n");
        
        // Add disqualified systems explanations if any exist
        generateDisqualifiedSystemsExplanations(report, allSystemMetrics, scoringResults);
        
        // Add profile-level insights for this ranking
        generateProfileInsightsForRanking(report, allSystemMetrics, scoringResults, functionName);
        
        // Add profile average rankings
        generateProfileAverageRankings(report, allSystemMetrics, scoringResults, functionName);
        
        report.append("---\n\n");
    }
    
    /**
     * Generates detailed explanations for disqualified systems
     */
    private void generateDisqualifiedSystemsExplanations(StringBuilder report, List<SystemMetrics> allSystemMetrics,
                                                        List<ScoringFunction.ScoringResult> scoringResults) {
        // Find disqualified systems
        List<ScoringFunction.ScoringResult> disqualifiedSystems = scoringResults.stream()
            .filter(result -> result.getTotalScore() == 0.0)
            .collect(java.util.stream.Collectors.toList());
            
        if (disqualifiedSystems.isEmpty()) {
            return; // No disqualified systems to explain
        }
        
        report.append("### Disqualified Systems Analysis\n\n");
        report.append("The following systems were automatically disqualified from ranking due to missing or zero values for required metrics:\n\n");
        
        for (ScoringFunction.ScoringResult result : disqualifiedSystems) {
            // Find system profile for this system
            String systemProfile = allSystemMetrics.stream()
                .filter(m -> m.getSystemName().equals(result.getSystemName()))
                .map(SystemMetrics::getSystemProfile)
                .findFirst()
                .orElse("unknown");
                
            report.append("#### ❌ ").append(result.getSystemName()).append(" (").append(systemProfile).append(")\n\n");
            
            // Use the detailed explanation from the scoring result
            String explanation = result.getExplanation();
            if (explanation != null && !explanation.isEmpty()) {
                // Format the explanation for better readability
                String[] lines = explanation.split("\\\\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        report.append(line.trim()).append("\n");
                    }
                }
            } else {
                report.append("System disqualified due to missing or zero values for required ranking metrics.\n");
            }
            report.append("\n");
        }
        
        report.append("**Note**: Systems must have non-zero values for all metrics used in the ranking function to participate in scored rankings. ");
        report.append("Disqualified systems still appear in the rankings table for completeness but receive a score of 0.000.\n\n");
    }
    
    /**
     * Generates profile-level insights for a specific ranking function
     */
    private void generateProfileInsightsForRanking(StringBuilder report, List<SystemMetrics> allSystemMetrics, 
                                                 List<ScoringFunction.ScoringResult> scoringResults, String functionName) {
        report.append("### Profile Performance in ").append(formatRankingFunctionName(functionName)).append(" Context\n\n");
        
        // Group results by profile
        Map<String, List<ScoringFunction.ScoringResult>> profileResults = new HashMap<>();
        for (ScoringFunction.ScoringResult result : scoringResults) {
            String profile = allSystemMetrics.stream()
                .filter(m -> m.getSystemName().equals(result.getSystemName()))
                .map(SystemMetrics::getSystemProfile)
                .findFirst()
                .orElse("unknown");
            profileResults.computeIfAbsent(profile, k -> new ArrayList<>()).add(result);
        }
        
        // Analyze each profile
        report.append("| Profile | Systems | Best Score | Avg Score | Top System | Analysis |\n");
        report.append("|---------|---------|------------|-----------|------------|----------|\n");
        
        for (Map.Entry<String, List<ScoringFunction.ScoringResult>> entry : profileResults.entrySet()) {
            String profile = entry.getKey();
            List<ScoringFunction.ScoringResult> results = entry.getValue();
            
            double bestScore = results.stream().mapToDouble(ScoringFunction.ScoringResult::getTotalScore).max().orElse(0.0);
            double avgScore = results.stream().mapToDouble(ScoringFunction.ScoringResult::getTotalScore).average().orElse(0.0);
            String topSystem = results.stream()
                .max((a, b) -> Double.compare(a.getTotalScore(), b.getTotalScore()))
                .map(ScoringFunction.ScoringResult::getSystemName)
                .orElse("N/A");
            
            String analysis = generateProfileAnalysis(functionName, bestScore, avgScore, results.size());
            
            report.append("| **").append(profile).append("** | ");
            report.append(results.size()).append(" | ");
            report.append(String.format("%.3f", bestScore)).append(" | ");
            report.append(String.format("%.3f", avgScore)).append(" | ");
            report.append("`").append(topSystem).append("` | ");
            report.append(analysis).append(" |\n");
        }
        report.append("\n");
    }
    
    /**
     * Generates profile average rankings - ranks profiles by their average scores
     */
    private void generateProfileAverageRankings(StringBuilder report, List<SystemMetrics> allSystemMetrics, 
                                               List<ScoringFunction.ScoringResult> scoringResults, String functionName) {
        report.append("### System Profile Rankings by Average Score\n\n");
        report.append("*Profiles ranked by the average score of their constituent systems.*\n\n");
        
        // Group results by profile and calculate averages
        Map<String, List<ScoringFunction.ScoringResult>> profileResults = new HashMap<>();
        for (ScoringFunction.ScoringResult result : scoringResults) {
            String profile = allSystemMetrics.stream()
                .filter(m -> m.getSystemName().equals(result.getSystemName()))
                .map(SystemMetrics::getSystemProfile)
                .findFirst()
                .orElse("unknown");
            profileResults.computeIfAbsent(profile, k -> new ArrayList<>()).add(result);
        }
        
        // Calculate profile averages and create ranking entries
        List<ProfileRanking> profileRankings = new ArrayList<>();
        for (Map.Entry<String, List<ScoringFunction.ScoringResult>> entry : profileResults.entrySet()) {
            String profile = entry.getKey();
            List<ScoringFunction.ScoringResult> results = entry.getValue();
            
            double avgScore = results.stream()
                .mapToDouble(ScoringFunction.ScoringResult::getTotalScore)
                .average().orElse(0.0);
            
            double bestScore = results.stream()
                .mapToDouble(ScoringFunction.ScoringResult::getTotalScore)
                .max().orElse(0.0);
                
            String bestSystem = results.stream()
                .max((a, b) -> Double.compare(a.getTotalScore(), b.getTotalScore()))
                .map(ScoringFunction.ScoringResult::getSystemName)
                .orElse("N/A");
            
            profileRankings.add(new ProfileRanking(profile, avgScore, bestScore, bestSystem, results.size()));
        }
        
        // Sort profiles by average score (descending)
        profileRankings.sort((a, b) -> Double.compare(b.avgScore, a.avgScore));
        
        // Generate ranking table
        report.append("| Rank | Profile | Systems | Avg Score | Best Score | Top System |\n");
        report.append("|------|---------|---------|-----------|------------|------------|\n");
        
        int rank = 1;
        for (ProfileRanking pr : profileRankings) {
            report.append("| ").append(rank++).append(" | ");
            report.append("**").append(pr.profileName).append("** | ");
            report.append(pr.systemCount).append(" | ");
            report.append(String.format("%.3f", pr.avgScore)).append(" | ");
            report.append(String.format("%.3f", pr.bestScore)).append(" | ");
            report.append("`").append(pr.bestSystem).append("` |\n");
        }
        report.append("\n");
    }
    
    // Inner class for profile ranking data
    private static class ProfileRanking {
        final String profileName;
        final double avgScore;
        final double bestScore;
        final String bestSystem;
        final int systemCount;
        
        ProfileRanking(String profileName, double avgScore, double bestScore, String bestSystem, int systemCount) {
            this.profileName = profileName;
            this.avgScore = avgScore;
            this.bestScore = bestScore;
            this.bestSystem = bestSystem;
            this.systemCount = systemCount;
        }
    }
    
    /**
     * Formats ranking function name for display
     */
    private String formatRankingFunctionName(String functionName) {
        return Arrays.stream(functionName.split("[-_]"))
            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
            .collect(Collectors.joining(" "));
    }
    
    /**
     * Generates contextual analysis for a profile based on ranking function
     */
    private String generateProfileAnalysis(String functionName, double bestScore, double avgScore, int systemCount) {
        switch (functionName.toLowerCase()) {
            case "realtime":
                if (bestScore > 0.8) return "Exceptional response times";
                if (bestScore > 0.6) return "Good latency characteristics";
                return "Higher latency profile";
            case "throughput-oriented":
                if (bestScore > 0.8) return "Outstanding bandwidth capability";
                if (bestScore > 0.6) return "Strong throughput performance";
                return "Limited bandwidth potential";
            case "balanced":
                if (bestScore > 0.8) return "Excellent all-around performance";
                if (bestScore > 0.6) return "Good balanced capability";
                return "Moderate overall performance";
            case "consistency-oriented":
                if (bestScore > 0.8) return "Very predictable performance";
                if (bestScore > 0.6) return "Reasonably consistent";
                return "Variable performance characteristics";
            case "ranking-function-example":
                if (bestScore > 0.8) return "High score in example ranking";
                if (bestScore > 0.6) return "Moderate score in example";
                return "Low score in example ranking";
            default:
                if (bestScore > 0.8) return "High performance in this context";
                if (bestScore > 0.6) return "Adequate performance";
                return "Below-average performance";
        }
    }
    
    /**
     * Generates separate ranking function sections for systems within a profile
     */
    private void generateProfileRankingAnalysis(StringBuilder report, Path reportPath, String systemProfileName, AnalysisManifest manifest) {
        // Load system metrics for this profile only
        List<SystemMetrics> profileSystemMetrics = new ArrayList<>();
        Map<String, Path> systems = manifest.getSystemsForProfile(systemProfileName);
        
        for (String systemName : systems.keySet()) {
            String metricsFilename = systemProfileName + "__" + systemName + ".json";
            Path metricsPath = reportPath.resolve(metricsFilename);
            
            try {
                if (Files.exists(metricsPath)) {
                    SystemMetrics metrics = objectMapper.readValue(metricsPath.toFile(), SystemMetrics.class);
                    profileSystemMetrics.add(metrics);
                }
            } catch (Exception e) {
                System.err.println("Error loading system metrics from " + metricsFilename + ": " + e.getMessage());
            }
        }
        
        if (profileSystemMetrics.isEmpty()) {
            report.append("## Ranking Function Analysis\n\n");
            report.append("*No system metrics available for ranking analysis.*\n\n");
            return;
        }
        
        // Determine which ranking functions to use
        Set<String> functionsToUse;
        boolean usingAllFunctions = false;
        
        if (rankingFunctionNames != null && !rankingFunctionNames.isEmpty()) {
            functionsToUse = rankingFunctionNames;
        } else if (rankingFunctionName != null) {
            functionsToUse = Set.of(rankingFunctionName);
        } else {
            // Use all available ranking functions (default behavior)
            functionsToUse = ScoringFunction.getAllRankingFunctions();
            usingAllFunctions = true;
        }
        
        // Add overview note when using all functions
        if (usingAllFunctions) {
            report.append("## Ranking Function Analysis Overview\n\n");
            report.append("**Note**: This profile includes separate analysis for each available ranking function. ");
            report.append("To focus on specific functions, use the `--ranking-functions` option.\n\n");
            report.append("**Available ranking functions**: ").append(String.join(", ", functionsToUse)).append("\n\n");
            report.append("---\n\n");
        }
        
        // Generate SEPARATE SECTION for each ranking function
        for (String functionName : functionsToUse) {
            generateSeparateProfileRankingSection(report, profileSystemMetrics, functionName);
        }
    }
    
    /**
     * Generates a complete separate section for a single ranking function within a profile
     */
    private void generateSeparateProfileRankingSection(StringBuilder report, List<SystemMetrics> profileSystemMetrics, String functionName) {
        ScoringFunction scoringFunction = ScoringFunction.createFromRankingFunctions(functionName);
        List<ScoringFunction.ScoringResult> scoringResults = scoringFunction.scoreAndRankSystems(profileSystemMetrics);
        
        // Create a separate section header (H2 level)
        String sectionTitle = formatRankingFunctionName(functionName) + " Profile Analysis";
        report.append("## ").append(sectionTitle).append("\n\n");
        
        // Add ranking function description and interpretation
        report.append("**Description**: ").append(scoringFunction.getConfiguration().getDescription()).append("\n\n");
        generateRankingFunctionInterpretation(report, functionName, scoringFunction);
        
        // Add component breakdown
        report.append("**Scoring Components**:\n");
        for (ScoringFunction.ScoringComponent component : scoringFunction.getConfiguration().getComponents()) {
            report.append("- **").append(component.getMetricName()).append("** (weight: ").append(String.format("%.1f", component.getWeight()));
            report.append(", mapping: ").append(component.getMappingFunction());
            if (component.isInvertBetter()) {
                report.append(", lower is better");
            }
            if (component.getThresholdValue() != null) {
                report.append(", threshold: ").append(String.format("%.1f", component.getThresholdValue()));
                report.append(", penalty: ").append(String.format("%.1f", component.getThresholdPenalty()));
            }
            report.append(")\n");
        }
        report.append("\n");
        
        // Generate within-profile rankings table
        report.append("### System Rankings Within Profile\n\n");
        report.append("| Rank | System | Score | Throughput | Latency | Knee Point | Component Scores |\n");
        report.append("|------|--------|-------|------------|---------|-------------|------------------|\n");
        
        int rank = 1;
        for (ScoringFunction.ScoringResult result : scoringResults) {
            SystemMetrics metrics = profileSystemMetrics.stream()
                .filter(m -> m.getSystemName().equals(result.getSystemName()))
                .findFirst()
                .orElse(null);
            
            boolean isDisqualified = result.getTotalScore() == 0.0;
            
            report.append("| ").append(rank++).append(" | ");
            
            // Annotate disqualified systems
            if (isDisqualified) {
                report.append("`").append(result.getSystemName()).append("` ❌ DISQUALIFIED | ");
            } else {
                report.append("`").append(result.getSystemName()).append("` | ");
            }
            
            report.append(String.format("%.3f", result.getTotalScore())).append(" | ");
            
            if (metrics != null && !isDisqualified) {
                report.append(String.format("%.1f MB/s", metrics.getRandreadThroughputMBps())).append(" | ");
                report.append(String.format("%.1f ms", metrics.getRandreadLatencyP99Us())).append(" | ");
                report.append(String.format("%.1f%%", metrics.getKneePointLatencyIncreasePercent())).append(" | ");
            } else if (isDisqualified) {
                report.append("DISQUALIFIED | DISQUALIFIED | DISQUALIFIED | ");
            } else {
                report.append("N/A | N/A | N/A | ");
            }
            
            // Add component scores in compact format
            StringBuilder details = new StringBuilder();
            if (isDisqualified) {
                details.append("Missing/zero required metrics");
            } else {
                for (Map.Entry<String, Double> componentEntry : result.getComponentScores().entrySet()) {
                    if (details.length() > 0) details.append(", ");
                    String shortMetricName = componentEntry.getKey().replace("optimal_", "").replace("_mbps", "").replace("_us", "");
                    details.append(shortMetricName).append(":").append(String.format("%.2f", componentEntry.getValue()));
                }
            }
            report.append(details.toString()).append(" |\n");
        }
        report.append("\n");
        
        // Add top performers analysis
        if (scoringResults.size() > 1) {
            report.append("### Top Performers Analysis\n\n");
            for (int i = 0; i < Math.min(3, scoringResults.size()); i++) {
                ScoringFunction.ScoringResult result = scoringResults.get(i);
                SystemMetrics metrics = profileSystemMetrics.stream()
                    .filter(m -> m.getSystemName().equals(result.getSystemName()))
                    .findFirst()
                    .orElse(null);
                    
                report.append("**").append(i + 1).append(". ").append(result.getSystemName())
                      .append("** - Score: ").append(String.format("%.3f", result.getTotalScore())).append("\n");
                
                if (metrics != null) {
                    report.append("   - Throughput: ").append(String.format("%.1f MB/s", metrics.getRandreadThroughputMBps()));
                    report.append(", Latency: ").append(String.format("%.1f ms", metrics.getRandreadLatencyP99Us()));
                    report.append(", Consistency: ").append(String.format("%.1f%% knee-point increase", metrics.getKneePointLatencyIncreasePercent()));
                    report.append("\n");
                }
            }
            report.append("\n");
            
            // Add disqualified systems explanations if any exist
            generateDisqualifiedSystemsExplanations(report, profileSystemMetrics, scoringResults);
        }
        
        report.append("---\n\n");
    }
    
    /**
     * Generates interpretation explanation for a ranking function
     */
    private void generateRankingFunctionInterpretation(StringBuilder report, String functionName, ScoringFunction scoringFunction) {
        report.append("**Interpretation**: ");
        
        switch (functionName.toLowerCase()) {
            case "realtime":
                report.append("This ranking emphasizes response time characteristics critical for real-time systems. " +
                            "Top systems here are ideal for interactive applications, real-time processing, and latency-sensitive services.");
                break;
            case "throughput-oriented":
                report.append("This ranking prioritizes raw performance and IOPS capability. " +
                            "High-scoring systems excel in batch processing, HPC, and bandwidth-intensive workloads.");
                break;
            case "balanced":
                report.append("This balanced ranking provides equal weight to throughput, latency, and consistency (33.3% each). " +
                            "Systems scoring high here perform well across all three key dimensions.");
                break;
            case "consistency-oriented":
                report.append("This ranking values predictable performance under load. " +
                            "High-ranking systems maintain stable latency characteristics and are suitable for mission-critical applications.");
                break;
            case "ranking-function-example":
                report.append("This example ranking demonstrates all available metrics and function options. " +
                            "It shows the full range of scoring capabilities available for custom ranking functions.");
                break;
            default:
                report.append("This custom ranking function evaluates systems according to the specific component weights and thresholds defined.");
                break;
        }
        
        report.append("\n\n");
    }
    
    /**
     * Generates system rankings across all profiles (traditional throughput-based)
     */
    private void generateSystemRankings(StringBuilder report, Map<String, Map<String, SystemPerformanceData>> allProfileData) {
        report.append("## Traditional System Rankings (Throughput-Based)\n\n");
        report.append("*The following rankings are based purely on throughput for comparison with the scored rankings above.*\n\n");
        
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
            report.append(String.format("%.1f MB/s (%.2f GB/s)", system.getRandreadThroughputMBps(), system.getRandreadThroughputGBps())).append(" | ");
            report.append(String.format("%.1f ms", system.getRandreadLatencyP99Us())).append(" | ");
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
    
    /**
     * Extracts P99 latency from the randread component of a mixed workload
     */
    private double extractRandreadP99Latency(WorkloadAnalyzer.WorkloadResult mixed) throws Exception {
        FioResult.FioMetrics metrics = extractRandreadMetrics(mixed);
        if (metrics != null && metrics.getCompletionLatency() != null) {
            return metrics.getCompletionLatency().getP99() / 1000000.0; // Convert to milliseconds
        }
        throw new Exception("No read metrics found in mixed workload");
    }
    
    /**
     * Extracts FioMetrics from the randread component of a mixed workload
     */
    private FioResult.FioMetrics extractRandreadMetrics(WorkloadAnalyzer.WorkloadResult mixed) {
        // Find the randread job in the mixed workload
        for (FioResult.FioJob job : mixed.getFioResult().getJobs()) {
            String jobName = job.getJobname();
            if (jobName != null && jobName.toLowerCase().contains("randread")) {
                FioResult.FioMetrics readMetrics = job.getRead();
                if (readMetrics != null) {
                    return readMetrics;
                }
            }
        }
        
        // Fallback: if no randread job found, use the first job with read metrics
        if (!mixed.getFioResult().getJobs().isEmpty()) {
            FioResult.FioMetrics readMetrics = mixed.getFioResult().getJobs().get(0).getRead();
            if (readMetrics != null) {
                return readMetrics;
            }
        }
        
        return null;
    }
    
    /**
     * Extracts the stream limit from a mixed workload filename
     * Examples:
     * - mixed-301-8to16k_10Mseq.fio.json -> "10M"
     * - mixed-302-8to16k_20Mseq.fio.json -> "20M"
     * - mixed-303-8to16k_uncapped.fio.json -> null
     */
    private String extractStreamLimitFromWorkloadName(String filename) {
        if (filename == null || !filename.startsWith("mixed-")) {
            return null;
        }
        
        // Check if it's uncapped
        if (filename.contains("uncapped")) {
            return null;
        }
        
        // Extract the streaming limit (e.g., "10Mseq" -> "10M")
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("_(\\d+)Mseq");
        java.util.regex.Matcher matcher = pattern.matcher(filename);
        
        if (matcher.find()) {
            return matcher.group(1) + "M";
        }
        
        return null;
    }
    
    /**
     * Parses a stream limit string to MB/s value
     * Examples:
     * - "10M" -> 10.0
     * - "20M" -> 20.0
     * - "100M" -> 100.0
     */
    private double parseStreamLimit(String streamLimit) {
        if (streamLimit == null) {
            throw new NumberFormatException("Stream limit is null");
        }
        
        // Remove the 'M' suffix if present
        String numericPart = streamLimit.replaceAll("[Mm]$", "");
        
        return Double.parseDouble(numericPart);
    }
}