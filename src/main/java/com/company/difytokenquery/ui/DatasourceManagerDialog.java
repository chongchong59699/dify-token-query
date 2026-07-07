package com.company.difytokenquery.ui;

import com.company.difytokenquery.config.AppConfig;
import com.company.difytokenquery.config.ConfigService;
import com.company.difytokenquery.db.DatasourceConfig;
import com.company.difytokenquery.db.DifyQueryService;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class DatasourceManagerDialog extends JDialog {
    private final AppConfig config;
    private final ConfigService configService;
    private final DifyQueryService queryService;
    private final DatasourceTableModel tableModel;
    private final JTable table;

    private boolean changed;

    public DatasourceManagerDialog(Frame owner, AppConfig config, ConfigService configService, DifyQueryService queryService) {
        super(owner, "管理数据库", true);
        this.config = config;
        this.configService = configService;
        this.queryService = queryService;
        this.tableModel = new DatasourceTableModel();
        this.table = new JTable(tableModel);
        initComponents();
        setSize(980, 460);
        setLocationRelativeTo(owner);
    }

    public boolean isChanged() {
        return changed;
    }

    private void initComponents() {
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setPreferredWidth(56);
        table.getColumnModel().getColumn(1).setPreferredWidth(56);
        table.getColumnModel().getColumn(2).setPreferredWidth(160);
        table.getColumnModel().getColumn(7).setPreferredWidth(300);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addButton = new JButton("新增");
        JButton editButton = new JButton("编辑");
        JButton deleteButton = new JButton("删除");
        JButton defaultButton = new JButton("设为默认");
        JButton testButton = new JButton("测试连接");
        JButton closeButton = new JButton("关闭");

        addButton.addActionListener(e -> addDatasource());
        editButton.addActionListener(e -> editDatasource());
        deleteButton.addActionListener(e -> deleteDatasource());
        defaultButton.addActionListener(e -> setDefaultDatasource());
        testButton.addActionListener(e -> testSelectedDatasource(testButton));
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(defaultButton);
        buttonPanel.add(testButton);
        buttonPanel.add(closeButton);

        setLayout(new BorderLayout(8, 8));
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void addDatasource() {
        DatasourceEditDialog dialog = new DatasourceEditDialog(this, null);
        dialog.setVisible(true);
        DatasourceConfig result = dialog.getResult();
        if (result == null) {
            return;
        }
        config.getDatasources().add(result);
        if (config.getDatasources().size() == 1) {
            config.setDefaultDatasourceId(result.getId());
        }
        saveAndRefresh();
    }

    private void editDatasource() {
        DatasourceConfig selected = getSelectedDatasource();
        if (selected == null) {
            showSelectTip();
            return;
        }
        DatasourceEditDialog dialog = new DatasourceEditDialog(this, selected);
        dialog.setVisible(true);
        DatasourceConfig result = dialog.getResult();
        if (result == null) {
            return;
        }
        selected.copyFrom(result);
        saveAndRefresh();
    }

    private void deleteDatasource() {
        DatasourceConfig selected = getSelectedDatasource();
        if (selected == null) {
            showSelectTip();
            return;
        }
        if (config.getDatasources().size() <= 1) {
            JOptionPane.showMessageDialog(this, "至少保留一个数据库配置", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "确定删除：" + selected.getName() + "？", "确认删除", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        config.getDatasources().remove(selected);
        if (selected.getId().equals(config.getDefaultDatasourceId())) {
            config.setDefaultDatasourceId(config.getDatasources().get(0).getId());
        }
        saveAndRefresh();
    }

    private void setDefaultDatasource() {
        DatasourceConfig selected = getSelectedDatasource();
        if (selected == null) {
            showSelectTip();
            return;
        }
        config.setDefaultDatasourceId(selected.getId());
        saveAndRefresh();
    }

    private void testSelectedDatasource(JButton button) {
        DatasourceConfig selected = getSelectedDatasource();
        if (selected == null) {
            showSelectTip();
            return;
        }
        button.setEnabled(false);
        new SwingWorker<Void, Void>() {
            private Exception failure;

            @Override
            protected Void doInBackground() {
                try {
                    queryService.testConnection(selected);
                } catch (SQLException e) {
                    failure = e;
                }
                return null;
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                if (failure == null) {
                    JOptionPane.showMessageDialog(DatasourceManagerDialog.this, "连接成功", "测试连接", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(DatasourceManagerDialog.this, failure.getMessage(), "连接失败", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private DatasourceConfig getSelectedDatasource() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(selectedRow);
        return config.getDatasources().get(modelRow);
    }

    private void saveAndRefresh() {
        try {
            configService.save(config);
            changed = true;
            tableModel.fireTableDataChanged();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "保存配置失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showSelectTip() {
        JOptionPane.showMessageDialog(this, "请先选择一条数据库配置", "提示", JOptionPane.WARNING_MESSAGE);
    }

    private class DatasourceTableModel extends AbstractTableModel {
        private final String[] columns = {"默认", "启用", "名称", "Host", "Port", "Database", "用户名", "JDBC URL"};

        @Override
        public int getRowCount() {
            List<DatasourceConfig> datasources = config.getDatasources();
            return datasources == null ? 0 : datasources.size();
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
            DatasourceConfig datasource = config.getDatasources().get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return datasource.getId().equals(config.getDefaultDatasourceId());
                case 1:
                    return datasource.isEnabled();
                case 2:
                    return datasource.getName();
                case 3:
                    return datasource.getHost();
                case 4:
                    return datasource.getPort();
                case 5:
                    return datasource.getDatabase();
                case 6:
                    return datasource.getUsername();
                case 7:
                    return datasource.getEffectiveJdbcUrl();
                default:
                    return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0 || columnIndex == 1) {
                return Boolean.class;
            }
            if (columnIndex == 4) {
                return Integer.class;
            }
            return String.class;
        }
    }
}
