package com.company.difytokenquery.dify;

import com.company.difytokenquery.dify.DifyConsoleClient.HttpResponse;
import com.company.difytokenquery.dify.DifyConsoleClient.HttpTransport;
import com.company.difytokenquery.model.BatchExecutionResult;
import com.company.difytokenquery.model.BatchExecutionResult.Status;
import com.company.difytokenquery.model.DifyAppSummary;
import com.company.difytokenquery.model.DifyAppSummary.ModelRef;
import com.company.difytokenquery.model.DifyModelOption;
import com.company.difytokenquery.model.ReplacementOptions;
import com.company.difytokenquery.model.ReplacementOptions.ParamStrategy;
import com.company.difytokenquery.model.ReplacementPlanItem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BatchModelReplaceServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void executeShouldBackupYmlBeforeSavingAndIsolateFailures() throws Exception {
        final AtomicInteger saveCallCount = new AtomicInteger(0);
        final AtomicInteger publishCallCount = new AtomicInteger(0);

        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpResponse execute(String method, String url, Map<String, String> headers, String body) {
                if ("GET".equals(method) && url.endsWith("/console/api/apps/app-wf-1/workflows/draft")) {
                    return new HttpResponse(200, "{"
                            + "\"hash\":\"h1\","
                            + "\"features\":{},"
                            + "\"environment_variables\":[],"
                            + "\"conversation_variables\":[],"
                            + "\"graph\":{\"nodes\":[{\"id\":\"n1\",\"data\":{\"type\":\"llm\",\"title\":\"LLM\",\"model\":{\"provider\":\"p1\",\"name\":\"m1\",\"mode\":\"chat\",\"completion_params\":{\"temperature\":0.3}}}}],\"edges\":[]}"
                            + "}");
                }
                if ("GET".equals(method) && url.contains("/console/api/apps/app-wf-1/export")) {
                    return new HttpResponse(200, "{\"data\":\"app:\\n  name: Workflow1\\n\"}");
                }
                if ("POST".equals(method) && url.endsWith("/console/api/apps/app-wf-1/workflows/draft")) {
                    saveCallCount.incrementAndGet();
                    return new HttpResponse(200, "{\"result\":\"success\"}");
                }
                if ("POST".equals(method) && url.endsWith("/console/api/apps/app-wf-1/workflows/publish")) {
                    publishCallCount.incrementAndGet();
                    return new HttpResponse(200, "{\"result\":\"success\"}");
                }
                // Second app fails during DSL export -> MUST NOT call POST /model-config!
                if ("GET".equals(method) && url.endsWith("/console/api/apps/app-chat-2")) {
                    return new HttpResponse(200, "{\"id\":\"app-chat-2\",\"model_config\":{\"model\":{\"provider\":\"p1\",\"name\":\"m1\",\"mode\":\"chat\",\"completion_params\":{}}}}");
                }
                if ("GET".equals(method) && url.contains("/console/api/apps/app-chat-2/export")) {
                    return new HttpResponse(500, "{\"message\":\"export failed\"}");
                }
                if ("POST".equals(method) && url.endsWith("/console/api/apps/app-chat-2/model-config")) {
                    saveCallCount.incrementAndGet();
                    return new HttpResponse(200, "{\"result\":\"success\"}");
                }
                return new HttpResponse(404, "{}");
            }
        };

        DifyConsoleClient client = new DifyConsoleClient("http://192.2.2.170:8180", transport);
        client.setTokens("admin@test.com", "tok", "ref");
        AppBackupService backupService = new AppBackupService(temporaryFolder.newFolder("backups").toPath());
        ModelReplacementEngine engine = new ModelReplacementEngine();
        BatchModelReplaceService service = new BatchModelReplaceService(client, backupService, engine);

        DifyAppSummary wfApp = new DifyAppSummary(
                "app-wf-1", "测试/工作流:1", "workflow", "normal", "", "pub-wf-1", true, "",
                Collections.<String>emptyList(),
                Collections.singletonList(new ModelRef("LLM", "n1", "llm", "p1", "m1", "chat", "{}"))
        );
        DifyAppSummary chatApp = new DifyAppSummary(
                "app-chat-2", "对话应用2", "chat", "normal", "amc-2", "", false, "",
                Collections.<String>emptyList(),
                Collections.singletonList(new ModelRef("主模型", "", "app_model_config", "p1", "m1", "chat", "{}"))
        );

        DifyModelOption target = DifyModelOption.simple("p2", "m2", "chat");
        ReplacementOptions options = new ReplacementOptions("p1", "m1", false, target, ParamStrategy.KEEP_COMMON, true);
        List<ReplacementPlanItem> plan = engine.buildPlan(Arrays.asList(wfApp, chatApp), options);

        Path batchDir = backupService.createBatchDirectory();
        List<BatchExecutionResult> results = service.execute(plan, options, batchDir, null);

        assertEquals(2, results.size());
        assertEquals(Status.SUCCESS, results.get(0).getStatus());
        assertTrue(results.get(0).isPublished());
        Path ymlPath = Paths.get(results.get(0).getBackupYmlPath());
        assertTrue(Files.exists(ymlPath));
        assertTrue(new String(Files.readAllBytes(ymlPath), StandardCharsets.UTF_8).contains("name: Workflow1"));
        assertFalse(results.get(0).getPublishedBackupYmlPath().isEmpty());
        assertTrue(Files.exists(Paths.get(results.get(0).getPublishedBackupYmlPath())));
        assertTrue(Files.exists(batchDir.resolve("manifest.json")));

        // Second app failed export -> must fail safely without calling save
        assertEquals(Status.FAILED, results.get(1).getStatus());
        assertEquals(1, saveCallCount.get());
        assertEquals(1, publishCallCount.get());
    }
}