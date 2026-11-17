package com.company.difytokenquery.db;

import com.company.difytokenquery.dify.ModelReplacementEngine;
import com.company.difytokenquery.model.DifyAppSummary;
import com.company.difytokenquery.model.DifyAppSummary.ModelRef;
import com.company.difytokenquery.model.DifyModelOption;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Queries Dify apps, active model configs, workflow drafts, and API tokens from PostgreSQL. */
public class DifyAppModelRepository {
    private static final String LOAD_APPS_META_SQL =
            "SELECT a.id::text AS app_id, " +
            "       a.name AS app_name, " +
            "       a.mode AS app_mode, " +
            "       a.status AS app_status, " +
            "       a.app_model_config_id::text AS amc_id, " +
            "       a.workflow_id::text AS published_wf_id, " +
            "       amc.model AS amc_model_json, " +
            "       tok.token_list " +
            "FROM apps a " +
            "LEFT JOIN app_model_configs amc ON amc.id = a.app_model_config_id " +
            "LEFT JOIN (SELECT app_id, string_agg(token, ',') AS token_list FROM api_tokens GROUP BY app_id) tok ON tok.app_id = a.id " +
            "ORDER BY a.updated_at DESC NULLS LAST, a.name ASC";

    private static final String LOAD_COMPACT_WORKFLOW_MODELS_SQL =
            "WITH selected_wf AS ( " +
            "    SELECT DISTINCT ON (w.app_id) w.app_id, (w.version = 'draft') AS is_draft, w.graph " +
            "    FROM workflows w " +
            "    WHERE w.version = 'draft' " +
            "       OR w.id IN (SELECT workflow_id FROM apps WHERE workflow_id IS NOT NULL) " +
            "    ORDER BY w.app_id, (w.version = 'draft') DESC, w.updated_at DESC NULLS LAST, w.created_at DESC " +
            ") " +
            "SELECT sw.app_id::text AS app_id, " +
            "       sw.is_draft, " +
            "       (SELECT jsonb_agg(jsonb_build_object( " +
            "           'id', n->>'id', " +
            "           'type', n->'data'->>'type', " +
            "           'title', n->'data'->>'title', " +
            "           'model', CASE " +
            "               WHEN (n->'data'->>'type') IN ('llm', 'parameter-extractor', 'question-classifier') THEN n->'data'->'model' " +
            "               WHEN (n->'data'->>'type') = 'agent' THEN n->'data'->'agent_parameters'->'model'->'value' " +
            "               WHEN (n->'data'->>'type') LIKE 'knowledge-retrieval%' THEN n->'data'->'single_retrieval_config'->'model' " +
            "               ELSE NULL END " +
            "       )) " +
            "       FROM jsonb_array_elements( " +
            "           CASE WHEN sw.graph IS NOT NULL AND sw.graph <> '' AND jsonb_typeof((sw.graph::jsonb)->'nodes') = 'array' " +
            "                THEN (sw.graph::jsonb)->'nodes' ELSE '[]'::jsonb END " +
            "       ) n " +
            "       WHERE (n->'data'->>'type') IN ('llm', 'parameter-extractor', 'question-classifier', 'agent') " +
            "          OR ((n->'data'->>'type') LIKE 'knowledge-retrieval%' AND jsonb_exists(n->'data'->'single_retrieval_config', 'model')) " +
            "       )::text AS compact_models " +
            "FROM selected_wf sw";

    private final ModelReplacementEngine engine = new ModelReplacementEngine();

    public DifyAppModelRepository() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 PostgreSQL JDBC 驱动", e);
        }
    }

    public List<DifyAppSummary> loadAllApps(DatasourceConfig datasource) throws SQLException {
        if (datasource == null || !datasource.isEnabled()) {
            throw new SQLException("未选择可用的 Dify 数据源");
        }
        datasource.normalize();
        List<DifyAppSummary> result = new ArrayList<DifyAppSummary>();
        try (Connection conn = DriverManager.getConnection(
                datasource.getEffectiveJdbcUrl(), datasource.getUsername(), datasource.getPassword())) {
            Map<String, WorkflowModelEntry> wfMap = loadWorkflowModels(conn);
            try (PreparedStatement ps = conn.prepareStatement(LOAD_APPS_META_SQL);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String appId = rs.getString("app_id");
                    String appName = rs.getString("app_name");
                    String mode = rs.getString("app_mode");
                    String status = rs.getString("app_status");
                    String amcId = rs.getString("amc_id");
                    String publishedWfId = rs.getString("published_wf_id");
                    String amcModelJson = rs.getString("amc_model_json");
                    String tokenListStr = rs.getString("token_list");

                    List<String> tokens = parseTokens(tokenListStr);
                    boolean isWf = "workflow".equalsIgnoreCase(mode) || "advanced-chat".equalsIgnoreCase(mode);
                    WorkflowModelEntry wfEntry = wfMap.get(appId);
                    List<ModelRef> refs;
                    if (isWf) {
                        refs = wfEntry == null
                                ? java.util.Collections.<ModelRef>emptyList()
                                : engine.extractCompactWorkflowRefs(wfEntry.compactJson);
                    } else {
                        refs = engine.extractAppModelConfigRefs(amcModelJson);
                    }
                    String appUrl = DatasourceConfig.buildDifyAppUrl(datasource.getDifyFrontendUrl(), appId, mode);
                    result.add(new DifyAppSummary(
                            appId, appName, mode, status, amcId, publishedWfId,
                            wfEntry != null && wfEntry.hasDraft,
                            appUrl, tokens, refs
                    ));
                }
            }
        }
        return result;
    }

    private Map<String, WorkflowModelEntry> loadWorkflowModels(Connection conn) throws SQLException {
        Map<String, WorkflowModelEntry> map = new HashMap<String, WorkflowModelEntry>();
        try (PreparedStatement ps = conn.prepareStatement(LOAD_COMPACT_WORKFLOW_MODELS_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String appId = rs.getString("app_id");
                boolean isDraft = rs.getBoolean("is_draft");
                String compact = rs.getString("compact_models");
                map.put(appId, new WorkflowModelEntry(isDraft, compact));
            }
        }
        return map;
    }

    public List<DifyModelOption> collectUsedModels(List<DifyAppSummary> apps) {
        Map<String, DifyModelOption> unique = new LinkedHashMap<String, DifyModelOption>();
        if (apps != null) {
            for (DifyAppSummary app : apps) {
                for (ModelRef ref : app.getModelRefs()) {
                    if (ref.getProvider().isEmpty() && ref.getModelName().isEmpty()) {
                        continue;
                    }
                    String key = ref.getProvider() + "::" + ref.getModelName();
                    if (!unique.containsKey(key)) {
                        unique.put(key, DifyModelOption.simple(ref.getProvider(), ref.getModelName(), ref.getModelMode()));
                    }
                }
            }
        }
        return new ArrayList<DifyModelOption>(unique.values());
    }

    private List<String> parseTokens(String tokenListStr) {
        List<String> tokens = new ArrayList<String>();
        if (tokenListStr == null || tokenListStr.trim().isEmpty()) {
            return tokens;
        }
        for (String part : tokenListStr.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private static class WorkflowModelEntry {
        final boolean hasDraft;
        final String compactJson;

        WorkflowModelEntry(boolean hasDraft, String compactJson) {
            this.hasDraft = hasDraft;
            this.compactJson = compactJson;
        }
    }
}