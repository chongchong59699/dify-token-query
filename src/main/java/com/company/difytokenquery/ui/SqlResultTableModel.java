package com.company.difytokenquery.ui;

import com.company.difytokenquery.db.SqlQueryResult;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class SqlResultTableModel extends AbstractTableModel {
    private List<String> columns = new ArrayList<String>();
    private List<List<String>> rows = new ArrayList<List<String>>();

    public void setResult(SqlQueryResult result) {
        columns = result == null ? new ArrayList<String>() : result.getColumns();
        rows = result == null ? new ArrayList<List<String>>() : result.getRows();
        fireTableStructureChanged();
    }
    public int getRowCount() { return rows.size(); }
    public int getColumnCount() { return columns.size(); }
    public String getColumnName(int column) { return columns.get(column); }
    public Object getValueAt(int row, int column) { return rows.get(row).get(column); }
}
