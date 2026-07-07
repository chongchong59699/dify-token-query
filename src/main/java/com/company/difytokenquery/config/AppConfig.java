package com.company.difytokenquery.config;

import com.company.difytokenquery.db.DatasourceConfig;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    private String defaultDatasourceId;
    private List<DatasourceConfig> datasources = new ArrayList<DatasourceConfig>();

    public String getDefaultDatasourceId() {
        return defaultDatasourceId;
    }

    public void setDefaultDatasourceId(String defaultDatasourceId) {
        this.defaultDatasourceId = defaultDatasourceId;
    }

    public List<DatasourceConfig> getDatasources() {
        return datasources;
    }

    public void setDatasources(List<DatasourceConfig> datasources) {
        this.datasources = datasources;
    }
}
