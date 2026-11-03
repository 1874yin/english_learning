package com.xiaojie.englishlearning.translation;

/**
 * Snapshot returned by the admin translation status endpoint.
 */
public class TranslationStatus {

    private final boolean enabled;
    private final String provider;
    private final String model;
    private final boolean stateCurrent;
    private final String storedProvider;
    private final Integer storedVersion;
    private final int totalLessons;
    private final int totalLines;
    private final int translatedLines;
    private final int missingLines;
    private final int translatedLessons;
    private final int missingLessons;

    public TranslationStatus(boolean enabled, String provider, String model, boolean stateCurrent,
            String storedProvider, Integer storedVersion, int totalLessons, int totalLines,
            int translatedLines, int missingLines, int translatedLessons, int missingLessons) {
        this.enabled = enabled;
        this.provider = provider;
        this.model = model;
        this.stateCurrent = stateCurrent;
        this.storedProvider = storedProvider;
        this.storedVersion = storedVersion;
        this.totalLessons = totalLessons;
        this.totalLines = totalLines;
        this.translatedLines = translatedLines;
        this.missingLines = missingLines;
        this.translatedLessons = translatedLessons;
        this.missingLessons = missingLessons;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public boolean isStateCurrent() {
        return stateCurrent;
    }

    public String getStoredProvider() {
        return storedProvider;
    }

    public Integer getStoredVersion() {
        return storedVersion;
    }

    public int getTotalLessons() {
        return totalLessons;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public int getTranslatedLines() {
        return translatedLines;
    }

    public int getMissingLines() {
        return missingLines;
    }

    public int getTranslatedLessons() {
        return translatedLessons;
    }

    public int getMissingLessons() {
        return missingLessons;
    }
}
