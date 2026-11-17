package com.company.difytokenquery.dify;

import com.company.difytokenquery.dify.ModelReplacementEngine.ApplyOutcome;
import com.company.difytokenquery.model.DifyAppSummary;
import com.company.difytokenquery.model.DifyAppSummary.ModelRef;
import com.company.difytokenquery.model.DifyModelOption;
import com.company.difytokenquery.model.ReplacementOptions;
import com.company.difytokenquery.model.ReplacementOptions.ModelMappingRule;
import com.company.difytokenquery.model.ReplacementOptions.NodeTargetOverride;
import com.company.difytokenquery.model.ReplacementOptions.ParamStrategy;
import com.company.difytokenquery.model.ReplacementOptions.ThinkingConfig;
import com.company.difytokenquery.model.ReplacementOptions.ThinkingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.company.difytokenquery.model.ReplacementPlanItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ModelReplacementEngineTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ModelReplacementEngine engine = new ModelReplacementEngine();

    @Test
    public void extractWorkflowGraphRefsShouldCoverAllLlmSlotsAndSkipReranking() {
        String graph = "{"
                + "\"nodes\":["
                + "  {\"id\":\"n1\",\"data\":{\"type\":\"llm\",\"title\":\"主LLM\",\"model\":{\"provider\":\"p1\",\"name\":\"m1\",\"mode\":\"chat\",\"completion_params\":{\"temperature\":0.3,\"enable_thinking\":false}}}},"
                + "  {\"id\":\"n2\",\"data\":{\"type\":\"parameter-extractor\",\"title\":\"提取\",\"model\":{\"provider\":\"p1\",\"name\":\"m1\",\"mode\":\"chat\",\"completion_params\":{}}}},"
                + "  {\"id\":\"n3\",\"data\":{\"type\":\"question-classifier\",\"title\":\"分类\",\"model\":{\"provider\":\"p2\",\"name\":\"m2\",\"mode\":\"chat\",\"completion_params\":{}}}},"
                + "  {\"id\":\"n4\",\"data\":{\"type\":\"agent\",\"title\":\"Agent\",\"agent_parameters\":{\"model\":{\"type\":\"constant\",\"value\":{\"provider\":\"p1\",\"model\":\"m1\",\"mode\":\"chat\",\"completion_params\":{\"top_p\":0.9}}}}}},"
                + "  {\"id\":\"n5\",\"data\":{\"type\":\"knowledge-retrieval\",\"title\":\"检索\",\"single_retrieval_config\":{\"model\":{\"provider\":\"p1\",\"name\":\"m1\",\"mode\":\"chat\",\"completion_params\":{}}},\"multiple_retrieval_config\":{\"reranking_model\":{\"provider\":\"rerank_p\",\"model\":\"bge-reranker\"}}}}"
                + "]}";

        List<ModelRef> refs = engine.extractWorkflowGraphRefs(graph);
        assertEquals(5, refs.size());
        assertEquals("llm", refs.get(0).getNodeType());
        assertEquals("parameter-extractor", refs.get(1).getNodeType());
        assertEquals("question-classifier", refs.get(2).getNodeType());
        assertEquals("agent", refs.get(3).getNodeType());
        assertEquals("m1", refs.get(3).getModelName());
        assertEquals("knowledge-retrieval.single_retrieval", refs.get(4).getNodeType());
    }

    @Test
    public void extractCompactWorkflowRefsShouldParseCompactPostgresSummary() {
        String compact = "["
                + "{\"id\":\"n1\",\"type\":\"llm\",\"title\":\"主节点\",\"model\":{\"provider\":\"p1\",\"name\":\"m1\",\"mode\":\"chat\",\"completion_params\":{}}},"
                + "{\"id\":\"n2\",\"type\":\"agent\",\"title\":\"Agent\",\"model\":{\"provider\":\"p2\",\"model\":\"m2\",\"mode\":\"chat\",\"completion_params\":{}}},"
                + "{\"id\":\"n3\",\"type\":\"knowledge-retrieval\",\"title\":\"知识\",\"model\":{\"provider\":\"p3\",\"name\":\"m3\",\"mode\":\"chat\",\"completion_params\":{}}}"
                + "]";
        List<ModelRef> refs = engine.extractCompactWorkflowRefs(compact);
        assertEquals(3, refs.size());
        assertEquals("m1", refs.get(0).getModelName());
        assertEquals("m2", refs.get(1).getModelName());
        assertEquals("knowledge-retrieval.single_retrieval", refs.get(2).getNodeType());
    }

    @Test
    public void applyToAppDetailShouldKeepCommonParamsAndUpdateModel() throws Exception {
        String detailJson = "{"
                + "\"id\":\"app-1\","
                + "\"model_config\":{"
                + "  \"id\":\"amc-1\","
                + "  \"created_at\":123456,"
                + "  \"pre_prompt\":\"hello\","
                + "  \"prompt_type\":\"simple\","
                + "  \"model\":{\"provider\":\"old-p\",\"name\":\"old-m\",\"mode\":\"chat\",\"completion_params\":{\"temperature\":0.2,\"stop\":[\"END\"],\"enable_thinking\":false}}"
                + "}"
                + "}";
        JsonNode detail = mapper.readTree(detailJson);
        DifyModelOption target = DifyModelOption.simple("new-p", "new-m", "completion");
        ReplacementOptions options = new ReplacementOptions("old-p", "old-m", false, target, ParamStrategy.KEEP_COMMON, false);

        ApplyOutcome outcome = engine.applyToAppDetail(detail, options);
        assertTrue(outcome.isChanged());
        assertEquals(1, outcome.getChanges().size());
        JsonNode payload = outcome.getPayload();
        assertFalse(payload.has("created_at"));
        assertFalse(payload.has("id"));
        assertEquals("hello", payload.get("pre_prompt").asText());
        JsonNode newModel = payload.get("model");
        assertEquals("new-p", newModel.get("provider").asText());
        assertEquals("new-m", newModel.get("name").asText());
        assertEquals("completion", newModel.get("mode").asText());
        assertEquals(0.2, newModel.get("completion_params").get("temperature").asDouble(), 0.0001);
        assertTrue(newModel.get("completion_params").has("stop"));
        assertFalse(newModel.get("completion_params").has("enable_thinking"));
    }

    @Test
    public void applyToWorkflowDraftShouldRespectMatchedVsForceAllAndPreserveHash() throws Exception {
        String draftJson = "{"
                + "\"hash\":\"abc-hash-1\","
                + "\"features\":{\"opening_statement\":\"hi\"},"
                + "\"environment_variables\":[{\"id\":\"e1\"}],"
                + "\"conversation_variables\":[],"
                + "\"graph\":{"
                + "  \"nodes\":["
                + "    {\"id\":\"n1\",\"data\":{\"type\":\"llm\",\"title\":\"A\",\"model\":{\"provider\":\"p1\",\"name\":\"m1\",\"mode\":\"chat\",\"completion_params\":{\"temperature\":0.5,\"custom_flag\":true}}}},"
                + "    {\"id\":\"n2\",\"data\":{\"type\":\"llm\",\"title\":\"B\",\"model\":{\"provider\":\"p2\",\"name\":\"m2\",\"mode\":\"chat\",\"completion_params\":{\"temperature\":0.7}}}},"
                + "    {\"id\":\"n3\",\"data\":{\"type\":\"agent\",\"title\":\"C\",\"agent_parameters\":{\"model\":{\"type\":\"constant\",\"value\":{\"provider\":\"p1\",\"model\":\"m1\",\"mode\":\"chat\",\"completion_params\":{\"max_tokens\":512}}}}}},"
                + "    {\"id\":\"n4\",\"data\":{\"type\":\"knowledge-retrieval\",\"title\":\"D\",\"multiple_retrieval_config\":{\"reranking_model\":{\"provider\":\"p1\",\"model\":\"m1\"}}}}"
                + "  ],"
                + "  \"edges\":[{\"id\":\"e1\"}]"
                + "}"
                + "}";
        JsonNode draft = mapper.readTree(draftJson);
        DifyModelOption target = DifyModelOption.simple("new-p", "new-m", "chat");

        // 1. Match only p1/m1, CLEAR_ALL params
        ReplacementOptions matchOpts = new ReplacementOptions("p1", "m1", false, target, ParamStrategy.CLEAR_ALL, false);
        ApplyOutcome matchedOutcome = engine.applyToWorkflowDraft(draft, matchOpts);
        assertTrue(matchedOutcome.isChanged());
        assertEquals(2, matchedOutcome.getChanges().size());
        JsonNode payload = matchedOutcome.getPayload();
        assertEquals("abc-hash-1", payload.get("hash").asText());
        JsonNode nodes = payload.get("graph").get("nodes");
        assertEquals("new-p", nodes.get(0).get("data").get("model").get("provider").asText());
        assertEquals(0, nodes.get(0).get("data").get("model").get("completion_params").size());
        assertEquals("p2", nodes.get(1).get("data").get("model").get("provider").asText());
        assertEquals("new-m", nodes.get(2).get("data").get("agent_parameters").get("model").get("value").get("model").asText());
        // Reranking model must remain untouched
        assertEquals("p1", nodes.get(3).get("data").get("multiple_retrieval_config").get("reranking_model").get("provider").asText());

        // 2. Force replace all LLM nodes, KEEP_ALL params
        ReplacementOptions forceOpts = new ReplacementOptions("", "", true, target, ParamStrategy.KEEP_ALL, false);
        ApplyOutcome forceOutcome = engine.applyToWorkflowDraft(draft, forceOpts);
        assertTrue(forceOutcome.isChanged());
        assertEquals(3, forceOutcome.getChanges().size());
        JsonNode forceNodes = forceOutcome.getPayload().get("graph").get("nodes");
        assertTrue(forceNodes.get(0).get("data").get("model").get("completion_params").get("custom_flag").asBoolean());
        assertEquals("new-p", forceNodes.get(1).get("data").get("model").get("provider").asText());
    }

    @Test
    public void buildPlanShouldSkipAppsWithoutMatchingSlots() {
        ModelRef ref = new ModelRef("主模型", "", "app_model_config", "p2", "m2", "chat", "{}");
        DifyAppSummary app = new DifyAppSummary("id1", "App1", "chat", "normal", "amc1", "", false, "", Collections.<String>emptyList(), Collections.singletonList(ref));
        DifyModelOption target = DifyModelOption.simple("new-p", "new-m", "chat");
        ReplacementOptions opts = new ReplacementOptions("p1", "m1", false, target, ParamStrategy.KEEP_COMMON, false);

        List<ReplacementPlanItem> plan = engine.buildPlan(Collections.singletonList(app), opts);
        assertEquals(1, plan.size());
        assertFalse(plan.get(0).isExecutable());
        assertNotNull(plan.get(0).getSkippedReason());
    }

    @Test
    public void multiRulesAndNodeOverridesShouldSupportDifferentTargetsPerNodeAndSyncPromptTemplate() throws Exception {
        String draftJson = "{"
                + "\"hash\":\"h1\","
                + "\"features\":{},"
                + "\"environment_variables\":[],"
                + "\"conversation_variables\":[],"
                + "\"graph\":{"
                + "  \"nodes\":["
                + "    {\"id\":\"n1\",\"data\":{\"type\":\"llm\",\"title\":\"LLM\",\"model\":{\"provider\":\"ollama\",\"name\":\"deepseek-r1:32b\",\"mode\":\"completion\",\"completion_params\":{\"temperature\":0.7},\"aip_source_app_id\":\"x\"},\"prompt_template\":{\"text\":\"Human: {{#sys.query#}}\",\"edition_type\":\"basic\"}}},"
                + "    {\"id\":\"n2\",\"data\":{\"type\":\"llm\",\"title\":\"LLM 2\",\"model\":{\"provider\":\"ollama\",\"name\":\"qwq:32b\",\"mode\":\"chat\",\"completion_params\":{\"temperature\":0.7}}}}"
                + "  ],"
                + "  \"edges\":[]"
                + "}"
                + "}";
        JsonNode draft = mapper.readTree(draftJson);
        DifyModelOption targetA = DifyModelOption.simple("pA", "mA", "chat");
        DifyModelOption targetB = DifyModelOption.simple("pB", "mB", "completion");

        ReplacementOptions multiRuleOpts = new ReplacementOptions(
                "", "", false, null, ParamStrategy.KEEP_COMMON, false,
                Arrays.asList(
                        new ModelMappingRule("ollama", "deepseek-r1:32b", targetA),
                        new ModelMappingRule("ollama", "qwq:32b", targetB)
                ),
                Collections.<String, Map<String, NodeTargetOverride>>emptyMap()
        );
        ApplyOutcome out1 = engine.applyToWorkflowDraft("app-1", draft, multiRuleOpts);
        assertTrue(out1.isChanged());
        assertEquals(2, out1.getChanges().size());
        JsonNode n1Data = out1.getPayload().get("graph").get("nodes").get(0).get("data");
        assertEquals("mA", n1Data.get("model").get("name").asText());
        assertEquals("chat", n1Data.get("model").get("mode").asText());
        assertTrue(n1Data.get("model").get("aip_source_app_id").isNull());
        assertTrue(n1Data.get("prompt_template").isArray());
        assertEquals("Human: {{#sys.query#}}", n1Data.get("prompt_template").get(0).get("text").asText());

        Map<String, NodeTargetOverride> appOverrides = new HashMap<String, NodeTargetOverride>();
        appOverrides.put("n1::llm", new NodeTargetOverride(false, targetA, null));
        appOverrides.put("n2::llm", new NodeTargetOverride(true, targetA, ParamStrategy.CLEAR_ALL));
        Map<String, Map<String, NodeTargetOverride>> allOverrides = new HashMap<String, Map<String, NodeTargetOverride>>();
        allOverrides.put("app-1", appOverrides);

        ReplacementOptions overrideOpts = new ReplacementOptions(
                "", "", false, null, ParamStrategy.KEEP_COMMON, false,
                Collections.<ModelMappingRule>emptyList(), allOverrides
        );
        ApplyOutcome out2 = engine.applyToWorkflowDraft("app-1", draft, overrideOpts);
        assertTrue(out2.isChanged());
        assertEquals(1, out2.getChanges().size());
        assertEquals("LLM 2 (llm)", out2.getChanges().get(0).getSlotLabel());
        JsonNode n2Model = out2.getPayload().get("graph").get("nodes").get(1).get("data").get("model");
        assertEquals("mA", n2Model.get("name").asText());
        assertEquals(0, n2Model.get("completion_params").size());
    }

    @Test
    public void thinkingConfigShouldControlEnableThinkingAndReasoningEffortPerGlobalOrNode() throws Exception {
        String draftJson = "{"
                + "\"hash\":\"h2\","
                + "\"features\":{},"
                + "\"environment_variables\":[],"
                + "\"conversation_variables\":[],"
                + "\"graph\":{"
                + "  \"nodes\":["
                + "    {\"id\":\"n1\",\"data\":{\"type\":\"llm\",\"title\":\"深度思考节点\",\"model\":{\"provider\":\"p1\",\"name\":\"m1\",\"mode\":\"chat\",\"completion_params\":{\"temperature\":0.6,\"enable_thinking\":false}}}},"
                + "    {\"id\":\"n2\",\"data\":{\"type\":\"llm\",\"title\":\"快速输出节点\",\"model\":{\"provider\":\"p1\",\"name\":\"m1\",\"mode\":\"chat\",\"completion_params\":{\"temperature\":0.6,\"enable_thinking\":true,\"reasoning_effort\":\"high\"}}}}"
                + "  ],"
                + "  \"edges\":[]"
                + "}"
                + "}";
        JsonNode draft = mapper.readTree(draftJson);
        DifyModelOption target = DifyModelOption.simple("p2", "m2", "chat");

        // Global ENABLE_WITH_EFFORT ("high") + per-node override on n2 to DISABLE
        Map<String, NodeTargetOverride> appOverrides = new HashMap<String, NodeTargetOverride>();
        appOverrides.put("n2::llm", new NodeTargetOverride(true, target, ParamStrategy.KEEP_COMMON,
                new ThinkingConfig(ThinkingMode.DISABLE, "")));
        Map<String, Map<String, NodeTargetOverride>> allOverrides = new HashMap<String, Map<String, NodeTargetOverride>>();
        allOverrides.put("app-t", appOverrides);

        ReplacementOptions opts = new ReplacementOptions(
                "p1", "m1", false, target, ParamStrategy.KEEP_COMMON,
                new ThinkingConfig(ThinkingMode.ENABLE_WITH_EFFORT, "high"),
                false, Collections.<ModelMappingRule>emptyList(), allOverrides
        );

        ApplyOutcome out = engine.applyToWorkflowDraft("app-t", draft, opts);
        assertTrue(out.isChanged());
        assertEquals(2, out.getChanges().size());

        JsonNode n1Params = out.getPayload().get("graph").get("nodes").get(0).get("data").get("model").get("completion_params");
        assertTrue(n1Params.get("enable_thinking").asBoolean());
        assertEquals("high", n1Params.get("reasoning_effort").asText());
        assertEquals(0.6, n1Params.get("temperature").asDouble(), 0.0001);

        JsonNode n2Params = out.getPayload().get("graph").get("nodes").get(1).get("data").get("model").get("completion_params");
        assertFalse(n2Params.get("enable_thinking").asBoolean());
        assertFalse(n2Params.has("reasoning_effort"));

        // Global OMIT should strip both enable_thinking and reasoning_effort
        ReplacementOptions omitOpts = new ReplacementOptions(
                "p1", "m1", false, target, ParamStrategy.KEEP_ALL,
                new ThinkingConfig(ThinkingMode.OMIT, ""),
                false, Collections.<ModelMappingRule>emptyList(), Collections.<String, Map<String, NodeTargetOverride>>emptyMap()
        );
        ApplyOutcome omitOut = engine.applyToWorkflowDraft("app-t", draft, omitOpts);
        JsonNode n2OmitParams = omitOut.getPayload().get("graph").get("nodes").get(1).get("data").get("model").get("completion_params");
        assertFalse(n2OmitParams.has("enable_thinking"));
        assertFalse(n2OmitParams.has("reasoning_effort"));
    }
}