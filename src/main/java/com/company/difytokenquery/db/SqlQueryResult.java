package com.company.difytokenquery.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A bounded page of SQL results. Values are strings so the Swing table can render every JDBC type safely. */
public class SqlQueryResult {
    private final List<String> columns;
    private final List<List<String>> rows;
    private final long affectedRows;

    private SqlQueryResult(List<String> columns, List<List<String>> rows, long affectedRows) {
        this.columns = columns == null ? Collections.<String>emptyList() : new ArrayList<String>(columns);
        this.rows = rows == null ? Collections.<List<String>>emptyList() : new ArrayList<List<String>>(rows);
        this.affectedRows = affectedRows;
    }

    public static SqlQueryResult rows(List<String> columns, List<List<String>> rows) {
        return new SqlQueryResult(columns, rows, -1L);
    }

    public static SqlQueryResult update(long affectedRows) {
        return new SqlQueryResult(Collections.<String>emptyList(), Collections.<List<String>>emptyList(), affectedRows);
    }

    public List<String> getColumns() { return new ArrayList<String>(columns); }
    public List<List<String>> getRows() { return new ArrayList<List<String>>(rows); }
    public long getAffectedRows() { return affectedRows; }
    public boolean isResultSet() { return affectedRows < 0; }
}
