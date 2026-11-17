package com.company.difytokenquery.dify;

import com.company.difytokenquery.dify.DifyConsoleClient.HttpResponse;
import com.company.difytokenquery.dify.DifyConsoleClient.HttpTransport;
import com.company.difytokenquery.dify.DifyConsoleClient.ModelParameterSupport;
import com.company.difytokenquery.model.DifyModelOption;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DifyConsoleClientTest {
    @Test
    public void loginAndListLlmModelsShouldFilterActiveModels() throws Exception {
        final List<String> calls = new ArrayList<String>();
        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpResponse execute(String method, String url, Map<String, String> headers, String body) throws IOException {
                calls.add(method + " " + url);
                if (url.endsWith("/console/api/login")) {
                    return new HttpResponse(200, "{\"result\":\"success\",\"data\":{\"access_token\":\"tok-1\",\"refresh_token\":\"ref-1\"}}");
                }
                if (url.endsWith("/console/api/workspaces/current/models/model-types/llm")) {
                    assertEquals("Bearer tok-1", headers.get("Authorization"));
                    return new HttpResponse(200, "{\"data\":["
                            + "{\"provider\":\"langgenius/siliconflow/siliconflow\",\"label\":{\"zh_Hans\":\"硅基流动\"},\"models\":["
                            + "  {\"model\":\"Qwen/Qwen2.5-72B\",\"label\":{\"en_US\":\"Qwen2.5-72B\"},\"model_type\":\"llm\",\"model_properties\":{\"mode\":\"chat\"},\"status\":\"active\",\"fetch_from\":\"predefined-model\"},"
                            + "  {\"model\":\"DisabledModel\",\"label\":{\"en_US\":\"Disabled\"},\"model_type\":\"llm\",\"model_properties\":{\"mode\":\"chat\"},\"status\":\"no-configure\"}"
                            + "]}"
                            + "]}");
                }
                if (url.contains("/export?include_secret=false")) {
                    return new HttpResponse(200, "{\"data\":\"app:\\n  name: Demo\\n\"}");
                }
                return new HttpResponse(404, "{}");
            }
        };

        DifyConsoleClient client = new DifyConsoleClient("http://192.2.2.170:8180/", transport);
        client.login("admin@example.com", "secret");
        assertTrue(client.isLoggedIn());
        assertEquals("tok-1", client.getAccessToken());

        List<DifyModelOption> models = client.listLlmModels();
        assertEquals(1, models.size());
        assertEquals("langgenius/siliconflow/siliconflow", models.get(0).getProvider());
        assertEquals("硅基流动", models.get(0).getProviderLabel());
        assertEquals("Qwen/Qwen2.5-72B", models.get(0).getModelName());
        assertEquals("chat", models.get(0).getModelMode());

        String yaml = client.exportAppDsl("app-123", "wf-999");
        assertTrue(yaml.contains("name: Demo"));
        assertTrue(calls.get(2).contains("workflow_id=wf-999"));
    }

    @Test
    public void shouldRefreshTokenOn401AndRetry() throws Exception {
        HttpTransport transport = new HttpTransport() {
            private int exportAttempts = 0;

            @Override
            public HttpResponse execute(String method, String url, Map<String, String> headers, String body) {
                if (url.endsWith("/console/api/refresh-token")) {
                    return new HttpResponse(200, "{\"result\":\"success\",\"data\":{\"access_token\":\"tok-2\",\"refresh_token\":\"ref-2\"}}");
                }
                if (url.contains("/export")) {
                    exportAttempts++;
                    if (exportAttempts == 1) {
                        return new HttpResponse(401, "{\"code\":\"unauthorized\",\"message\":\"expired\"}");
                    }
                    assertEquals("Bearer tok-2", headers.get("Authorization"));
                    return new HttpResponse(200, "{\"data\":\"app:\\n  mode: chat\\n\"}");
                }
                return new HttpResponse(404, "{}");
            }
        };

        DifyConsoleClient client = new DifyConsoleClient("http://192.2.2.170:8180", transport);
        client.setTokens("a@b.com", "tok-1", "ref-1");
        String yaml = client.exportAppDsl("app-1", null);
        assertTrue(yaml.contains("mode: chat"));
        assertEquals("tok-2", client.getAccessToken());
    }

    @Test
    public void getModelParameterSupportShouldParseThinkingRules() throws Exception {
        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpResponse execute(String method, String url, Map<String, String> headers, String body) {
                assertTrue(url.contains("/model-providers/langgenius/openai_api_compatible/openai_api_compatible/models/parameter-rules?model=deepseek-v4-flash"));
                return new HttpResponse(200, "{\"data\":["
                        + "{\"name\":\"temperature\",\"type\":\"float\"},"
                        + "{\"name\":\"enable_thinking\",\"type\":\"boolean\"},"
                        + "{\"name\":\"reasoning_effort\",\"type\":\"string\",\"options\":[\"low\",\"medium\",\"high\"]}"
                        + "]}");
            }
        };
        DifyConsoleClient client = new DifyConsoleClient("http://192.2.2.170:8180", transport);
        client.setTokens("a@b.com", "tok", "ref");
        ModelParameterSupport support = client.getModelParameterSupport("langgenius/openai_api_compatible/openai_api_compatible", "deepseek-v4-flash");
        assertTrue(support.isLoaded());
        assertTrue(support.isSupportsEnableThinking());
        assertTrue(support.isSupportsReasoningEffort());
        assertEquals(3, support.getReasoningEffortOptions().size());
        assertEquals("high", support.getReasoningEffortOptions().get(2));
    }
}