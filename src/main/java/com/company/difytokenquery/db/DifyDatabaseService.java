package com.company.difytokenquery.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** PostgreSQL metadata access used by the database browser. */
public class DifyDatabaseService {
    public DifyDatabaseService() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 PostgreSQL JDBC 驱动", e);
        }
    }

    public List<String> listDatabases(DatasourceConfig datasource) throws SQLException {
        String sql = "select datname from pg_database where datallowconn = true and not datistemplate order by datname";
        List<String> result = new ArrayList<String>();
        try (Connection connection = openConnection(datasource);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    public List<String> listSchemas(DatasourceConfig datasource, String database) throws SQLException {
        String sql = "select schema_name from information_schema.schemata "
                + "where schema_name not like 'pg_%' and schema_name <> 'information_schema' order by schema_name";
        List<String> result = new ArrayList<String>();
        try (Connection connection = openDatabase(datasource, database);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    public List<String> listTables(DatasourceConfig datasource, String database, String schema) throws SQLException {
        String sql = "select table_name from information_schema.tables where table_schema = ? "
                + "and table_type in ('BASE TABLE', 'VIEW', 'FOREIGN') order by table_name";
        List<String> result = new ArrayList<String>();
        try (Connection connection = openDatabase(datasource, database);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
        }
        return result;
    }

    public List<ColumnInfo> listColumns(DatasourceConfig datasource, String database, String schema, String table) throws SQLException {
        String sql = "select c.ordinal_position, c.column_name, c.data_type, c.udt_name, c.is_nullable, "
                + "c.column_default, coalesce(pd.description, '') "
                + "from information_schema.columns c "
                + "left join pg_catalog.pg_class pc on pc.relname = c.table_name "
                + "left join pg_catalog.pg_namespace pn on pn.oid = pc.relnamespace and pn.nspname = c.table_schema "
                + "left join pg_catalog.pg_attribute pa on pa.attrelid = pc.oid and pa.attname = c.column_name and pa.attnum > 0 "
                + "left join pg_catalog.pg_description pd on pd.objoid = pa.attrelid and pd.objsubid = pa.attnum "
                + "where c.table_schema = ? and c.table_name = ? order by c.ordinal_position";
        List<ColumnInfo> result = new ArrayList<ColumnInfo>();
        try (Connection connection = openDatabase(datasource, database);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new ColumnInfo(rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
                }
            }
        }
        return result;
    }

    private Connection openDatabase(DatasourceConfig source, String database) throws SQLException {
        DatasourceConfig copy = database == null || database.trim().isEmpty() ? source.copy() : source.copyForDatabase(database);
        copy.normalize();
        return DriverManager.getConnection(copy.getEffectiveJdbcUrl(), copy.getUsername(), copy.getPassword());
    }

    private Connection openConnection(DatasourceConfig datasource) throws SQLException {
        if (datasource == null || !datasource.isEnabled()) throw new SQLException("未选择可用的数据源");
        datasource.normalize();
        return DriverManager.getConnection(datasource.getEffectiveJdbcUrl(), datasource.getUsername(), datasource.getPassword());
    }

    public static class ColumnInfo {
        private final int ordinal;
        private final String name;
        private final String dataType;
        private final String udtName;
        private final String nullable;
        private final String defaultValue;
        private final String comment;

        public ColumnInfo(int ordinal, String name, String dataType, String udtName, String nullable, String defaultValue, String comment) {
            this.ordinal = ordinal; this.name = name; this.dataType = dataType; this.udtName = udtName;
            this.nullable = nullable; this.defaultValue = defaultValue; this.comment = comment;
        }
        public int getOrdinal() { return ordinal; }
        public String getName() { return name; }
        public String getDataType() { return dataType; }
        public String getUdtName() { return udtName; }
        public String getNullable() { return nullable; }
        public String getDefaultValue() { return defaultValue; }
        public String getComment() { return comment; }
    }
}
