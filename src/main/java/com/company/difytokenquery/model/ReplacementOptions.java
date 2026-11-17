package com.company.difytokenquery.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReplacementOptions {
    public enum ParamStrategy {
        KEEP_COMMON("仅保留通用参数 (temperature/top_p/max_tokens/stop)"),
        CLEAR_ALL("清空全部模型参数"),
        KEEP_ALL("保留全部原模型参数");

        private final String displayName;

        ParamStrategy(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum ThinkingMode {
        FOLLOW_STRATEGY("跟随参数策略 (不额外修改)"),
        DISABLE("关闭思考 (enable_thinking=false)"),
        ENABLE_DEFAULT("开启思考 (enable_thinking=true)"),
        ENABLE_WITH_EFFORT("开启思考 + 指定强度"),
        OMIT("移除思考参数 (不传入)");

        private final String displayName;

        ThinkingMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static class ThinkingConfig {
        public static final List<String> DEFAULT_REASONING_EFFORTS = Collections.unmodifiableList(Arrays.asList(
                "medium", "low", "high", "minimal", "none", "auto", "default",
                "light", "xhigh", "extra_high", "max", "maximum", "adaptive", "disable", "disabled"
        ));

        private final ThinkingMode mode;
        private final String reasoningEffort;

        public ThinkingConfig(ThinkingMode mode, String reasoningEffort) {
            this.mode = mode == null ? ThinkingMode.FOLLOW_STRATEGY : mode;
            this.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim();
        }

        public static ThinkingConfig followStrategy() {
            return new ThinkingConfig(ThinkingMode.FOLLOW_STRATEGY, "");
        }

        public ThinkingMode getMode() {
            return mode;
        }

        public String getReasoningEffort() {
            return reasoningEffort;
        }

        public String getSummaryLabel() {
            if (mode == ThinkingMode.ENABLE_WITH_EFFORT) {
                return "开启思考(" + (reasoningEffort.isEmpty() ? "medium" : reasoningEffort) + ")";
            }
            return mode.getDisplayName();
        }
    }

    public static class ModelMappingRule {
        private final String sourceProvider;
        private final String sourceModelName;
        private final DifyModelOption targetModel;

        public ModelMappingRule(String sourceProvider, String sourceModelName, DifyModelOption targetModel) {
            this.sourceProvider = sourceProvider == null ? "" : sourceProvider.trim();
            this.sourceModelName = sourceModelName == null ? "" : sourceModelName.trim();
            this.targetModel = targetModel;
        }

        public String getSourceProvider() {
            return sourceProvider;
        }

        public String getSourceModelName() {
            return sourceModelName;
        }

        public DifyModelOption getTargetModel() {
            return targetModel;
        }

        public boolean matches(String provider, String modelName) {
            return sourceProvider.equals(provider == null ? "" : provider.trim())
                    && sourceModelName.equals(modelName == null ? "" : modelName.trim());
        }

        public String getRuleKey() {
            return sourceProvider + "::" + sourceModelName;
        }

        @Override
        public String toString() {
            String src = sourceProvider + ":" + sourceModelName;
            String dst = targetModel == null ? "" : targetModel.getProvider() + ":" + targetModel.getModelName();
            return src + " -> " + dst;
        }
    }

    public static class NodeTargetOverride {
        private final boolean enabled;
        private final DifyModelOption targetModel;
        private final ParamStrategy paramStrategy;
        private final ThinkingConfig thinkingConfig;

        public NodeTargetOverride(boolean enabled, DifyModelOption targetModel, ParamStrategy paramStrategy) {
            this(enabled, targetModel, paramStrategy, null);
        }

        public NodeTargetOverride(boolean enabled, DifyModelOption targetModel,
                                  ParamStrategy paramStrategy, ThinkingConfig thinkingConfig) {
            this.enabled = enabled;
            this.targetModel = targetModel;
            this.paramStrategy = paramStrategy;
            this.thinkingConfig = thinkingConfig;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public DifyModelOption getTargetModel() {
            return targetModel;
        }

        public ParamStrategy getParamStrategy() {
            return paramStrategy;
        }

        public ThinkingConfig getThinkingConfig() {
            return thinkingConfig;
        }
    }

    private final String sourceProvider;
    private final String sourceModelName;
    private final boolean replaceAllNodes;
    private final DifyModelOption targetModel;
    private final ParamStrategy paramStrategy;
    private final ThinkingConfig thinkingConfig;
    private final boolean autoPublishWorkflow;
    private final List<ModelMappingRule> mappingRules;
    private final Map<String, Map<String, NodeTargetOverride>> nodeOverrides;

    public ReplacementOptions(String sourceProvider, String sourceModelName, boolean replaceAllNodes,
                              DifyModelOption targetModel, ParamStrategy paramStrategy,
                              boolean autoPublishWorkflow) {
        this(sourceProvider, sourceModelName, replaceAllNodes, targetModel, paramStrategy,
                ThinkingConfig.followStrategy(), autoPublishWorkflow,
                Collections.<ModelMappingRule>emptyList(),
                Collections.<String, Map<String, NodeTargetOverride>>emptyMap());
    }

    public ReplacementOptions(String sourceProvider, String sourceModelName, boolean replaceAllNodes,
                              DifyModelOption targetModel, ParamStrategy paramStrategy,
                              boolean autoPublishWorkflow,
                              List<ModelMappingRule> mappingRules,
                              Map<String, Map<String, NodeTargetOverride>> nodeOverrides) {
        this(sourceProvider, sourceModelName, replaceAllNodes, targetModel, paramStrategy,
                ThinkingConfig.followStrategy(), autoPublishWorkflow, mappingRules, nodeOverrides);
    }

    public ReplacementOptions(String sourceProvider, String sourceModelName, boolean replaceAllNodes,
                              DifyModelOption targetModel, ParamStrategy paramStrategy,
                              ThinkingConfig thinkingConfig,
                              boolean autoPublishWorkflow,
                              List<ModelMappingRule> mappingRules,
                              Map<String, Map<String, NodeTargetOverride>> nodeOverrides) {
        this.sourceProvider = sourceProvider == null ? "" : sourceProvider.trim();
        this.sourceModelName = sourceModelName == null ? "" : sourceModelName.trim();
        this.replaceAllNodes = replaceAllNodes;
        this.targetModel = targetModel;
        this.paramStrategy = paramStrategy == null ? ParamStrategy.KEEP_COMMON : paramStrategy;
        this.thinkingConfig = thinkingConfig == null ? ThinkingConfig.followStrategy() : thinkingConfig;
        this.autoPublishWorkflow = autoPublishWorkflow;
        this.mappingRules = mappingRules == null
                ? Collections.<ModelMappingRule>emptyList()
                : Collections.unmodifiableList(new ArrayList<ModelMappingRule>(mappingRules));
        if (nodeOverrides == null || nodeOverrides.isEmpty()) {
            this.nodeOverrides = Collections.emptyMap();
        } else {
            Map<String, Map<String, NodeTargetOverride>> copy = new LinkedHashMap<String, Map<String, NodeTargetOverride>>();
            for (Map.Entry<String, Map<String, NodeTargetOverride>> entry : nodeOverrides.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<String, NodeTargetOverride>(entry.getValue())));
                }
            }
            this.nodeOverrides = Collections.unmodifiableMap(copy);
        }
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public String getSourceModelName() {
        return sourceModelName;
    }

    public boolean isReplaceAllNodes() {
        return replaceAllNodes;
    }

    public DifyModelOption getTargetModel() {
        return targetModel;
    }

    public ParamStrategy getParamStrategy() {
        return paramStrategy;
    }

    public ThinkingConfig getThinkingConfig() {
        return thinkingConfig;
    }

    public boolean isAutoPublishWorkflow() {
        return autoPublishWorkflow;
    }

    public List<ModelMappingRule> getMappingRules() {
        return mappingRules;
    }

    public Map<String, Map<String, NodeTargetOverride>> getNodeOverrides() {
        return nodeOverrides;
    }

    public NodeTargetOverride getNodeOverride(String appId, String slotKey) {
        if (appId == null || slotKey == null) {
            return null;
        }
        Map<String, NodeTargetOverride> appMap = nodeOverrides.get(appId);
        return appMap == null ? null : appMap.get(slotKey);
    }

    public boolean hasOverridesForApp(String appId) {
        Map<String, NodeTargetOverride> appMap = nodeOverrides.get(appId);
        return appMap != null && !appMap.isEmpty();
    }
}