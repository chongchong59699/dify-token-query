package com.company.difytokenquery.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SqlQueryService {
    public static final int MAX_PAGE_SIZE = 1000;

    public SqlQueryService() {
        try { Class.forName("org.postgresql.Driver"); }
        catch (ClassNotFoundException e) { throw new IllegalStateException("未找到 PostgreSQL JDBC 驱动", e); }
    }

    public SqlQueryResult executePage(String sql, DatasourceConfig datasource, int page, int pageSize) throws SQLException {
        String normalized = normalizeSql(sql);
        if (normalized.isEmpty()) throw new SQLException("SQL 不能为空");
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
        if (!isQuery(normalized)) {
            try (Connection connection = openConnection(datasource); Statement statement = connection.createStatement()) {
                return SqlQueryResult.update(statement.executeUpdate(normalized));
            }
        }
        String pagedSql = "select * from (" + normalized + ") codex_query_page limit ? offset ?";
        List<String> columns = new ArrayList<String>();
        List<List<String>> rows = new ArrayList<List<String>>();
        try (Connection connection = openConnection(datasource); PreparedStatement statement = connection.prepareStatement(pagedSql)) {
            statement.setInt(1, safeSize);
            statement.setLong(2, (long) safePage * safeSize);
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnLabel(i));
                while (rs.next()) {
                    List<String> row = new ArrayList<String>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) row.add(valueAsText(rs.getObject(i)));
                    rows.add(row);
                }
            }
        }
        return SqlQueryResult.rows(columns, rows);
    }

    public long count(String sql, DatasourceConfig datasource) throws SQLException {
        String normalized = normalizeSql(sql);
        if (!isQuery(normalized)) return -1L;
        String countSql = "select count(*) from (" + normalized + ") codex_query_count";
        try (Connection connection = openConnection(datasource);
             PreparedStatement statement = connection.prepareStatement(countSql);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public static String normalizeSql(String sql) {
        String result = sql == null ? "" : sql.trim();
        while (result.endsWith(";")) result = result.substring(0, result.length() - 1).trim();
        return result;
    }

    public static boolean isQuery(String sql) {
        String normalized = normalizeSql(sql).toLowerCase();
        int end = 0;
        while (end < normalized.length() && Character.isLetter(normalized.charAt(end))) end++;
        String keyword = normalized.substring(0, end);
        return "select".equals(keyword) || "with".equals(keyword) || "values".equals(keyword)
                || "show".equals(keyword) || "explain".equals(keyword);
    }

    private Connection openConnection(DatasourceConfig datasource) throws SQLException {
        if (datasource == null || !datasource.isEnabled()) throw new SQLException("未选择可用的数据源");
        datasource.normalize();
        return DriverManager.getConnection(datasource.getEffectiveJdbcUrl(), datasource.getUsername(), datasource.getPassword());
    }

    private String valueAsText(Object value) {
        if (value == null) return "";
        if (value instanceof byte[]) return "[binary " + ((byte[]) value).length + " bytes]";
        return String.valueOf(value);
    }
}
