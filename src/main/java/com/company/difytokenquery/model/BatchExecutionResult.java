package com.company.difytokenquery.model;

public class BatchExecutionResult {
    public enum Status {
        SUCCESS("成功"),
        SKIPPED("跳过"),
        FAILED("失败");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String appId;
    private final String appName;
    private final String mode;
    private final Status status;
    private final int changedCount;
    private final boolean published;
    private final String backupYmlPath;
    private final String publishedBackupYmlPath;
    private final String message;

    public BatchExecutionResult(String appId, String appName, String mode, Status status,
                                int changedCount, boolean published,
                                String backupYmlPath, String publishedBackupYmlPath,
                                String message) {
        this.appId = appId;
        this.appName = appName;
        this.mode = mode;
        this.status = status;
        this.changedCount = changedCount;
        this.published = published;
        this.backupYmlPath = backupYmlPath == null ? "" : backupYmlPath;
        this.publishedBackupYmlPath = publishedBackupYmlPath == null ? "" : publishedBackupYmlPath;
        this.message = message == null ? "" : message;
    }

    public String getAppId() { return appId; }
    public String getAppName() { return appName; }
    public String getMode() { return mode; }
    public Status getStatus() { return status; }
    public int getChangedCount() { return changedCount; }
    public boolean isPublished() { return published; }
    public String getBackupYmlPath() { return backupYmlPath; }
    public String getPublishedBackupYmlPath() { return publishedBackupYmlPath; }
    public String getMessage() { return message; }
}