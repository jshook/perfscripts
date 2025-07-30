package com.jshook.analysis.workload;

import java.nio.file.Path;

/**
 * Represents a single workload file (*.fio.json) with parsed metadata
 */
public class WorkloadFile {
    private final Path path;
    private final String workloadType;
    private final String testId;
    private final String parameter;
    private final int series;
    
    public WorkloadFile(Path path, String workloadType, String testId, String parameter) {
        this.path = path;
        this.workloadType = workloadType;
        this.testId = testId;
        this.parameter = parameter;
        this.series = extractSeries(testId);
    }
    
    private int extractSeries(String testId) {
        try {
            return Integer.parseInt(testId) / 100;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    public Path getPath() { return path; }
    public String getWorkloadType() { return workloadType; }
    public String getTestId() { return testId; }
    public String getParameter() { return parameter; }
    public int getSeries() { return series; }
    
    /**
     * Parses workload filename to extract metadata
     * Examples:
     * - randread-000-512.fio.json -> type=randread, id=000, param=512
     * - mixed-301-1to4k_10Mseq.fio.json -> type=mixed, id=301, param=1to4k_10Mseq
     */
    public static WorkloadFile parseFromPath(Path path) {
        String filename = path.getFileName().toString();
        if (!filename.endsWith(".fio.json")) {
            throw new IllegalArgumentException("Not a fio.json file: " + filename);
        }
        
        String baseName = filename.substring(0, filename.length() - 9); // Remove .fio.json
        String[] parts = baseName.split("-", 3);
        
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid workload filename format: " + filename);
        }
        
        return new WorkloadFile(path, parts[0], parts[1], parts[2]);
    }
    
    @Override
    public String toString() {
        return String.format("WorkloadFile{type=%s, id=%s, param=%s, series=%d}", 
                workloadType, testId, parameter, series);
    }
}