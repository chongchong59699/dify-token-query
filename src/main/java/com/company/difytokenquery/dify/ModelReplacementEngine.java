package com.company.difytokenquery.dify;

import com.company.difytokenquery.model.DifyAppSummary;
import com.company.difytokenquery.model.DifyAppSummary.ModelRef;
import com.company.difytokenquery.model.DifyModelOption;
import com.company.difytokenquery.model.ModelSlotChange;
import com.company.difytokenquery.model.ReplacementOptions;
import com.company.difytokenquery.model.ReplacementOptions.ModelMappingRule;
import com.company.difytokenquery.model.ReplacementOptions.NodeTargetOverride;
import com.company.difytokenquery.model.ReplacementOptions.ParamStrategy;
import com.company.difytokenquery.model.ReplacementOptions.ThinkingConfig;
import com.company.difytokenquery.model.ReplacementOptions.ThinkingMode;
import com.company.difytokenquery.model.ReplacementPlanItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Extracts model references and applies model replacements for Dify apps and workflows. */
public class ModelReplacementEngine {
    private static final Set<String> COMMON_COMPLETION_PARAMS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("temperature", "top_p", "max_tokens", "stop"))
    );
    private static final Set<String> NON_INPUT_MODEL_CONFIG_KEYS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("id", "app_id", "created_at", "updated_at", "created_by", "updated_by"))
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ModelRef> extractAppModelConfigRefs(String modelJsonText) {
        List<ModelRef> refs = new ArrayList<ModelRef>();
        if (isBlank(modelJsonText)) {
            return refs;
        }
        try {
            JsonNode root = objectMapper.readTree(modelJsonText);
            if (root != null && root.isObject()) {
                ModelRef ref = readStandardModelNode("主模型配置", "", "app_model_config", root);
                if (ref != null) {
                    refs.add(ref);
                }
            }
        } catch (IOException ignored) {
            // Ignore malformed legacy rows during inventory scan.
        }
        return refs;
    }

    public List<ModelRef> extractCompactWorkflowRefs(String compactArrayJson) {
        List<ModelRef> refs = new ArrayList<ModelRef>();
        if (isBlank(compactArrayJson)) {
            return refs;
        }
        try {
            JsonNode arr = objectMapper.readTree(compactArrayJson);
            if (arr == null || !arr.isArray()) {
                return refs;
            }
            for (JsonNode item : arr) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                String nodeId = textOf(item, "id");
                String nodeType = textOf(item, "type");
                String title = textOf(item, "title");
                String displayLabel = isBlank(title) ? nodeType : title + " (" + nodeType + ")";
                JsonNode modelNode = item.get("model");
                if ("agent".equals(nodeType)) {
                    ModelRef ref = readAgentModelNode(displayLabel, nodeId, nodeType, modelNode);
                    if (ref != null) {
                        refs.add(ref);
                    }
                } else if (nodeType.startsWith("knowledge-retrieval")) {
                    ModelRef ref = readStandardModelNode(displayLabel + " [单路召回]", nodeId, nodeType + ".single_retrieval", modelNode);
                    if (ref != null) {
                        refs.add(ref);
                    }
                } else {
                    ModelRef ref = readStandardModelNode(displayLabel, nodeId, nodeType, modelNode);
                    if (ref != null) {
                        refs.add(ref);
                    }
                }
            }
        } catch (IOException ignored) {
            // Ignore malformed rows during inventory scan.
        }
        return refs;
    }

    public List<ModelRef> extractWorkflowGraphRefs(String graphJsonText) {
        List<ModelRef> refs = new ArrayList<ModelRef>();
        if (isBlank(graphJsonText)) {
            return refs;
        }
        try {
            JsonNode root = objectMapper.readTree(graphJsonText);
            if (root == null || !root.isObject()) {
                return refs;
            }
            JsonNode nodes = root.get("nodes");
            if (nodes == null || !nodes.isArray()) {
                return refs;
            }
            for (JsonNode node : nodes) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                String nodeId = textOf(node, "id");
                JsonNode data = node.get("data");
                if (data == null || !data.isObject()) {
                    continue;
                }
                String nodeType = textOf(data, "type");
                String title = textOf(data, "title");
                String displayLabel = isBlank(title) ? nodeType : title + " (" + nodeType + ")";

                if ("llm".equals(nodeType) || "parameter-extractor".equals(nodeType) || "question-classifier".equals(nodeType)) {
                    ModelRef ref = readStandardModelNode(displayLabel, nodeId, nodeType, data.get("model"));
                    if (ref != null) {
                        refs.add(ref);
                    }
                } else if ("agent".equals(nodeType)) {
                    JsonNode agentModel = data.path("agent_parameters").path("model").path("value");
                    ModelRef ref = readAgentModelNode(displayLabel, nodeId, nodeType, agentModel);
                    if (ref != null) {
                        refs.add(ref);
                    }
                } else if (nodeType.startsWith("knowledge-retrieval")) {
                    JsonNode singleModel = data.path("single_retrieval_config").path("model");
                    ModelRef ref = readStandardModelNode(displayLabel + " [单路召回]", nodeId, nodeType + ".single_retrieval", singleModel);
                    if (ref != null) {
                        refs.add(ref);
                    }
                }
            }
        } catch (IOException ignored) {
            // Ignore malformed graph during inventory scan.
        }
        return refs;
    }

    public List<ReplacementPlanItem> buildPlan(List<DifyAppSummary> selectedApps, ReplacementOptions options) {
        List<ReplacementPlanItem> plan = new ArrayList<ReplacementPlanItem>();
        if (selectedApps == null || options == null) {
            return plan;
        }
        for (DifyAppSummary app : selectedApps) {
            plan.add(planOneApp(app, options));
        }
        return plan;
    }

    public ReplacementPlanItem planOneApp(DifyAppSummary app, ReplacementOptions options) {
        if (app == null) {
            return new ReplacementPlanItem(null, Collections.<ModelSlotChange>emptyList(), "应用信息为空");
        }
        if (options == null) {
            return new ReplacementPlanItem(app, Collections.<ModelSlotChange>emptyList(), "未指定替换规则");
        }
        if (app.getModelRefs().isEmpty()) {
            return new ReplacementPlanItem(app, Collections.<ModelSlotChange>emptyList(), "未检测到可替换的 LLM 模型配置");
        }
        boolean hasAnyConfig = options.getTargetModel() != null
                || !options.getMappingRules().isEmpty()
                || options.hasOverridesForApp(app.getAppId());
        if (!hasAnyConfig) {
            return new ReplacementPlanItem(app, Collections.<ModelSlotChange>emptyList(), "未选择目标模型或配置映射规则");
        }

        List<ModelSlotChange> changes = new ArrayList<ModelSlotChange>();
        for (ModelRef ref : app.getModelRefs()) {
            ResolvedTarget resolved = resolveTargetForSlot(app.getAppId(), ref.getSlotKey(), ref.getProvider(), ref.getModelName(), options);
            if (resolved == null) {
                continue;
            }
            DifyModelOption target = resolved.targetModel;
            String afterMode = resolveTargetMode(target.getModelMode(), ref.getModelMode());
            String afterParams = summarizeParamsAfterStrategy(ref.getCompletionParamsSummary(), resolved.paramStrategy, resolved.thinkingConfig);
            changes.add(new ModelSlotChange(
                    ref.getSlotLabel(),
                    ref.getNodeType(),
                    ref.getNodeId(),
                    ref.getProvider(),
                    ref.getModelName(),
                    ref.getModelMode(),
                    target.getProvider(),
                    target.getModelName(),
                    afterMode,
                    ref.getCompletionParamsSummary(),
                    afterParams
            ));
        }

        if (changes.isEmpty()) {
            return new ReplacementPlanItem(app, changes, "未命中目标替换条件（未匹配原模型、已是目标模型或节点已被取消勾选）");
        }
        return new ReplacementPlanItem(app, changes, "");
    }

    public ApplyOutcome applyToAppDetail(JsonNode appDetailResponse, ReplacementOptions options) {
        return applyToAppDetail("", appDetailResponse, options);
    }

    /** Builds POST /console/api/apps/{appId}/model-config payload from GET /console/api/apps/{appId} response. */
    public ApplyOutcome applyToAppDetail(String appId, JsonNode appDetailResponse, ReplacementOptions options) {
        if (appDetailResponse == null || !appDetailResponse.isObject()) {
            return ApplyOutcome.noChange("应用详情响应格式无效", null);
        }
        String effectiveAppId = !isBlank(appId) ? appId : textOf(appDetailResponse, "id");
        JsonNode modelConfigNode = appDetailResponse.get("model_config");
        if (modelConfigNode == null || !modelConfigNode.isObject()) {
            return ApplyOutcome.noChange("应用缺少 model_config 字段", null);
        }

        ObjectNode updatedConfig = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = modelConfigNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!NON_INPUT_MODEL_CONFIG_KEYS.contains(entry.getKey())) {
                updatedConfig.set(entry.getKey(), entry.getValue().deepCopy());
            }
        }

        JsonNode modelNode = updatedConfig.get("model");
        if (modelNode == null || !modelNode.isObject()) {
            return ApplyOutcome.noChange("model_config 中缺少 model 对象", null);
        }

        ModelSlotChange change = mutateStandardModelObject(
                effectiveAppId, "app_model_config", "主模型配置", "", "app_model_config",
                (ObjectNode) modelNode, options
        );
        if (change == null) {
            return ApplyOutcome.noChange("当前线上模型配置未命中替换条件", updatedConfig);
        }
        return ApplyOutcome.changed(Collections.singletonList(change), updatedConfig);
    }

    public ApplyOutcome applyToWorkflowDraft(JsonNode draftResponse, ReplacementOptions options) {
        return applyToWorkflowDraft("", draftResponse, options);
    }

    /** Builds POST /console/api/apps/{appId}/workflows/draft payload from GET /workflows/draft response. */
    public ApplyOutcome applyToWorkflowDraft(String appId, JsonNode draftResponse, ReplacementOptions options) {
        if (draftResponse == null || !draftResponse.isObject()) {
            return ApplyOutcome.noChange("工作流草稿响应格式无效", null);
        }
        JsonNode graphNode = draftResponse.get("graph");
        if (graphNode == null || !graphNode.isObject()) {
            return ApplyOutcome.noChange("工作流草稿缺少 graph 字段", null);
        }

        ObjectNode updatedGraph = (ObjectNode) graphNode.deepCopy();
        JsonNode nodesNode = updatedGraph.get("nodes");
        if (nodesNode == null || !nodesNode.isArray()) {
            return ApplyOutcome.noChange("工作流 graph 中缺少 nodes 数组", null);
        }

        List<ModelSlotChange> changes = new ArrayList<ModelSlotChange>();
        ArrayNode nodes = (ArrayNode) nodesNode;
        for (JsonNode node : nodes) {
            if (node == null || !node.isObject()) {
                continue;
            }
            String nodeId = textOf(node, "id");
            JsonNode dataNode = node.get("data");
            if (dataNode == null || !dataNode.isObject()) {
                continue;
            }
            ObjectNode data = (ObjectNode) dataNode;
            String nodeType = textOf(data, "type");
            String title = textOf(data, "title");
            String displayLabel = isBlank(title) ? nodeType : title + " (" + nodeType + ")";
            String slotKey = (isBlank(nodeId) ? displayLabel : nodeId) + "::" + nodeType;

            if ("llm".equals(nodeType) || "parameter-extractor".equals(nodeType) || "question-classifier".equals(nodeType)) {
                JsonNode modelNode = data.get("model");
                if (modelNode != null && modelNode.isObject()) {
                    ModelSlotChange change = mutateStandardModelObject(
                            appId, slotKey, displayLabel, nodeId, nodeType, (ObjectNode) modelNode, options
                    );
                    if (change != null) {
                        changes.add(change);
                        if ("llm".equals(nodeType)) {
                            syncLlmPromptTemplateForMode(data, change.getBeforeMode(), change.getAfterMode());
                        }
                    }
                }
            } else if ("agent".equals(nodeType)) {
                JsonNode modelValue = data.path("agent_parameters").path("model").path("value");
                if (modelValue != null && modelValue.isObject()) {
                    ModelSlotChange change = mutateAgentModelObject(
                            appId, slotKey, displayLabel, nodeId, nodeType, (ObjectNode) modelValue, options
                    );
                    if (change != null) {
                        changes.add(change);
                    }
                }
            } else if (nodeType.startsWith("knowledge-retrieval")) {
                String retrievalType = nodeType + ".single_retrieval";
                String retrievalSlotKey = (isBlank(nodeId) ? displayLabel + " [单路召回]" : nodeId) + "::" + retrievalType;
                JsonNode singleModel = data.path("single_retrieval_config").path("model");
                if (singleModel != null && singleModel.isObject()) {
                    ModelSlotChange change = mutateStandardModelObject(
                            appId, retrievalSlotKey, displayLabel + " [单路召回]", nodeId, retrievalType,
                            (ObjectNode) singleModel, options
                    );
                    if (change != null) {
                        changes.add(change);
                    }
                }
            }
        }

        if (changes.isEmpty()) {
            return ApplyOutcome.noChange("当前工作流草稿中未命中可替换节点", null);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("graph", updatedGraph);
        if (draftResponse.has("features") && !draftResponse.get("features").isNull()) {
            payload.set("features", draftResponse.get("features").deepCopy());
        } else {
            payload.set("features", objectMapper.createObjectNode());
        }
        if (draftResponse.has("environment_variables") && !draftResponse.get("environment_variables").isNull()) {
            payload.set("environment_variables", draftResponse.get("environment_variables").deepCopy());
        } else {
            payload.set("environment_variables", objectMapper.createArrayNode());
        }
        if (draftResponse.has("conversation_variables") && !draftResponse.get("conversation_variables").isNull()) {
            payload.set("conversation_variables", draftResponse.get("conversation_variables").deepCopy());
        } else {
            payload.set("conversation_variables", objectMapper.createArrayNode());
        }
        if (draftResponse.has("hash") && !draftResponse.get("hash").isNull()) {
            payload.set("hash", draftResponse.get("hash").deepCopy());
        }
        return ApplyOutcome.changed(changes, payload);
    }

    /** Resolves target model & param strategy for a slot, checking node override -> force-all -> mapping rules -> primary rule. */
    public ResolvedTarget resolveTargetForSlot(String appId, String slotKey,
                                               String currentProvider, String currentModelName,
                                               ReplacementOptions options) {
        if (options == null || isBlank(currentProvider) || isBlank(currentModelName)) {
            return null;
        }
        NodeTargetOverride override = options.getNodeOverride(appId, slotKey);
        if (override != null) {
            if (!override.isEnabled()) {
                return null;
            }
            DifyModelOption target = override.getTargetModel();
            if (target == null || isBlank(target.getProvider()) || isBlank(target.getModelName())) {
                return null;
            }
            if (target.isSameModel(currentProvider, currentModelName)) {
                return null;
            }
            ParamStrategy strategy = override.getParamStrategy() != null
                    ? override.getParamStrategy()
                    : options.getParamStrategy();
            ThinkingConfig thinking = override.getThinkingConfig() != null
                    ? override.getThinkingConfig()
                    : options.getThinkingConfig();
            return new ResolvedTarget(target, strategy, thinking);
        }

        if (options.isReplaceAllNodes()) {
            DifyModelOption target = options.getTargetModel();
            if (target == null || isBlank(target.getProvider()) || isBlank(target.getModelName())) {
                return null;
            }
            if (target.isSameModel(currentProvider, currentModelName)) {
                return null;
            }
            return new ResolvedTarget(target, options.getParamStrategy(), options.getThinkingConfig());
        }

        List<ModelMappingRule> rules = options.getMappingRules();
        if (rules != null && !rules.isEmpty()) {
            for (ModelMappingRule rule : rules) {
                if (rule.matches(currentProvider, currentModelName)) {
                    DifyModelOption target = rule.getTargetModel();
                    if (target == null || isBlank(target.getProvider()) || isBlank(target.getModelName())) {
                        return null;
                    }
                    if (target.isSameModel(currentProvider, currentModelName)) {
                        return null;
                    }
                    return new ResolvedTarget(target, options.getParamStrategy(), options.getThinkingConfig());
                }
            }
            return null;
        }

        DifyModelOption target = options.getTargetModel();
        if (target == null || isBlank(target.getProvider()) || isBlank(target.getModelName())) {
            return null;
        }
        if (target.isSameModel(currentProvider, currentModelName)) {
            return null;
        }
        if (currentProvider.equals(options.getSourceProvider())
                && currentModelName.equals(options.getSourceModelName())) {
            return new ResolvedTarget(target, options.getParamStrategy(), options.getThinkingConfig());
        }
        return null;
    }

    private ModelSlotChange mutateStandardModelObject(String appId, String slotKey, String slotLabel,
                                                      String nodeId, String nodeType,
                                                      ObjectNode modelObj, ReplacementOptions options) {
        String beforeProvider = textOf(modelObj, "provider");
        String beforeModel = textOf(modelObj, "name");
        String beforeMode = textOf(modelObj, "mode");
        ResolvedTarget resolved = resolveTargetForSlot(appId, slotKey, beforeProvider, beforeModel, options);
        if (resolved == null) {
            return null;
        }

        DifyModelOption target = resolved.targetModel;
        String afterMode = resolveTargetMode(target.getModelMode(), beforeMode);
        JsonNode oldParamsNode = modelObj.get("completion_params");
        String beforeParams = oldParamsNode == null ? "{}" : oldParamsNode.toString();
        ObjectNode newParamsNode = transformCompletionParams(oldParamsNode, resolved.paramStrategy, resolved.thinkingConfig);

        modelObj.put("provider", target.getProvider());
        modelObj.put("name", target.getModelName());
        if (!isBlank(afterMode)) {
            modelObj.put("mode", afterMode);
        }
        if (modelObj.has("aip_source_app_id")) {
            modelObj.putNull("aip_source_app_id");
        }
        modelObj.set("completion_params", newParamsNode);

        return new ModelSlotChange(
                slotLabel, nodeType, nodeId,
                beforeProvider, beforeModel, beforeMode,
                target.getProvider(), target.getModelName(), afterMode,
                beforeParams, newParamsNode.toString()
        );
    }

    private ModelSlotChange mutateAgentModelObject(String appId, String slotKey, String slotLabel,
                                                   String nodeId, String nodeType,
                                                   ObjectNode modelObj, ReplacementOptions options) {
        String beforeProvider = textOf(modelObj, "provider");
        String beforeModel = textOf(modelObj, "model");
        String beforeMode = textOf(modelObj, "mode");
        ResolvedTarget resolved = resolveTargetForSlot(appId, slotKey, beforeProvider, beforeModel, options);
        if (resolved == null) {
            return null;
        }

        DifyModelOption target = resolved.targetModel;
        String afterMode = resolveTargetMode(target.getModelMode(), beforeMode);
        JsonNode oldParamsNode = modelObj.get("completion_params");
        String beforeParams = oldParamsNode == null ? "{}" : oldParamsNode.toString();
        ObjectNode newParamsNode = transformCompletionParams(oldParamsNode, resolved.paramStrategy, resolved.thinkingConfig);

        modelObj.put("provider", target.getProvider());
        modelObj.put("model", target.getModelName());
        if (!isBlank(afterMode)) {
            modelObj.put("mode", afterMode);
        }
        modelObj.set("completion_params", newParamsNode);

        return new ModelSlotChange(
                slotLabel, nodeType, nodeId,
                beforeProvider, beforeModel, beforeMode,
                target.getProvider(), target.getModelName(), afterMode,
                beforeParams, newParamsNode.toString()
        );
    }

    /** Keeps Dify LLM node prompt_template structure compatible when switching between chat (array) and completion (object). */
    private void syncLlmPromptTemplateForMode(ObjectNode llmData, String beforeMode, String afterMode) {
        if (isBlank(afterMode) || afterMode.equalsIgnoreCase(beforeMode)) {
            return;
        }
        JsonNode currentTemplate = llmData.get("prompt_template");
        if ("chat".equalsIgnoreCase(afterMode)) {
            if (currentTemplate != null && currentTemplate.isArray()) {
                return;
            }
            ArrayNode arr = objectMapper.createArrayNode();
            ObjectNode item = objectMapper.createObjectNode();
            item.put("id", UUID.randomUUID().toString());
            item.put("role", "system");
            if (currentTemplate != null && currentTemplate.isObject()) {
                String editionType = textOf(currentTemplate, "edition_type");
                String text = textOf(currentTemplate, "text");
                item.put("text", text);
                if (!isBlank(editionType)) {
                    item.put("edition_type", editionType);
                } else {
                    item.put("edition_type", "basic");
                }
                if ("jinja2".equalsIgnoreCase(editionType) && currentTemplate.has("jinja2_text")) {
                    item.put("jinja2_text", textOf(currentTemplate, "jinja2_text"));
                }
            } else {
                item.put("text", "");
                item.put("edition_type", "basic");
            }
            arr.add(item);
            llmData.set("prompt_template", arr);
        } else if ("completion".equalsIgnoreCase(afterMode)) {
            if (currentTemplate != null && currentTemplate.isObject()) {
                return;
            }
            ObjectNode obj = objectMapper.createObjectNode();
            StringBuilder combined = new StringBuilder();
            String editionType = "basic";
            String jinja2Text = null;
            if (currentTemplate != null && currentTemplate.isArray()) {
                for (JsonNode item : currentTemplate) {
                    if (item == null || !item.isObject()) {
                        continue;
                    }
                    String itemEdition = textOf(item, "edition_type");
                    if ("jinja2".equalsIgnoreCase(itemEdition) && jinja2Text == null) {
                        editionType = "jinja2";
                        jinja2Text = textOf(item, "jinja2_text");
                    }
                    String text = textOf(item, "text");
                    if (!text.isEmpty()) {
                        if (combined.length() > 0) {
                            combined.append("\n\n");
                        }
                        combined.append(text);
                    }
                }
            }
            obj.put("text", combined.toString());
            obj.put("edition_type", editionType);
            if (jinja2Text != null) {
                obj.put("jinja2_text", jinja2Text);
            }
            llmData.set("prompt_template", obj);
        }
    }

    private ObjectNode transformCompletionParams(JsonNode oldParams, ParamStrategy strategy, ThinkingConfig thinking) {
        ObjectNode result = objectMapper.createObjectNode();
        if (strategy != ParamStrategy.CLEAR_ALL && oldParams != null && oldParams.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = oldParams.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (strategy == ParamStrategy.KEEP_ALL || COMMON_COMPLETION_PARAMS.contains(entry.getKey())) {
                    result.set(entry.getKey(), entry.getValue().deepCopy());
                }
            }
        }
        applyThinkingConfig(result, thinking);
        return result;
    }

    private void applyThinkingConfig(ObjectNode params, ThinkingConfig thinking) {
        if (params == null || thinking == null || thinking.getMode() == ThinkingMode.FOLLOW_STRATEGY) {
            return;
        }
        switch (thinking.getMode()) {
            case DISABLE:
                params.put("enable_thinking", false);
                params.remove("reasoning_effort");
                break;
            case ENABLE_DEFAULT:
                params.put("enable_thinking", true);
                params.remove("reasoning_effort");
                break;
            case ENABLE_WITH_EFFORT:
                params.put("enable_thinking", true);
                String effort = isBlank(thinking.getReasoningEffort()) ? "medium" : thinking.getReasoningEffort();
                params.put("reasoning_effort", effort);
                break;
            case OMIT:
                params.remove("enable_thinking");
                params.remove("reasoning_effort");
                break;
            default:
                break;
        }
    }

    private String summarizeParamsAfterStrategy(String beforeParamsJson, ParamStrategy strategy, ThinkingConfig thinking) {
        try {
            JsonNode node = isBlank(beforeParamsJson) ? objectMapper.createObjectNode() : objectMapper.readTree(beforeParamsJson);
            return transformCompletionParams(node, strategy, thinking).toString();
        } catch (IOException e) {
            return transformCompletionParams(objectMapper.createObjectNode(), strategy, thinking).toString();
        }
    }

    private ModelRef readStandardModelNode(String slotLabel, String nodeId, String nodeType, JsonNode modelNode) {
        if (modelNode == null || !modelNode.isObject()) {
            return null;
        }
        String provider = textOf(modelNode, "provider");
        String modelName = textOf(modelNode, "name");
        if (isBlank(provider) && isBlank(modelName)) {
            return null;
        }
        String mode = textOf(modelNode, "mode");
        JsonNode params = modelNode.get("completion_params");
        String paramsText = params != null && params.isObject() ? params.toString() : "{}";
        return new ModelRef(slotLabel, nodeId, nodeType, provider, modelName, mode, paramsText);
    }

    private ModelRef readAgentModelNode(String slotLabel, String nodeId, String nodeType, JsonNode modelNode) {
        if (modelNode == null || !modelNode.isObject()) {
            return null;
        }
        String provider = textOf(modelNode, "provider");
        String modelName = textOf(modelNode, "model");
        if (isBlank(provider) && isBlank(modelName)) {
            return null;
        }
        String mode = textOf(modelNode, "mode");
        JsonNode params = modelNode.get("completion_params");
        String paramsText = params != null && params.isObject() ? params.toString() : "{}";
        return new ModelRef(slotLabel, nodeId, nodeType, provider, modelName, mode, paramsText);
    }

    private String resolveTargetMode(String targetMode, String currentMode) {
        if (!isBlank(targetMode)) {
            return targetMode;
        }
        return isBlank(currentMode) ? "chat" : currentMode;
    }

    private static String textOf(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText("");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class ResolvedTarget {
        public final DifyModelOption targetModel;
        public final ParamStrategy paramStrategy;
        public final ThinkingConfig thinkingConfig;

        public ResolvedTarget(DifyModelOption targetModel, ParamStrategy paramStrategy, ThinkingConfig thinkingConfig) {
            this.targetModel = targetModel;
            this.paramStrategy = paramStrategy == null ? ParamStrategy.KEEP_COMMON : paramStrategy;
            this.thinkingConfig = thinkingConfig == null ? ThinkingConfig.followStrategy() : thinkingConfig;
        }
    }

    public static class ApplyOutcome {
        private final boolean changed;
        private final String message;
        private final List<ModelSlotChange> changes;
        private final JsonNode payload;

        private ApplyOutcome(boolean changed, String message, List<ModelSlotChange> changes, JsonNode payload) {
            this.changed = changed;
            this.message = message;
            this.changes = changes == null ? Collections.<ModelSlotChange>emptyList() : changes;
            this.payload = payload;
        }

        public static ApplyOutcome changed(List<ModelSlotChange> changes, JsonNode payload) {
            return new ApplyOutcome(true, "", changes, payload);
        }

        public static ApplyOutcome noChange(String message, JsonNode payload) {
            return new ApplyOutcome(false, message, Collections.<ModelSlotChange>emptyList(), payload);
        }

        public boolean isChanged() { return changed; }
        public String getMessage() { return message; }
        public List<ModelSlotChange> getChanges() { return changes; }
        public JsonNode getPayload() { return payload; }
    }
}