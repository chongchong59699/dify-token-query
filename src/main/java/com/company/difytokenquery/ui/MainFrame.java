package com.company.difytokenquery.ui;

import com.company.difytokenquery.config.AppConfig;
import com.company.difytokenquery.config.ConfigService;
import com.company.difytokenquery.db.DatasourceConfig;
import com.company.difytokenquery.db.DifyQueryService;
import com.company.difytokenquery.model.AppQueryResult;
import com.company.difytokenquery.model.QueryStatus;
import com.company.difytokenquery.parser.TokenParser;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {
    private final ConfigService configService;
    private final DifyQueryService queryService;
    private final TokenParser tokenParser = new TokenParser();

    private AppConfig config;
    private final JComboBox<DatasourceConfig> datasourceComboBox = new JComboBox<DatasourceConfig>();
    private final JTextArea tokenTextArea = new JTextArea();
    private final ResultTableModel resultTableModel = new ResultTableModel();
    private final JTable resultTable = new JTable(resultTableModel);
    private final JLabel statusLabel = new JLabel("就绪");

    private final JButton parseButton = new JButton("解析Token");
    private final JButton queryButton = new JButton("查询");
    private final JButton clearButton = new JButton("清空");
    private final JButton copySelectedButton = new JButton("复制选中");
    private final JButton copyAllButton = new JButton("复制全部");
    private final JButton manageDatasourceButton = new JButton("管理Dify");
    private final JButton testConnectionButton = new JButton("测试连接");

    public MainFrame(ConfigService configService, DifyQueryService queryService) {
        this.configService = configService;
        this.queryService = queryService;
        initComponents();
        loadConfig();
    }

    private void initComponents() {
        setTitle("Dify Token 查询工具");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(calculateDefaultWindowSize());
        setMinimumSize(new Dimension(1100, 760));
        setSize(calculateDefaultWindowSize());
        setLocationRelativeTo(null);

        datasourceComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DatasourceConfig) {
                    DatasourceConfig datasource = (DatasourceConfig) value;
                    String text = datasource.getName();
                    if (!datasource.isEnabled()) {
                        text += "（禁用）";
                    }
                    setText(text);
                }
                return component;
            }
        });

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        topPanel.add(new JLabel("Dify配置"));
        datasourceComboBox.setPrototypeDisplayValue(prototypeDatasource());
        topPanel.add(datasourceComboBox);
        topPanel.add(manageDatasourceButton);
        topPanel.add(testConnectionButton);

        tokenTextArea.setLineWrap(true);
        tokenTextArea.setWrapStyleWord(true);
        tokenTextArea.setRows(9);
        JScrollPane tokenScrollPane = new JScrollPane(tokenTextArea);
        tokenScrollPane.setBorder(BorderFactory.createTitledBorder("粘贴包含 API Token 的文本"));
        tokenScrollPane.setMinimumSize(new Dimension(0, 190));
        tokenScrollPane.setPreferredSize(new Dimension(0, 240));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionPanel.add(parseButton);
        actionPanel.add(queryButton);
        actionPanel.add(clearButton);
        actionPanel.add(copySelectedButton);
        actionPanel.add(copyAllButton);

        JPanel inputPanel = new JPanel(new BorderLayout(4, 4));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        inputPanel.add(tokenScrollPane, BorderLayout.CENTER);
        inputPanel.add(actionPanel, BorderLayout.SOUTH);

        resultTable.setAutoCreateRowSorter(true);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        resultTable.setRowHeight(26);
        resultTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(270);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(300);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(250);
        resultTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        resultTable.getColumnModel().getColumn(4).setPreferredWidth(160);
        resultTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        resultTable.getColumnModel().getColumn(6).setPreferredWidth(80);
        resultTable.getColumnModel().getColumn(6).setCellRenderer(new ConfigurationLinkRenderer());
        resultTable.getColumnModel().getColumn(7).setPreferredWidth(100);
        resultTable.getColumnModel().getColumn(7).setCellRenderer(new ConfigurationLinkRenderer());
        resultTable.getColumnModel().getColumn(8).setPreferredWidth(420);
        JScrollPane tableScrollPane = new JScrollPane(resultTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("查询结果"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputPanel, tableScrollPane);
        splitPane.setResizeWeight(0.32);
        splitPane.setDividerLocation(300);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 8, 10));
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        bindActions();
    }

    private void bindActions() {
        manageDatasourceButton.addActionListener(e -> openDatasourceManager());
        testConnectionButton.addActionListener(e -> testCurrentConnection());
        parseButton.addActionListener(e -> parseTokens());
        queryButton.addActionListener(e -> queryTokens());
        clearButton.addActionListener(e -> clearAll());
        copySelectedButton.addActionListener(e -> copySelectedRows());
        copyAllButton.addActionListener(e -> copyAllRows());
        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleConfigurationActionAt(e);
            }
        });
        resultTable.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                resultTable.setCursor(isConfigurationActionAt(e) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });
    }

    private void loadConfig() {
        try {
            config = configService.load();
            refreshDatasourceComboBox();
            statusLabel.setText("配置文件：" + configService.getConfigFile());
        } catch (IOException e) {
            config = ConfigService.createDefaultConfig();
            refreshDatasourceComboBox();
            JOptionPane.showMessageDialog(this, e.getMessage(), "加载配置失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshDatasourceComboBox() {
        DefaultComboBoxModel<DatasourceConfig> model = new DefaultComboBoxModel<DatasourceConfig>();
        if (config != null && config.getDatasources() != null) {
            for (DatasourceConfig datasource : config.getDatasources()) {
                model.addElement(datasource);
            }
        }
        datasourceComboBox.setModel(model);
        selectDefaultDatasource();
    }

    private void selectDefaultDatasource() {
        if (config == null || config.getDefaultDatasourceId() == null) {
            return;
        }
        for (int i = 0; i < datasourceComboBox.getItemCount(); i++) {
            DatasourceConfig datasource = datasourceComboBox.getItemAt(i);
            if (config.getDefaultDatasourceId().equals(datasource.getId())) {
                datasourceComboBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void openDatasourceManager() {
        DatasourceManagerDialog dialog = new DatasourceManagerDialog(this, config, configService, queryService);
        dialog.setVisible(true);
        if (dialog.isChanged()) {
            loadConfig();
        }
    }

    private void testCurrentConnection() {
        DatasourceConfig datasource = getSelectedDatasource();
        if (datasource == null) {
            JOptionPane.showMessageDialog(this, "请先选择Dify配置", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setWorking(true, "正在测试连接：" + datasource.getName());
        new SwingWorker<Void, Void>() {
            private Exception failure;

            @Override
            protected Void doInBackground() {
                try {
                    queryService.testConnection(datasource);
                } catch (SQLException e) {
                    failure = e;
                }
                return null;
            }

            @Override
            protected void done() {
                setWorking(false, failure == null ? "连接成功：" + datasource.getName() : "连接失败：" + failure.getMessage());
                if (failure == null) {
                    JOptionPane.showMessageDialog(MainFrame.this, "连接成功", "测试连接", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(MainFrame.this, failure.getMessage(), "连接失败", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void parseTokens() {
        List<String> tokens = tokenParser.parse(tokenTextArea.getText());
        if (tokens.isEmpty()) {
            statusLabel.setText("未解析到符合规则的 token");
            JOptionPane.showMessageDialog(this, "未解析到符合 app-... 规则的 token", "解析Token", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        statusLabel.setText("已解析到 " + tokens.size() + " 个 token，点击“查询”获取应用信息");
        JOptionPane.showMessageDialog(this, "已解析到 " + tokens.size() + " 个 token", "解析Token", JOptionPane.INFORMATION_MESSAGE);
    }

    private void queryTokens() {
        List<String> tokens = tokenParser.parse(tokenTextArea.getText());
        if (tokens.isEmpty()) {
            statusLabel.setText("未解析到符合规则的 token");
            JOptionPane.showMessageDialog(this, "未解析到符合 app-... 规则的 token", "查询", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DatasourceConfig datasource = getSelectedDatasource();
        if (datasource == null) {
            JOptionPane.showMessageDialog(this, "请先选择Dify配置", "查询", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setWorking(true, "正在查询 " + tokens.size() + " 个 token...");
        new SwingWorker<List<AppQueryResult>, Void>() {
            @Override
            protected List<AppQueryResult> doInBackground() {
                return queryService.query(tokens, datasource);
            }

            @Override
            protected void done() {
                try {
                    List<AppQueryResult> results = get();
                    resultTableModel.setResults(results);
                    statusLabel.setText(buildSummary(results));
                } catch (Exception e) {
                    statusLabel.setText("查询失败：" + e.getMessage());
                    JOptionPane.showMessageDialog(MainFrame.this, e.getMessage(), "查询失败", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setWorking(false, statusLabel.getText());
                }
            }
        }.execute();
    }

    private String buildSummary(List<AppQueryResult> results) {
        int success = 0;
        int notFound = 0;
        int dbError = 0;
        int invalid = 0;
        for (AppQueryResult result : results) {
            if (result.getStatus() == QueryStatus.SUCCESS) {
                success++;
            } else if (result.getStatus() == QueryStatus.NOT_FOUND) {
                notFound++;
            } else if (result.getStatus() == QueryStatus.DB_ERROR) {
                dbError++;
            } else if (result.getStatus() == QueryStatus.INVALID_TOKEN) {
                invalid++;
            }
        }
        return "查询完成：成功 " + success + "，未找到 " + notFound + "，数据库错误 " + dbError + "，Token无效 " + invalid;
    }

    private void clearAll() {
        tokenTextArea.setText("");
        resultTableModel.setResults(new ArrayList<AppQueryResult>());
        statusLabel.setText("已清空");
    }

    private void copySelectedRows() {
        int[] selectedRows = resultTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "请先选择要复制的结果行", "复制选中", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        copyRows(selectedRows);
    }

    private void copyAllRows() {
        int rowCount = resultTable.getRowCount();
        if (rowCount == 0) {
            JOptionPane.showMessageDialog(this, "当前没有可复制的结果", "复制全部", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int[] rows = new int[rowCount];
        for (int i = 0; i < rowCount; i++) {
            rows[i] = i;
        }
        copyRows(rows);
    }

    private void copyRows(int[] viewRows) {
        StringBuilder builder = new StringBuilder();
        for (int column = 0; column < resultTableModel.getColumnCount(); column++) {
            if (column > 0) {
                builder.append('\t');
            }
            builder.append(resultTableModel.getColumnName(column));
        }
        builder.append(System.lineSeparator());

        for (int viewRow : viewRows) {
            int modelRow = resultTable.convertRowIndexToModel(viewRow);
            for (int column = 0; column < resultTableModel.getColumnCount(); column++) {
                if (column > 0) {
                    builder.append('\t');
                }
                builder.append(resultTableModel.getCopyValueAt(modelRow, column));
            }
            builder.append(System.lineSeparator());
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(builder.toString()), null);
        statusLabel.setText("已复制 " + viewRows.length + " 行");
    }

    private void handleConfigurationActionAt(MouseEvent event) {
        int viewColumn = resultTable.columnAtPoint(event.getPoint());
        if (viewColumn < 0) {
            return;
        }
        int modelColumn = resultTable.convertColumnIndexToModel(viewColumn);
        boolean openAction = modelColumn == ResultTableModel.CONFIGURATION_COLUMN;
        boolean copyAction = modelColumn == ResultTableModel.COPY_CONFIGURATION_COLUMN;
        if (!openAction && !copyAction) {
            return;
        }

        int viewRow = resultTable.rowAtPoint(event.getPoint());
        if (viewRow < 0) {
            return;
        }
        int modelRow = resultTable.convertRowIndexToModel(viewRow);
        String url = resultTableModel.getResultAt(modelRow).getConfigurationUrl();
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        if (openAction) {
            openUrl(url);
        } else {
            copyConfigurationUrl(url);
        }
    }

    private boolean isConfigurationActionAt(MouseEvent event) {
        int viewColumn = resultTable.columnAtPoint(event.getPoint());
        int viewRow = resultTable.rowAtPoint(event.getPoint());
        if (viewRow < 0 || viewColumn < 0) {
            return false;
        }
        int modelColumn = resultTable.convertColumnIndexToModel(viewColumn);
        if (modelColumn != ResultTableModel.CONFIGURATION_COLUMN && modelColumn != ResultTableModel.COPY_CONFIGURATION_COLUMN) {
            return false;
        }
        int modelRow = resultTable.convertRowIndexToModel(viewRow);
        String url = resultTableModel.getResultAt(modelRow).getConfigurationUrl();
        return url != null && !url.trim().isEmpty();
    }

    private void copyConfigurationUrl(String url) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
        statusLabel.setText("已复制编排链接：" + url);
    }

    private void openUrl(String url) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            JOptionPane.showMessageDialog(this, "当前环境不支持自动打开浏览器：" + url, "打开编排", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
            statusLabel.setText("已打开编排：" + url);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "打开编排失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private DatasourceConfig getSelectedDatasource() {
        Object item = datasourceComboBox.getSelectedItem();
        return item instanceof DatasourceConfig ? (DatasourceConfig) item : null;
    }

    private void setWorking(boolean working, String text) {
        parseButton.setEnabled(!working);
        queryButton.setEnabled(!working);
        clearButton.setEnabled(!working);
        copySelectedButton.setEnabled(!working);
        copyAllButton.setEnabled(!working);
        manageDatasourceButton.setEnabled(!working);
        testConnectionButton.setEnabled(!working);
        datasourceComboBox.setEnabled(!working);
        statusLabel.setText(text);
    }

    private DatasourceConfig prototypeDatasource() {
        DatasourceConfig datasource = new DatasourceConfig();
        datasource.setName("公司170dify pg库          ");
        return datasource;
    }

    private Dimension calculateDefaultWindowSize() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(1600, Math.max(1280, (int) (screenSize.width * 0.86)));
        int height = Math.min(950, Math.max(820, (int) (screenSize.height * 0.86)));
        return new Dimension(Math.min(width, screenSize.width - 80), Math.min(height, screenSize.height - 80));
    }

    private static class ConfigurationLinkRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = value == null ? "" : String.valueOf(value);
            label.setHorizontalAlignment(JLabel.CENTER);
            if ("编排".equals(text) || "复制链接".equals(text)) {
                label.setText("<html><a href=''>" + text + "</a></html>");
            } else {
                label.setText("");
            }
            return label;
        }
    }
}
