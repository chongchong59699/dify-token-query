package com.company.difytokenquery.db;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

public class DatasourceConfig {
    private String id;
    private String name;
    private String host;
    private int port = 5432;
    private String database;
    private String jdbcUrl;
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
        copy.setUsername(username);
        copy.setPassword(password);
        copy.setEnabled(enabled);
        return copy;
    }

    public void copyFrom(DatasourceConfig source) {
        setId(source.getId());
        setName(source.getName());
        setHost(source.getHost());
        setPort(source.getPort());
        setDatabase(source.getDatabase());
        setJdbcUrl(source.getJdbcUrl());
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

    @Override
    public String toString() {
        return name == null ? "" : name;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
