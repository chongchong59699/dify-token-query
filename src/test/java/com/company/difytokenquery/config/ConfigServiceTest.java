package com.company.difytokenquery.config;

import com.company.difytokenquery.db.DatasourceConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadShouldCreateDefaultConfigWhenFileDoesNotExist() throws Exception {
        Path configFile = temporaryFolder.newFolder("config").toPath().resolve("config.json");
        ConfigService service = new ConfigService(configFile);

        AppConfig config = service.load();

        assertTrue(configFile.toFile().exists());
        assertEquals(ConfigService.DEFAULT_DATASOURCE_ID, config.getDefaultDatasourceId());
        assertEquals(1, config.getDatasources().size());
        DatasourceConfig datasource = config.getDatasources().get(0);
        assertEquals("公司170dify pg库", datasource.getName());
        assertEquals("jdbc:postgresql://192.2.2.170:5432/dify", datasource.getJdbcUrl());
        assertEquals("http://192.2.2.170:8180", datasource.getDifyFrontendUrl());
        assertEquals("postgres", datasource.getUsername());
        assertEquals("difyai123456", datasource.getPassword());
    }

    @Test
    public void saveAndLoadShouldKeepDatasourceChanges() throws Exception {
        Path configFile = temporaryFolder.newFolder("config2").toPath().resolve("config.json");
        ConfigService service = new ConfigService(configFile);
        AppConfig config = service.load();

        DatasourceConfig datasource = new DatasourceConfig();
        datasource.setId("test-id");
        datasource.setName("测试库");
        datasource.setHost("127.0.0.1");
        datasource.setPort(5433);
        datasource.setDatabase("dify_test");
        datasource.setUsername("u");
        datasource.setPassword("p");
        datasource.setEnabled(true);
        datasource.normalize();
        config.getDatasources().add(datasource);
        config.setDefaultDatasourceId("test-id");

        service.save(config);
        AppConfig loaded = service.load();

        assertEquals("test-id", loaded.getDefaultDatasourceId());
        assertEquals(2, loaded.getDatasources().size());
        assertEquals("jdbc:postgresql://127.0.0.1:5433/dify_test", loaded.getDatasources().get(1).getJdbcUrl());
    }

    @Test
    public void loadShouldFillDefaultDifyFrontendUrlForExistingBuiltInDatasource() throws Exception {
        Path configFile = temporaryFolder.newFolder("config3").toPath().resolve("config.json");
        String legacyJson = "{"
                + "\"defaultDatasourceId\":\"company-170-dify-pg\","
                + "\"datasources\":[{"
                + "\"id\":\"company-170-dify-pg\","
                + "\"name\":\"公司170dify pg库\","
                + "\"host\":\"192.2.2.170\","
                + "\"port\":5432,"
                + "\"database\":\"dify\","
                + "\"jdbcUrl\":\"jdbc:postgresql://192.2.2.170:5432/dify\","
                + "\"username\":\"postgres\","
                + "\"password\":\"difyai123456\","
                + "\"enabled\":true"
                + "}]"
                + "}";
        Files.write(configFile, legacyJson.getBytes(StandardCharsets.UTF_8));
        ConfigService service = new ConfigService(configFile);

        AppConfig loaded = service.load();

        assertEquals(ConfigService.DEFAULT_DIFY_FRONTEND_URL, loaded.getDatasources().get(0).getDifyFrontendUrl());
    }
}
