package com.company.difytokenquery.db;

import com.company.difytokenquery.model.AppQueryResult;
import com.company.difytokenquery.parser.TokenParser;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DifyQueryService {
    private static final String QUERY_SQL =
            "select t.token, a.id, a.name, a.mode " +
            "from api_tokens t " +
            "join apps a on a.id = t.app_id " +
            "where t.token = ?";

    private final TokenParser tokenParser = new TokenParser();

    public DifyQueryService() {
        loadDriver();
        DriverManager.setLoginTimeout(8);
    }

    public List<AppQueryResult> query(List<String> tokens, DatasourceConfig datasource) {
        List<AppQueryResult> results = new ArrayList<AppQueryResult>();
        if (tokens == null || tokens.isEmpty()) {
            return results;
        }

        String datasourceName = datasource == null ? "" : datasource.getName();
        if (datasource == null || !datasource.isEnabled()) {
            for (String token : tokens) {
                results.add(AppQueryResult.dbError(token, datasourceName, "未选择可用的数据源"));
            }
            return results;
        }

        List<String> validTokens = new ArrayList<String>();
        for (String token : tokens) {
            if (tokenParser.isValidToken(token)) {
                validTokens.add(token);
            } else {
                results.add(AppQueryResult.invalidToken(token, datasourceName));
            }
        }

        if (validTokens.isEmpty()) {
            return results;
        }

        try (Connection connection = openConnection(datasource);
             PreparedStatement statement = connection.prepareStatement(QUERY_SQL)) {
            for (String token : validTokens) {
                results.add(queryOne(statement, token, datasource));
            }
        } catch (SQLException e) {
            String message = rootMessage(e);
            for (String token : validTokens) {
                results.add(AppQueryResult.dbError(token, datasourceName, message));
            }
        }
        return results;
    }

    public void testConnection(DatasourceConfig datasource) throws SQLException {
        if (datasource == null) {
            throw new SQLException("未选择数据源");
        }
        try (Connection connection = openConnection(datasource)) {
            if (!connection.isValid(5)) {
                throw new SQLException("数据库连接不可用");
            }
        }
    }

    private AppQueryResult queryOne(PreparedStatement statement, String token, DatasourceConfig datasource) throws SQLException {
        statement.clearParameters();
        statement.setString(1, token);
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                String appId = resultSet.getString("id");
                return AppQueryResult.success(
                        token,
                        appId,
                        resultSet.getString("name"),
                        resultSet.getString("mode"),
                        datasource.getName(),
                        DatasourceConfig.buildDifyConfigurationUrl(datasource.getDifyFrontendUrl(), appId)
                );
            }
            return AppQueryResult.notFound(token, datasource.getName());
        }
    }

    private Connection openConnection(DatasourceConfig datasource) throws SQLException {
        datasource.normalize();
        return DriverManager.getConnection(
                datasource.getEffectiveJdbcUrl(),
                datasource.getUsername(),
                datasource.getPassword()
        );
    }

    private void loadDriver() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 PostgreSQL JDBC 驱动", e);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty() ? current.getClass().getSimpleName() : message;
    }
}
