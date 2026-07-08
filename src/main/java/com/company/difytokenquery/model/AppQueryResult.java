package com.company.difytokenquery.model;

public class AppQueryResult {
    private final String token;
    private final String appId;
    private final String appName;
    private final String mode;
    private final String datasourceName;
    private final String configurationUrl;
    private final QueryStatus status;
    private final String errorMessage;

    public AppQueryResult(String token, String appId, String appName, String mode,
                          String datasourceName, String configurationUrl, QueryStatus status, String errorMessage) {
        this.token = token;
        this.appId = appId;
        this.appName = appName;
        this.mode = mode;
        this.datasourceName = datasourceName;
        this.configurationUrl = configurationUrl;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public static AppQueryResult success(String token, String appId, String appName, String mode,
                                         String datasourceName, String configurationUrl) {
        return new AppQueryResult(token, appId, appName, mode, datasourceName, configurationUrl, QueryStatus.SUCCESS, "");
    }

    public static AppQueryResult notFound(String token, String datasourceName) {
        return new AppQueryResult(token, "", "", "", datasourceName, "", QueryStatus.NOT_FOUND, "");
    }

    public static AppQueryResult dbError(String token, String datasourceName, String errorMessage) {
        return new AppQueryResult(token, "", "", "", datasourceName, "", QueryStatus.DB_ERROR, errorMessage);
    }

    public static AppQueryResult invalidToken(String token, String datasourceName) {
        return new AppQueryResult(token, "", "", "", datasourceName, "", QueryStatus.INVALID_TOKEN, "Token格式不符合 app-... 规则");
    }

    public String getToken() {
        return token;
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

    public String getDatasourceName() {
        return datasourceName;
    }

    public String getConfigurationUrl() {
        return configurationUrl;
    }

    public QueryStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
