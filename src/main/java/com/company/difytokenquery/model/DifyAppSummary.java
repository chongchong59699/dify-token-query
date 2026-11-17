package com.company.difytokenquery.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DifyAppSummary {
    private final String appId;
    private final String appName;
    private final String mode;
    private final String status;
    private final String appModelConfigId;
    private final String publishedWorkflowId;
    private final boolean hasDraftWorkflow;
    private final String configurationUrl;
    private final List<String> apiTokens;
    private final List<ModelRef> modelRefs;

    public DifyAppSummary(String appId, String appName, String mode, String status,
                          String appModelConfigId, String publishedWorkflowId, boolean hasDraftWorkflow,
                          String configurationUrl, List<String> apiTokens, List<ModelRef> modelRefs) {
        this.appId = nullToEmpty(appId);
        this.appName = nullToEmpty(appName);
        this.mode = nullToEmpty(mode);
        this.status = nullToEmpty(status);
        this.appModelConfigId = nullToEmpty(appModelConfigId);
        this.publishedWorkflowId = nullToEmpty(publishedWorkflowId);
        this.hasDraftWorkflow = hasDraftWorkflow;
        this.configurationUrl = nullToEmpty(configurationUrl);
        this.apiTokens = apiTokens == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(apiTokens));
        this.modelRefs = modelRefs == null
                ? Collections.<ModelRef>emptyList()
                : Collections.unmodifiableList(new ArrayList<ModelRef>(modelRefs));
    }

    public String getAppId() {
        return appId;
    }

    public String getAppName() {
        return appName;
    }

    public String getMode() {
        return mode;
    }

    public String getStatus() {
        return status;
    }

    public String getAppModelConfigId() {
        return appModelConfigId;
    }

    public String getPublishedWorkflowId() {
        return publishedWorkflowId;
    }

    public boolean isHasDraftWorkflow() {
        return hasDraftWorkflow;
    }

    public boolean isWorkflowMode() {
        return "workflow".equalsIgnoreCase(mode) || "advanced-chat".equalsIgnoreCase(mode);
    }

    public boolean hasPublishedWorkflow() {
        return !publishedWorkflowId.isEmpty();
    }

    public String getConfigurationUrl() {
        return configurationUrl;
    }

    public List<String> getApiTokens() {
        return apiTokens;
    }

    public List<ModelRef> getModelRefs() {
        return modelRefs;
    }

    public String getTokensSummary() {
        return String.join(", ", apiTokens);
    }

    public String getModelsSummary() {
        if (modelRefs.isEmpty()) {
            return "";
        }
        Set<String> unique = new LinkedHashSet<String>();
        for (ModelRef ref : modelRefs) {
            String shortProvider = ref.getProvider();
            int idx = shortProvider.lastIndexOf('/');
            if (idx >= 0 && idx + 1 < shortProvider.length()) {
                shortProvider = shortProvider.substring(idx + 1);
            }
            unique.add(shortProvider + ":" + ref.getModelName());
        }
        return String.join(", ", unique);
    }

    public boolean usesModel(String provider, String modelName) {
        for (ModelRef ref : modelRefs) {
            if (ref.matches(provider, modelName)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static class ModelRef {
        private final String slotLabel;
        private final String nodeId;
        private final String nodeType;
        private final String provider;
        private final String modelName;
        private final String modelMode;
        private final String completionParamsSummary;

        public ModelRef(String slotLabel, String nodeId, String nodeType,
                        String provider, String modelName, String modelMode,
                        String completionParamsSummary) {
            this.slotLabel = nullToEmpty(slotLabel);
            this.nodeId = nullToEmpty(nodeId);
            this.nodeType = nullToEmpty(nodeType);
            this.provider = nullToEmpty(provider);
            this.modelName = nullToEmpty(modelName);
            this.modelMode = nullToEmpty(modelMode);
            this.completionParamsSummary = nullToEmpty(completionParamsSummary);
        }

        public String getSlotLabel() {
            return slotLabel;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getNodeType() {
            return nodeType;
        }

        public String getProvider() {
            return provider;
        }

        public String getModelName() {
            return modelName;
        }

        public String getModelMode() {
            return modelMode;
        }

        public String getCompletionParamsSummary() {
            return completionParamsSummary;
        }

        public String getSlotKey() {
            if ("app_model_config".equals(nodeType)) {
                return "app_model_config";
            }
            String idPart = nodeId.isEmpty() ? slotLabel : nodeId;
            return idPart + "::" + nodeType;
        }

        public boolean matches(String targetProvider, String targetModelName) {
            return provider.equals(nullToEmpty(targetProvider))
                    && modelName.equals(nullToEmpty(targetModelName));
        }
    }
}