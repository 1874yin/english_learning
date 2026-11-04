package com.xiaojie.englishlearning.migration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ImportManifest {
    private final String manifestId;
    private final String status;
    private final Map<String, Integer> sourceCounts;
    private final Map<String, String> sourceHashes;
    private final Map<String, Integer> destinationCounts;
    private final String validation;

    public ImportManifest(String manifestId, String status, Map<String, Integer> sourceCounts,
            Map<String, String> sourceHashes, Map<String, Integer> destinationCounts, String validation) {
        this.manifestId = manifestId;
        this.status = status;
        this.sourceCounts = immutable(sourceCounts);
        this.sourceHashes = Collections.unmodifiableMap(new LinkedHashMap<>(sourceHashes));
        this.destinationCounts = immutable(destinationCounts);
        this.validation = validation;
    }

    public String getManifestId() { return manifestId; }
    public String getStatus() { return status; }
    public Map<String, Integer> getSourceCounts() { return sourceCounts; }
    public Map<String, String> getSourceHashes() { return sourceHashes; }
    public Map<String, Integer> getDestinationCounts() { return destinationCounts; }
    public String getValidation() { return validation; }

    private static Map<String, Integer> immutable(Map<String, Integer> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
