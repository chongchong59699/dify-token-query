package com.company.difytokenquery.config;

import com.company.difytokenquery.db.DatasourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConfigService {
    public static final String DEFAULT_DATASOURCE_ID = "company-170-dify-pg";
    public static final String DEFAULT_DATASOURCE_NAME = "公司170dify pg库";

    private final Path configFile;
    private final ObjectMapper objectMapper;

    public ConfigService() {
        this(defaultConfigFile());
    }

    public ConfigService(Path configFile) {
        this.configFile = configFile;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public AppConfig load() throws IOException {
        if (!Files.exists(configFile) || Files.size(configFile) == 0) {
            AppConfig defaultConfig = createDefaultConfig();
            save(defaultConfig);
            return defaultConfig;
        }

        AppConfig config = objectMapper.readValue(configFile.toFile(), AppConfig.class);
        normalize(config);
        save(config);
        return config;
    }

    public void save(AppConfig config) throws IOException {
        normalize(config);
        Path parent = configFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        objectMapper.writeValue(configFile.toFile(), config);
    }

    public Path getConfigFile() {
        return configFile;
    }

    public static Path defaultConfigFile() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.trim().isEmpty()) {
            appData = Paths.get(System.getProperty("user.home"), "AppData", "Roaming").toString();
        }
        return Paths.get(appData, "DifyTokenQueryTool", "config.json");
    }

    public static AppConfig createDefaultConfig() {
        AppConfig config = new AppConfig();
        DatasourceConfig datasource = new DatasourceConfig();
        datasource.setId(DEFAULT_DATASOURCE_ID);
        datasource.setName(DEFAULT_DATASOURCE_NAME);
        datasource.setHost("192.2.2.170");
        datasource.setPort(5432);
        datasource.setDatabase("dify");
        datasource.setJdbcUrl("jdbc:postgresql://192.2.2.170:5432/dify");
        datasource.setUsername("postgres");
        datasource.setPassword("difyai123456");
        datasource.setEnabled(true);

        List<DatasourceConfig> datasources = new ArrayList<DatasourceConfig>();
        datasources.add(datasource);
        config.setDatasources(datasources);
        config.setDefaultDatasourceId(DEFAULT_DATASOURCE_ID);
        return config;
    }

    private void normalize(AppConfig config) {
        if (config.getDatasources() == null) {
            config.setDatasources(new ArrayList<DatasourceConfig>());
        }
        if (config.getDatasources().isEmpty()) {
            AppConfig defaultConfig = createDefaultConfig();
            config.setDatasources(defaultConfig.getDatasources());
            config.setDefaultDatasourceId(defaultConfig.getDefaultDatasourceId());
            return;
        }

        String defaultId = config.getDefaultDatasourceId();
        boolean defaultExists = false;
        for (DatasourceConfig datasource : config.getDatasources()) {
            datasource.normalize();
            if (datasource.getId().equals(defaultId)) {
                defaultExists = true;
            }
        }
        if (!defaultExists) {
            config.setDefaultDatasourceId(config.getDatasources().get(0).getId());
        }
    }
}
