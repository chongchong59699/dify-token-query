package com.company.difytokenquery.dify;

import com.company.difytokenquery.dify.AppBackupService.BackupRecord;
import com.company.difytokenquery.dify.ModelReplacementEngine.ApplyOutcome;
import com.company.difytokenquery.model.BatchExecutionResult;
import com.company.difytokenquery.model.BatchExecutionResult.Status;
import com.company.difytokenquery.model.DifyAppSummary;
import com.company.difytokenquery.model.ReplacementOptions;
import com.company.difytokenquery.model.ReplacementPlanItem;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executes batch model replacement per app with mandatory YAML DSL backup before saving. */
public class BatchModelReplaceService {
    public interface ProgressListener {
        void onProgress(int completedCount, int totalCount, BatchExecutionResult latestResult);
    }

    private final DifyConsoleClient consoleClient;
    private final AppBackupService backupService;
    private final ModelReplacementEngine engine;

    public BatchModelReplaceService(DifyConsoleClient consoleClient,
                                    AppBackupService backupService,
                                    ModelReplacementEngine engine) {
        this.consoleClient = consoleClient;
        this.backupService = backupService;
        this.engine = engine;
    }

    public List<BatchExecutionResult> execute(List<ReplacementPlanItem> planItems,
                                              ReplacementOptions options,
                                              Path batchBackupDir,
                                              ProgressListener listener) {
        List<BatchExecutionResult> results = new ArrayList<BatchExecutionResult>();
        if (planItems == null || planItems.isEmpty()) {
            return results;
        }
        int total = planItems.size();
        for (int i = 0; i < total; i++) {
            ReplacementPlanItem item = planItems.get(i);
            BatchExecutionResult result = executeOne(item, options, batchBackupDir);
            results.add(result);
            if (listener != null) {
                listener.onProgress(i + 1, total, result);
            }
        }
        return results;
    }

    public BatchExecutionResult executeOne(ReplacementPlanItem item,
                                           ReplacementOptions options,
                                           Path batchBackupDir) {
        DifyAppSummary app = item == null ? null : item.getApp();
        if (app == null) {
            return new BatchExecutionResult("", "", "", Status.SKIPPED, 0, false, "", "", "应用信息为空");
        }
        if (!item.isExecutable()) {
            return new BatchExecutionResult(
                    app.getAppId(), app.getAppName(), app.getMode(),
                    Status.SKIPPED, 0, false, "", "", item.getSkippedReason()
            );
        }

        try {
            if (app.isWorkflowMode()) {
                return executeWorkflowApp(app, options, batchBackupDir);
            }
            return executeBasicApp(app, options, batchBackupDir);
        } catch (Exception e) {
            String message = e.getMessage() == null || e.getMessage().trim().isEmpty()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            return new BatchExecutionResult(
                    app.getAppId(), app.getAppName(), app.getMode(),
                    Status.FAILED, 0, false, "", "", message
            );
        }
    }

    private BatchExecutionResult executeBasicApp(DifyAppSummary app,
                                                 ReplacementOptions options,
                                                 Path batchBackupDir) throws Exception {
        JsonNode detail = consoleClient.getAppDetail(app.getAppId());
        ApplyOutcome outcome = engine.applyToAppDetail(app.getAppId(), detail, options);
        if (!outcome.isChanged()) {
            return new BatchExecutionResult(
                    app.getAppId(), app.getAppName(), app.getMode(),
                    Status.SKIPPED, 0, false, "", "", outcome.getMessage()
            );
        }

        String yaml = consoleClient.exportAppDsl(app.getAppId(), null);
        BackupRecord backup = backupService.backupApp(
                batchBackupDir, app.getAppId(), app.getAppName(), app.getMode(),
                yaml, null, detail, outcome.getChanges()
        );

        consoleClient.updateAppModelConfig(app.getAppId(), outcome.getPayload());
        return new BatchExecutionResult(
                app.getAppId(), app.getAppName(), app.getMode(),
                Status.SUCCESS,
                outcome.getChanges().size(),
                false,
                backup.getYmlFile().toAbsolutePath().toString(),
                "",
                "已更新模型配置"
        );
    }

    private BatchExecutionResult executeWorkflowApp(DifyAppSummary app,
                                                    ReplacementOptions options,
                                                    Path batchBackupDir) throws Exception {
        JsonNode draft = consoleClient.getWorkflowDraft(app.getAppId());
        ApplyOutcome outcome = engine.applyToWorkflowDraft(app.getAppId(), draft, options);
        if (!outcome.isChanged()) {
            return new BatchExecutionResult(
                    app.getAppId(), app.getAppName(), app.getMode(),
                    Status.SKIPPED, 0, false, "", "", outcome.getMessage()
            );
        }

        String draftYaml = consoleClient.exportAppDsl(app.getAppId(), null);
        String publishedYaml = null;
        if (options.isAutoPublishWorkflow() && app.hasPublishedWorkflow()) {
            publishedYaml = consoleClient.exportAppDsl(app.getAppId(), app.getPublishedWorkflowId());
        }

        BackupRecord backup = backupService.backupApp(
                batchBackupDir, app.getAppId(), app.getAppName(), app.getMode(),
                draftYaml, publishedYaml, draft, outcome.getChanges()
        );

        consoleClient.updateWorkflowDraft(app.getAppId(), outcome.getPayload());

        boolean published = false;
        String detailMsg = "已更新工作流草稿";
        if (options.isAutoPublishWorkflow()) {
            consoleClient.publishWorkflow(app.getAppId(), "", "Batch model replace");
            published = true;
            detailMsg = "已更新草稿并发布上线";
        }

        return new BatchExecutionResult(
                app.getAppId(), app.getAppName(), app.getMode(),
                Status.SUCCESS,
                outcome.getChanges().size(),
                published,
                backup.getYmlFile().toAbsolutePath().toString(),
                backup.getPublishedYmlFile() == null ? "" : backup.getPublishedYmlFile().toAbsolutePath().toString(),
                detailMsg
        );
    }
}