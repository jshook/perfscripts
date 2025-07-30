package com.jshook.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the manifest file containing system profile groups with system names and paths
 */
public class AnalysisManifest {
    private final Map<String, SystemProfile> systemProfileGroups;
    
    public AnalysisManifest(Map<String, SystemProfile> systemProfileGroups) {
        this.systemProfileGroups = systemProfileGroups;
    }
    
    public Map<String, SystemProfile> getSystemProfileGroups() {
        return systemProfileGroups;
    }
    
    /**
     * Returns list of all system profiles (group names)
     */
    public List<String> getSystemProfiles() {
        return systemProfileGroups.keySet().stream().sorted().collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Returns systems for a specific system profile
     */
    public Map<String, Path> getSystemsForProfile(String systemProfile) {
        SystemProfile profile = systemProfileGroups.get(systemProfile);
        return profile != null ? profile.getSystemPaths() : java.util.Collections.emptyMap();
    }
    
    /**
     * Returns system profile object for a specific profile
     */
    public SystemProfile getSystemProfile(String systemProfileName) {
        return systemProfileGroups.get(systemProfileName);
    }
    
    /**
     * Returns directories for a specific profile (backward compatibility)
     */
    public List<Path> getDirectoriesForProfile(String profile) {
        return getSystemsForProfile(profile).values().stream().collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Returns total number of directories containing fio.json files
     */
    public int getTotalDirectories() {
        return systemProfileGroups.values().stream().mapToInt(profile -> profile.getSystemPaths().size()).sum();
    }
    
    /**
     * Returns list of all profiles (backward compatibility)
     */
    public List<String> getProfiles() {
        return getSystemProfiles();
    }
    
    /**
     * Inner class to represent a system profile with separate names and paths
     */
    public static class SystemProfile {
        private final Map<String, Path> systemPaths;
        private final Path systemProfilePath;
        
        public SystemProfile(Map<String, Path> systemPaths, Path systemProfilePath) {
            this.systemPaths = systemPaths;
            this.systemProfilePath = systemProfilePath;
        }
        
        public Map<String, Path> getSystemPaths() {
            return systemPaths;
        }
        
        public Set<String> getSystemNames() {
            return systemPaths.keySet();
        }
        
        public Path getSystemProfilePath() {
            return systemProfilePath;
        }
        
        @JsonProperty("systemProfilePath")
        public String getSystemProfilePathString() {
            return systemProfilePath.toString();
        }
    }
}