package com.company.difytokenquery.db;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatasourceConfig {
    private String id;
    private String name;
    private String host;
    private int port = 5432;
    private String database;
    private String jdbcUrl;
    private String difyFrontendUrl;
    private String username;
    private String password;
    private boolean enabled = true;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getDifyFrontendUrl() {
        return difyFrontendUrl;
    }

    public void setDifyFrontendUrl(String difyFrontendUrl) {
        this.difyFrontendUrl = difyFrontendUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @JsonIgnore
    public String getEffectiveJdbcUrl() {
        if (!isBlank(jdbcUrl)) {
            return jdbcUrl.trim();
        }
        return buildJdbcUrl(host, port, database);
    }

    public void normalize() {
        if (isBlank(id)) {
            id = UUID.randomUUID().toString();
        }
        if (port <= 0) {
            port = 5432;
        }
        if (isBlank(name)) {
            name = "Dify PG库";
        }
        if (isBlank(jdbcUrl) && !isBlank(host) && !isBlank(database)) {
            jdbcUrl = buildJdbcUrl(host, port, database);
        }
        if (difyFrontendUrl == null) {
            difyFrontendUrl = "";
        } else {
            difyFrontendUrl = trimTrailingSlashes(difyFrontendUrl.trim());
        }
        if (username == null) {
            username = "";
        }
        if (password == null) {
            password = "";
        }
    }

    public DatasourceConfig copy() {
        DatasourceConfig copy = new DatasourceConfig();
        copy.setId(id);
        copy.setName(name);
        copy.setHost(host);
        copy.setPort(port);
        copy.setDatabase(database);
        copy.setJdbcUrl(jdbcUrl);
        copy.setDifyFrontendUrl(difyFrontendUrl);
        copy.setUsername(username);
        copy.setPassword(password);
        copy.setEnabled(enabled);
        return copy;
    }

    /** Returns a copy connected to another PostgreSQL database while preserving custom JDBC parameters. */
    public DatasourceConfig copyForDatabase(String targetDatabase) {
        DatasourceConfig copy = copy();
        String databaseName = targetDatabase == null ? "" : targetDatabase.trim();
        copy.setDatabase(databaseName);
        Matcher matcher = Pattern.compile("^(jdbc:postgresql://[^/?]+)(?:/[^?]*)?(\\?.*)?$", Pattern.CASE_INSENSITIVE).matcher(getEffectiveJdbcUrl());
        if (matcher.matches()) {
            copy.setJdbcUrl(matcher.group(1) + "/" + databaseName + (matcher.group(2) == null ? "" : matcher.group(2)));
        } else if (!isBlank(host)) {
            copy.setJdbcUrl(buildJdbcUrl(host, port, databaseName));
        }
        copy.normalize();
        return copy;
    }

    public void copyFrom(DatasourceConfig source) {
        setId(source.getId());
        setName(source.getName());
        setHost(source.getHost());
        setPort(source.getPort());
        setDatabase(source.getDatabase());
        setJdbcUrl(source.getJdbcUrl());
        setDifyFrontendUrl(source.getDifyFrontendUrl());
        setUsername(source.getUsername());
        setPassword(source.getPassword());
        setEnabled(source.isEnabled());
    }

    public static String buildJdbcUrl(String host, int port, String database) {
        String safeHost = host == null ? "" : host.trim();
        String safeDatabase = database == null ? "" : database.trim();
        int safePort = port <= 0 ? 5432 : port;
        return "jdbc:postgresql://" + safeHost + ":" + safePort + "/" + safeDatabase;
    }

    public static String buildDifyConfigurationUrl(String difyFrontendUrl, String appId) {
        if (isBlank(difyFrontendUrl) || isBlank(appId)) {
            return "";
        }
        return trimTrailingSlashes(difyFrontendUrl.trim()) + "/app/" + appId.trim() + "/configuration";
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimTrailingSlashes(String value) {
        String result = value == null ? "" : value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
