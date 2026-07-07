package com.company.difytokenquery.ui;

import com.company.difytokenquery.model.AppQueryResult;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ResultTableModel extends AbstractTableModel {
    private final String[] columns = {"token", "app_id", "name", "mode", "数据源", "状态", "错误信息"};
    private final List<AppQueryResult> results = new ArrayList<AppQueryResult>();

    public void setResults(List<AppQueryResult> newResults) {
        results.clear();
        if (newResults != null) {
            results.addAll(newResults);
        }
        fireTableDataChanged();
    }

    public AppQueryResult getResultAt(int row) {
        return results.get(row);
    }

    public List<AppQueryResult> getResults() {
        return new ArrayList<AppQueryResult>(results);
    }

    @Override
    public int getRowCount() {
        return results.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AppQueryResult result = results.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return result.getToken();
            case 1:
                return result.getAppId();
            case 2:
                return result.getAppName();
            case 3:
                return result.getMode();
            case 4:
                return result.getDatasourceName();
            case 5:
                return result.getStatus() == null ? "" : result.getStatus().name();
            case 6:
                return result.getErrorMessage();
            default:
                return "";
        }
    }
}
