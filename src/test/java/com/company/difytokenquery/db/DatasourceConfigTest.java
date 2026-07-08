package com.company.difytokenquery.db;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DatasourceConfigTest {
    @Test
    public void buildJdbcUrlShouldUsePostgresqlFormat() {
        assertEquals(
                "jdbc:postgresql://192.2.2.170:5432/dify",
                DatasourceConfig.buildJdbcUrl("192.2.2.170", 5432, "dify")
        );
    }

    @Test
    public void normalizeShouldGenerateJdbcUrlWhenMissing() {
        DatasourceConfig datasource = new DatasourceConfig();
        datasource.setName("测试库");
        datasource.setHost("localhost");
        datasource.setPort(15432);
        datasource.setDatabase("dify");
        datasource.normalize();

        assertEquals("jdbc:postgresql://localhost:15432/dify", datasource.getJdbcUrl());
    }

    @Test
    public void buildDifyConfigurationUrlShouldAppendAppPath() {
        assertEquals(
                "http://192.2.2.170:8180/app/a89939ed-57c2-4a7c-9af6-1b5c46545cdb/configuration",
                DatasourceConfig.buildDifyConfigurationUrl(
                        "http://192.2.2.170:8180/",
                        "a89939ed-57c2-4a7c-9af6-1b5c46545cdb"
                )
        );
    }
}
