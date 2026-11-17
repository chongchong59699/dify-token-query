package com.company.difytokenquery.model;

import java.util.Objects;

public class DifyModelOption {
    private final String provider;
    private final String providerLabel;
    private final String modelName;
    private final String modelLabel;
    private final String modelMode;
    private final String modelType;
    private final String status;
    private final String fetchFrom;

    public DifyModelOption(String provider, String providerLabel, String modelName, String modelLabel,
                           String modelMode, String modelType, String status, String fetchFrom) {
        this.provider = nullToEmpty(provider);
        this.providerLabel = nullToEmpty(providerLabel);
        this.modelName = nullToEmpty(modelName);
        this.modelLabel = nullToEmpty(modelLabel);
        this.modelMode = nullToEmpty(modelMode);
        this.modelType = nullToEmpty(modelType);
        this.status = nullToEmpty(status);
        this.fetchFrom = nullToEmpty(fetchFrom);
    }

    public static DifyModelOption simple(String provider, String modelName, String modelMode) {
        return new DifyModelOption(provider, provider, modelName, modelName, modelMode, "llm", "active", "");
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderLabel() {
        return providerLabel;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelLabel() {
        return modelLabel;
    }

    public String getModelMode() {
        return modelMode;
    }

    public String getModelType() {
        return modelType;
    }

    public String getStatus() {
        return status;
    }

    public String getFetchFrom() {
        return fetchFrom;
    }

    public String getKey() {
        return provider + "::" + modelName;
    }

    public String getShortProvider() {
        int idx = provider.lastIndexOf('/');
        return idx >= 0 && idx + 1 < provider.length() ? provider.substring(idx + 1) : provider;
    }

    public boolean isSameModel(String otherProvider, String otherModelName) {
        return Objects.equals(provider, nullToEmpty(otherProvider))
                && Objects.equals(modelName, nullToEmpty(otherModelName));
    }

    public String getDisplayText() {
        String p = providerLabel.isEmpty() ? getShortProvider() : providerLabel;
        String m = modelLabel.isEmpty() || modelLabel.equals(modelName)
                ? modelName
                : modelLabel + " (" + modelName + ")";
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(p).append("] ").append(m);
        if (!modelMode.isEmpty()) {
            sb.append(" <").append(modelMode).append(">");
        }
        if (!provider.equals(p) && !provider.isEmpty()) {
            sb.append(" — ").append(provider);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getDisplayText();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyModelOption)) return false;
        DifyModelOption that = (DifyModelOption) o;
        return Objects.equals(provider, that.provider) && Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, modelName);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}