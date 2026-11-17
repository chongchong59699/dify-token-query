package com.company.difytokenquery.ui;

import com.company.difytokenquery.config.AppConfig;
import com.company.difytokenquery.config.ConfigService;
import com.company.difytokenquery.db.DatasourceConfig;
import com.company.difytokenquery.db.DifyAppModelRepository;
import com.company.difytokenquery.dify.AppBackupService;
import com.company.difytokenquery.dify.BatchModelReplaceService;
import com.company.difytokenquery.dify.DifyConsoleClient;
import com.company.difytokenquery.dify.DifyConsoleClient.ModelParameterSupport;
import com.company.difytokenquery.dify.ModelReplacementEngine;
import com.company.difytokenquery.dify.ModelReplacementEngine.ResolvedTarget;
import com.company.difytokenquery.model.BatchExecutionResult;
import com.company.difytokenquery.model.DifyAppSummary;
import com.company.difytokenquery.model.DifyAppSummary.ModelRef;
import com.company.difytokenquery.model.DifyModelOption;
import com.company.difytokenquery.model.ModelSlotChange;
import com.company.difytokenquery.model.ReplacementOptions;
import com.company.difytokenquery.model.ReplacementOptions.ModelMappingRule;
import com.company.difytokenquery.model.ReplacementOptions.NodeTargetOverride;
import com.company.difytokenquery.model.ReplacementOptions.ParamStrategy;
import com.company.difytokenquery.model.ReplacementOptions.ThinkingConfig;
import com.company.difytokenquery.model.ReplacementOptions.ThinkingMode;
import com.company.difytokenquery.model.ReplacementPlanItem;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Workbench window for loading Dify models, filtering apps, configuring per-node or multi-rule replacements, backing up YAML, and executing batch updates. */
public class BatchModelReplaceFrame extends JFrame {
    private static final String ALL_MODELS_FILTER = "全部模型";

    private final DatasourceConfig datasource;
    private final AppConfig appConfig;
    private final ConfigService configService;
    private final Set<String> preselectedAppIds;
    private final Runnable onConfigSavedCallback;

    private final DifyAppModelRepository appRepository = new DifyAppModelRepository();
    private final ModelReplacementEngine replacementEngine = new ModelReplacementEngine();
    private final DifyConsoleClient consoleClient;

    private final JLabel loginStatusLabel = new JLabel("Dify账号：未登录");
    private final JButton loginButton = new JButton("登录/切换账号");
    private final JButton refreshAllButton = new JButton("加载模型并刷新应用");
    private final JTextField backupDirField = new JTextField(24);
    private final JButton chooseBackupDirButton = new JButton("选择");
    private final JButton openBackupDirButton = new JButton("打开备份目录");

    private final JCheckBox replaceAllNodesCheckBox = new JCheckBox("强制替换应用内全部LLM节点", false);
    private final JComboBox<DifyModelOption> sourceModelCombo = new JComboBox<DifyModelOption>();
    private final JTextField targetModelFilterField = new JTextField(12);
    private final JComboBox<DifyModelOption> targetModelCombo = new JComboBox<DifyModelOption>();
    private final JComboBox<ParamStrategy> paramStrategyCombo = new JComboBox<ParamStrategy>(ParamStrategy.values());
    private final JComboBox<ThinkingMode> thinkingModeCombo = new JComboBox<ThinkingMode>(ThinkingMode.values());
    private final JComboBox<String> thinkingEffortCombo = new JComboBox<String>(ThinkingConfig.DEFAULT_REASONING_EFFORTS.toArray(new String[0]));
    private final JLabel thinkingHintLabel = new JLabel("");
    private final JCheckBox autoPublishCheckBox = new JCheckBox("工作流更新后自动发布", false);

    private final JButton addRuleButton = new JButton("添加到多规则");
    private final JButton removeRuleButton = new JButton("移除规则");
    private final JButton clearRulesButton = new JButton("清空规则");
    private final DefaultListModel<ModelMappingRule> ruleListModel = new DefaultListModel<ModelMappingRule>();
    private final JList<ModelMappingRule> ruleList = new JList<ModelMappingRule>(ruleListModel);

    private final JTextField appSearchField = new JTextField(16);
    private final JComboBox<String> appModeFilterCombo = new JComboBox<String>(
            new String[]{"全部模式", "completion", "chat", "agent-chat", "workflow", "advanced-chat"}
    );
    private final JComboBox<String> appModelFilterCombo = new JComboBox<String>();
    private final JButton selectVisibleButton = new JButton("全选可见");
    private final JButton unselectAllButton = new JButton("取消全选");
    private final JButton invertSelectionButton = new JButton("反选可见");
    private final JButton selectMatchedButton = new JButton("仅选命中规则");
    private final JButton selectPreselectedButton = new JButton("仅选Token查询带入");

    private final AppTableModel appTableModel = new AppTableModel();
    private final JTable appTable = new JTable(appTableModel);

    private final JLabel nodePanelTitleLabel = new JLabel("请点击左侧应用查看并配置节点模型");
    private final JButton setAllNodesTargetButton = new JButton("本应用全部节点设为当前目标");
    private final JButton clearAppOverridesButton = new JButton("恢复按全局规则");
    private final NodeConfigTableModel nodeConfigTableModel = new NodeConfigTableModel();
    private final JTable nodeConfigTable = new JTable(nodeConfigTableModel);
    private final JComboBox<DifyModelOption> nodeTargetEditorCombo = new JComboBox<DifyModelOption>();

    private final PreviewTableModel previewTableModel = new PreviewTableModel();
    private final JTable previewTable = new JTable(previewTableModel);
    private final ExecutionResultTableModel executionTableModel = new ExecutionResultTableModel();
    private final JTable executionTable = new JTable(executionTableModel);
    private final JTabbedPane bottomTabs = new JTabbedPane();

    private final JButton dryRunButton = new JButton("预演预览 (Dry-run)");
    private final JButton executeButton = new JButton("执行批量替换");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("就绪");

    private List<DifyAppSummary> allApps = new ArrayList<DifyAppSummary>();
    private List<DifyModelOption> consoleModels = new ArrayList<DifyModelOption>();
    private List<DifyModelOption> usedModels = new ArrayList<DifyModelOption>();
    private final Set<String> checkedAppIds = new LinkedHashSet<String>();
    private final Map<String, Map<String, NodeTargetOverride>> nodeOverrides = new LinkedHashMap<String, Map<String, NodeTargetOverride>>();
    private DifyAppSummary activeDetailApp;
    private Path lastBackupDir;

    public BatchModelReplaceFrame(Frame owner,
                                  DatasourceConfig datasource,
                                  AppConfig appConfig,
                                  ConfigService configService,
                                  Set<String> preselectedAppIds,
                                  Runnable onConfigSavedCallback) {
        super("批量替换应用模型 - " + (datasource == null ? "" : datasource.getName()));
        this.datasource = datasource == null ? new DatasourceConfig() : datasource.copy();
        this.datasource.normalize();
        this.appConfig = appConfig;
        this.configService = configService;
        this.preselectedAppIds = preselectedAppIds == null
                ? Collections.<String>emptySet()
                : new LinkedHashSet<String>(preselectedAppIds);
        this.onConfigSavedCallback = onConfigSavedCallback;
        this.consoleClient = new DifyConsoleClient(this.datasource.getDifyFrontendUrl());
        this.checkedAppIds.addAll(this.preselectedAppIds);

        initComponents();
        bindActions();
        initialLoad();
    }

    private void initComponents() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(new Dimension(Math.min(1620, screen.width - 50), Math.min(960, screen.height - 50)));
        setMinimumSize(new Dimension(1220, 780));
        setLocationRelativeTo(getOwner());

        backupDirField.setText(AppBackupService.defaultBackupRootDir().toAbsolutePath().toString());

        JPanel topContainer = new JPanel(new BorderLayout(4, 4));
        topContainer.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));

        JPanel connPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        String frontend = datasource.getDifyFrontendUrl().isEmpty() ? "(未配置前端地址)" : datasource.getDifyFrontendUrl();
        connPanel.add(new JLabel("配置：" + datasource.getName() + " [" + frontend + "]"));
        connPanel.add(loginStatusLabel);
        connPanel.add(loginButton);
        connPanel.add(refreshAllButton);
        connPanel.add(new JLabel("YML备份目录："));
        connPanel.add(backupDirField);
        connPanel.add(chooseBackupDirButton);
        connPanel.add(openBackupDirButton);

        JPanel rulePanel = new JPanel(new GridBagLayout());
        rulePanel.setBorder(BorderFactory.createTitledBorder("模型替换规则（支持单条规则、多规则映射或右侧逐节点单独配置）"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        rulePanel.add(new JLabel("原模型："), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.45;
        sourceModelCombo.setPrototypeDisplayValue(DifyModelOption.simple("langgenius/openai_api_compatible", "sample-model-name-width", "chat"));
        rulePanel.add(sourceModelCombo, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0;
        rulePanel.add(replaceAllNodesCheckBox, gbc);

        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0;
        rulePanel.add(new JLabel("参数策略："), gbc);

        gbc.gridx = 4; gbc.gridy = 0; gbc.weightx = 0.35;
        rulePanel.add(paramStrategyCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        rulePanel.add(new JLabel("目标模型："), gbc);

        JPanel targetPanel = new JPanel(new BorderLayout(6, 0));
        targetModelFilterField.putClientProperty("JTextField.placeholderText", "筛选目标模型...");
        targetModelCombo.setPrototypeDisplayValue(DifyModelOption.simple("langgenius/openai_api_compatible", "sample-model-name-width", "chat"));
        targetPanel.add(targetModelFilterField, BorderLayout.WEST);
        targetPanel.add(targetModelCombo, BorderLayout.CENTER);
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 0.65;
        rulePanel.add(targetPanel, gbc);

        JPanel ruleActionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        ruleActionPanel.add(addRuleButton);
        ruleActionPanel.add(removeRuleButton);
        ruleActionPanel.add(clearRulesButton);
        ruleActionPanel.add(autoPublishCheckBox);
        gbc.gridx = 3; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 0.35;
        rulePanel.add(ruleActionPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0;
        rulePanel.add(new JLabel("思考层级："), gbc);

        JPanel thinkingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        thinkingPanel.add(thinkingModeCombo);
        thinkingPanel.add(new JLabel("思考强度："));
        thinkingEffortCombo.setEnabled(false);
        thinkingEffortCombo.setPreferredSize(new Dimension(125, thinkingEffortCombo.getPreferredSize().height));
        thinkingPanel.add(thinkingEffortCombo);
        thinkingHintLabel.setForeground(new Color(100, 110, 125));
        thinkingPanel.add(thinkingHintLabel);
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 4; gbc.weightx = 1;
        rulePanel.add(thinkingPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0;
        rulePanel.add(new JLabel("多规则队列："), gbc);

        ruleList.setVisibleRowCount(2);
        ruleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane ruleScroll = new JScrollPane(ruleList);
        ruleScroll.setPreferredSize(new Dimension(0, 44));
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 4; gbc.weightx = 1;
        rulePanel.add(ruleScroll, gbc);

        topContainer.add(connPanel, BorderLayout.NORTH);
        topContainer.add(rulePanel, BorderLayout.CENTER);

        // Left center: App filters + App table
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 4));
        appSearchField.putClientProperty("JTextField.placeholderText", "搜索应用名 / app_id / Token...");
        filterPanel.add(new JLabel("筛选："));
        filterPanel.add(appSearchField);
        filterPanel.add(appModeFilterCombo);
        appModelFilterCombo.addItem(ALL_MODELS_FILTER);
        appModelFilterCombo.setPreferredSize(new Dimension(210, appModelFilterCombo.getPreferredSize().height));
        filterPanel.add(appModelFilterCombo);
        filterPanel.add(selectVisibleButton);
        filterPanel.add(unselectAllButton);
        filterPanel.add(invertSelectionButton);
        filterPanel.add(selectMatchedButton);
        selectPreselectedButton.setEnabled(!preselectedAppIds.isEmpty());
        filterPanel.add(selectPreselectedButton);

        configureAppTable();
        JScrollPane appScroll = new JScrollPane(appTable);
        appScroll.setBorder(BorderFactory.createTitledBorder("应用列表（勾选要批量替换的应用，单击行在右侧配置多节点）"));

        JPanel leftAppsPanel = new JPanel(new BorderLayout(4, 4));
        leftAppsPanel.add(filterPanel, BorderLayout.NORTH);
        leftAppsPanel.add(appScroll, BorderLayout.CENTER);

        // Right center: Per-app multi-node configuration
        configureNodeConfigTable();
        JPanel nodeTopPanel = new JPanel(new BorderLayout(4, 4));
        nodeTopPanel.add(nodePanelTitleLabel, BorderLayout.NORTH);
        JPanel nodeBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        nodeBtnPanel.add(setAllNodesTargetButton);
        nodeBtnPanel.add(clearAppOverridesButton);
        nodeTopPanel.add(nodeBtnPanel, BorderLayout.SOUTH);

        JPanel rightNodePanel = new JPanel(new BorderLayout(4, 4));
        rightNodePanel.setBorder(BorderFactory.createTitledBorder("当前选中应用的节点模型配置"));
        rightNodePanel.add(nodeTopPanel, BorderLayout.NORTH);
        rightNodePanel.add(new JScrollPane(nodeConfigTable), BorderLayout.CENTER);

        JSplitPane middleSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftAppsPanel, rightNodePanel);
        middleSplit.setResizeWeight(0.58);
        middleSplit.setDividerLocation(910);
        middleSplit.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));

        // Bottom: Action bar + Dry-run preview & Execution results tabs
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionBar.add(dryRunButton);
        actionBar.add(executeButton);
        progressBar.setPreferredSize(new Dimension(220, 18));
        progressBar.setStringPainted(true);
        actionBar.add(progressBar);
        actionBar.add(statusLabel);

        configurePreviewTable();
        configureExecutionTable();
        bottomTabs.addTab("变更预览 (Dry-run)", new JScrollPane(previewTable));
        bottomTabs.addTab("执行结果 (含YML备份路径)", new JScrollPane(executionTable));

        JPanel bottomContainer = new JPanel(new BorderLayout(4, 4));
        bottomContainer.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        bottomContainer.add(actionBar, BorderLayout.NORTH);
        bottomContainer.add(bottomTabs, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, middleSplit, bottomContainer);
        mainSplit.setResizeWeight(0.58);
        mainSplit.setDividerLocation(440);
        mainSplit.setBorder(BorderFactory.createEmptyBorder());

        setLayout(new BorderLayout());
        add(topContainer, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
    }

    private void configureAppTable() {
        appTable.setAutoCreateRowSorter(true);
        appTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        appTable.setRowHeight(26);
        appTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        appTable.getColumnModel().getColumn(0).setPreferredWidth(48);
        appTable.getColumnModel().getColumn(1).setPreferredWidth(210);
        appTable.getColumnModel().getColumn(2).setPreferredWidth(98);
        appTable.getColumnModel().getColumn(3).setPreferredWidth(84);
        appTable.getColumnModel().getColumn(4).setPreferredWidth(260);
        appTable.getColumnModel().getColumn(5).setPreferredWidth(62);
        appTable.getColumnModel().getColumn(6).setPreferredWidth(62);
        appTable.getColumnModel().getColumn(7).setPreferredWidth(58);
        appTable.getColumnModel().getColumn(7).setCellRenderer(new LinkRenderer());
        appTable.getColumnModel().getColumn(8).setPreferredWidth(250);
        appTable.getColumnModel().getColumn(9).setPreferredWidth(220);

        appTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    onAppRowSelected();
                }
            }
        });

        appTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = appTable.columnAtPoint(e.getPoint());
                int viewRow = appTable.rowAtPoint(e.getPoint());
                if (viewCol < 0 || viewRow < 0) {
                    return;
                }
                int modelCol = appTable.convertColumnIndexToModel(viewCol);
                if (modelCol == AppTableModel.LINK_COL) {
                    int modelRow = appTable.convertRowIndexToModel(viewRow);
                    DifyAppSummary app = appTableModel.getRow(modelRow);
                    if (!app.getConfigurationUrl().isEmpty()) {
                        openUrl(app.getConfigurationUrl());
                    }
                }
            }
        });
        appTable.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int viewCol = appTable.columnAtPoint(e.getPoint());
                int viewRow = appTable.rowAtPoint(e.getPoint());
                boolean isLink = false;
                if (viewCol >= 0 && viewRow >= 0 && appTable.convertColumnIndexToModel(viewCol) == AppTableModel.LINK_COL) {
                    DifyAppSummary app = appTableModel.getRow(appTable.convertRowIndexToModel(viewRow));
                    isLink = !app.getConfigurationUrl().isEmpty();
                }
                appTable.setCursor(Cursor.getPredefinedCursor(isLink ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
    }

    private void configureNodeConfigTable() {
        nodeConfigTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        nodeConfigTable.setRowHeight(26);
        nodeConfigTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        nodeConfigTable.getColumnModel().getColumn(0).setPreferredWidth(48);
        nodeConfigTable.getColumnModel().getColumn(1).setPreferredWidth(145);
        nodeConfigTable.getColumnModel().getColumn(2).setPreferredWidth(215);
        nodeConfigTable.getColumnModel().getColumn(3).setPreferredWidth(240);
        nodeConfigTable.getColumnModel().getColumn(4).setPreferredWidth(135);
        nodeConfigTable.getColumnModel().getColumn(5).setPreferredWidth(155);

        nodeConfigTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(nodeTargetEditorCombo));
        JComboBox<ParamStrategyChoice> strategyEditor = new JComboBox<ParamStrategyChoice>(ParamStrategyChoice.values());
        nodeConfigTable.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(strategyEditor));
        JComboBox<ThinkingChoice> thinkingEditor = new JComboBox<ThinkingChoice>(ThinkingChoice.values());
        nodeConfigTable.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(thinkingEditor));
    }

    private void configurePreviewTable() {
        previewTable.setAutoCreateRowSorter(true);
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        previewTable.setRowHeight(24);
        previewTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        previewTable.getColumnModel().getColumn(1).setPreferredWidth(210);
        previewTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        previewTable.getColumnModel().getColumn(3).setPreferredWidth(190);
        previewTable.getColumnModel().getColumn(4).setPreferredWidth(210);
        previewTable.getColumnModel().getColumn(5).setPreferredWidth(190);
        previewTable.getColumnModel().getColumn(6).setPreferredWidth(210);
        previewTable.getColumnModel().getColumn(7).setPreferredWidth(190);
        previewTable.getColumnModel().getColumn(8).setPreferredWidth(220);
        previewTable.getColumnModel().getColumn(9).setPreferredWidth(240);
    }

    private void configureExecutionTable() {
        executionTable.setAutoCreateRowSorter(true);
        executionTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        executionTable.setRowHeight(24);
        executionTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        executionTable.getColumnModel().getColumn(1).setPreferredWidth(105);
        executionTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        executionTable.getColumnModel().getColumn(3).setPreferredWidth(70);
        executionTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        executionTable.getColumnModel().getColumn(5).setPreferredWidth(420);
        executionTable.getColumnModel().getColumn(6).setPreferredWidth(320);

        executionTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = executionTable.rowAtPoint(e.getPoint());
                    if (viewRow >= 0) {
                        BatchExecutionResult row = executionTableModel.getRow(executionTable.convertRowIndexToModel(viewRow));
                        if (!row.getBackupYmlPath().isEmpty()) {
                            openDirectory(Paths.get(row.getBackupYmlPath()).getParent());
                        }
                    }
                }
            }
        });
    }

    private void bindActions() {
        loginButton.addActionListener(e -> promptLoginAndLoadModels());
        refreshAllButton.addActionListener(e -> reloadModelsAndApps(true));
        chooseBackupDirButton.addActionListener(e -> chooseBackupDirectory());
        openBackupDirButton.addActionListener(e -> openCurrentBackupDirectory());

        replaceAllNodesCheckBox.addActionListener(e -> {
            boolean forceAll = replaceAllNodesCheckBox.isSelected();
            sourceModelCombo.setEnabled(!forceAll);
            addRuleButton.setEnabled(!forceAll);
            refreshRulesAndNodeViews();
        });
        sourceModelCombo.addActionListener(e -> refreshRulesAndNodeViews());
        targetModelCombo.addActionListener(e -> {
            refreshRulesAndNodeViews();
            inspectTargetModelThinkingSupport();
        });
        paramStrategyCombo.addActionListener(e -> refreshRulesAndNodeViews());
        thinkingModeCombo.addActionListener(e -> {
            ThinkingMode mode = (ThinkingMode) thinkingModeCombo.getSelectedItem();
            thinkingEffortCombo.setEnabled(mode == ThinkingMode.ENABLE_WITH_EFFORT);
            refreshRulesAndNodeViews();
        });
        thinkingEffortCombo.addActionListener(e -> refreshRulesAndNodeViews());

        addRuleButton.addActionListener(e -> addMappingRule());
        removeRuleButton.addActionListener(e -> removeSelectedMappingRule());
        clearRulesButton.addActionListener(e -> {
            ruleListModel.clear();
            refreshRulesAndNodeViews();
        });

        setAllNodesTargetButton.addActionListener(e -> applyCurrentTargetToAllNodesOfSelectedApp());
        clearAppOverridesButton.addActionListener(e -> clearOverridesOfSelectedApp());

        targetModelFilterField.getDocument().addDocumentListener(simpleDocListener(this::refreshTargetModelCombo));
        appSearchField.getDocument().addDocumentListener(simpleDocListener(this::applyAppFilters));
        appModeFilterCombo.addActionListener(e -> applyAppFilters());
        appModelFilterCombo.addActionListener(e -> applyAppFilters());

        selectVisibleButton.addActionListener(e -> setVisibleChecked(true));
        unselectAllButton.addActionListener(e -> {
            checkedAppIds.clear();
            appTableModel.fireTableDataChanged();
            updateSelectionStatus();
        });
        invertSelectionButton.addActionListener(e -> invertVisibleChecked());
        selectMatchedButton.addActionListener(e -> selectAppsMatchingCurrentRule());
        selectPreselectedButton.addActionListener(e -> selectPreselectedApps());

        dryRunButton.addActionListener(e -> runDryRunPreview());
        executeButton.addActionListener(e -> executeBatchReplace());
    }
    private void initialLoad() {
        boolean hasCreds = !datasource.getDifyFrontendUrl().isEmpty()
                && !datasource.getDifyUsername().isEmpty()
                && !datasource.getDifyPassword().isEmpty();
        reloadModelsAndApps(hasCreds);
    }

    private void promptLoginAndLoadModels() {
        if (datasource.getDifyFrontendUrl().isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前Dify配置未设置前端地址，请先在“管理Dify”中补充。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DifyLoginDialog dialog = new DifyLoginDialog(
                this,
                datasource.getDifyFrontendUrl(),
                datasource.getDifyUsername(),
                datasource.getDifyPassword()
        );
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }
        String email = dialog.getEmail();
        String password = dialog.getPassword();
        boolean saveToConfig = dialog.isSaveCredentials();

        setWorking(true, "正在登录 Dify 控制台...");
        new SwingWorker<List<DifyModelOption>, Void>() {
            private Exception failure;

            @Override
            protected List<DifyModelOption> doInBackground() {
                try {
                    consoleClient.login(email, password);
                    if (saveToConfig) {
                        saveDatasourceCredentials(email, password);
                    }
                    return consoleClient.listLlmModels();
                } catch (Exception e) {
                    failure = e;
                    return Collections.emptyList();
                }
            }

            @Override
            protected void done() {
                setWorking(false, statusLabel.getText());
                if (failure != null) {
                    loginStatusLabel.setText("Dify账号：登录失败");
                    JOptionPane.showMessageDialog(BatchModelReplaceFrame.this, failure.getMessage(), "登录失败", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                try {
                    consoleModels = get();
                    loginStatusLabel.setText("Dify账号：已登录 (" + consoleClient.getLoggedInEmail() + ")");
                    refreshModelSelectors();
                    statusLabel.setText("已登录并加载 " + consoleModels.size() + " 个可用 LLM 模型，应用数 " + allApps.size());
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(BatchModelReplaceFrame.this, e.getMessage(), "加载模型失败", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void reloadModelsAndApps(boolean attemptConsoleLogin) {
        if (attemptConsoleLogin && !consoleClient.isLoggedIn()
                && (datasource.getDifyUsername().isEmpty() || datasource.getDifyPassword().isEmpty())) {
            promptLoginAndLoadModels();
            attemptConsoleLogin = false;
        }
        final boolean shouldLoadConsoleModels = attemptConsoleLogin || consoleClient.isLoggedIn();
        setWorking(true, "正在从数据库与 Dify 加载应用和模型列表...");

        new SwingWorker<LoadSnapshot, Void>() {
            private Exception dbFailure;
            private String consoleWarning = "";

            @Override
            protected LoadSnapshot doInBackground() {
                List<DifyAppSummary> apps = Collections.emptyList();
                List<DifyModelOption> liveModels = consoleModels;
                try {
                    apps = appRepository.loadAllApps(datasource);
                } catch (Exception e) {
                    dbFailure = e;
                }

                if (shouldLoadConsoleModels) {
                    try {
                        if (!consoleClient.isLoggedIn()
                                && !datasource.getDifyUsername().isEmpty()
                                && !datasource.getDifyPassword().isEmpty()) {
                            consoleClient.login(datasource.getDifyUsername(), datasource.getDifyPassword());
                        }
                        if (consoleClient.isLoggedIn()) {
                            liveModels = consoleClient.listLlmModels();
                        }
                    } catch (Exception e) {
                        consoleWarning = e.getMessage();
                    }
                }
                return new LoadSnapshot(apps, liveModels);
            }

            @Override
            protected void done() {
                setWorking(false, statusLabel.getText());
                if (dbFailure != null) {
                    statusLabel.setText("加载应用失败：" + dbFailure.getMessage());
                    JOptionPane.showMessageDialog(BatchModelReplaceFrame.this, dbFailure.getMessage(), "加载应用失败", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                try {
                    LoadSnapshot snapshot = get();
                    allApps = snapshot.apps;
                    usedModels = appRepository.collectUsedModels(allApps);
                    consoleModels = snapshot.models == null ? Collections.<DifyModelOption>emptyList() : snapshot.models;
                    if (consoleClient.isLoggedIn()) {
                        loginStatusLabel.setText("Dify账号：已登录 (" + consoleClient.getLoggedInEmail() + ")");
                    }
                    refreshModelSelectors();
                    applyAppFilters();
                    String msg = "已加载 " + allApps.size() + " 个应用，Console 可用模型 " + consoleModels.size() + " 个";
                    if (!preselectedAppIds.isEmpty()) {
                        msg += "（已带入勾选 " + countCheckedInAllApps() + " 个）";
                    }
                    if (!consoleWarning.isEmpty()) {
                        msg += "；Dify登录/拉取模型提示：" + consoleWarning;
                    }
                    statusLabel.setText(msg);
                } catch (Exception e) {
                    statusLabel.setText("加载失败：" + e.getMessage());
                }
            }
        }.execute();
    }

    private void refreshModelSelectors() {
        DifyModelOption prevSource = (DifyModelOption) sourceModelCombo.getSelectedItem();
        Map<String, DifyModelOption> consoleLookup = new LinkedHashMap<String, DifyModelOption>();
        for (DifyModelOption m : consoleModels) {
            consoleLookup.put(m.getKey(), m);
        }
        List<DifyModelOption> usedOnlyList = new ArrayList<DifyModelOption>();
        for (DifyModelOption used : usedModels) {
            DifyModelOption enriched = consoleLookup.get(used.getKey());
            usedOnlyList.add(enriched != null ? enriched : used);
        }
        Collections.sort(usedOnlyList, new Comparator<DifyModelOption>() {
            @Override
            public int compare(DifyModelOption a, DifyModelOption b) {
                int p = a.getProvider().compareToIgnoreCase(b.getProvider());
                if (p != 0) return p;
                return a.getModelName().compareToIgnoreCase(b.getModelName());
            }
        });
        DefaultComboBoxModel<DifyModelOption> sourceModel = new DefaultComboBoxModel<DifyModelOption>();
        for (DifyModelOption option : usedOnlyList) {
            sourceModel.addElement(option);
        }
        sourceModelCombo.setModel(sourceModel);
        if (prevSource != null) {
            selectOptionByKey(sourceModelCombo, prevSource.getKey());
        }

        DefaultComboBoxModel<DifyModelOption> editorModel = new DefaultComboBoxModel<DifyModelOption>();
        for (DifyModelOption option : consoleModels) {
            editorModel.addElement(option);
        }
        nodeTargetEditorCombo.setModel(editorModel);

        Object prevFilter = appModelFilterCombo.getSelectedItem();
        DefaultComboBoxModel<String> filterModel = new DefaultComboBoxModel<String>();
        filterModel.addElement(ALL_MODELS_FILTER);
        for (DifyModelOption used : usedModels) {
            filterModel.addElement(used.getProvider() + " | " + used.getModelName());
        }
        appModelFilterCombo.setModel(filterModel);
        if (prevFilter != null) {
            appModelFilterCombo.setSelectedItem(prevFilter);
        }

        refreshTargetModelCombo();
        refreshRulesAndNodeViews();
    }

    private void refreshTargetModelCombo() {
        DifyModelOption prevTarget = (DifyModelOption) targetModelCombo.getSelectedItem();
        String keyword = targetModelFilterField.getText().trim().toLowerCase();
        DefaultComboBoxModel<DifyModelOption> model = new DefaultComboBoxModel<DifyModelOption>();
        for (DifyModelOption option : consoleModels) {
            if (keyword.isEmpty() || option.getDisplayText().toLowerCase().contains(keyword)
                    || option.getProvider().toLowerCase().contains(keyword)
                    || option.getModelName().toLowerCase().contains(keyword)) {
                model.addElement(option);
            }
        }
        targetModelCombo.setModel(model);
        if (prevTarget != null) {
            selectOptionByKey(targetModelCombo, prevTarget.getKey());
        }
        refreshRulesAndNodeViews();
    }

    private void selectOptionByKey(JComboBox<DifyModelOption> combo, String key) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            DifyModelOption opt = combo.getItemAt(i);
            if (opt != null && opt.getKey().equals(key)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void addMappingRule() {
        DifyModelOption source = (DifyModelOption) sourceModelCombo.getSelectedItem();
        DifyModelOption target = (DifyModelOption) targetModelCombo.getSelectedItem();
        if (source == null) {
            JOptionPane.showMessageDialog(this, "请先选择原模型", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (target == null) {
            JOptionPane.showMessageDialog(this, "请先登录并选择目标模型", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (target.isSameModel(source.getProvider(), source.getModelName())) {
            JOptionPane.showMessageDialog(this, "原模型与目标模型相同，无需添加", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ModelMappingRule newRule = new ModelMappingRule(source.getProvider(), source.getModelName(), target);
        for (int i = 0; i < ruleListModel.size(); i++) {
            if (ruleListModel.get(i).getRuleKey().equals(newRule.getRuleKey())) {
                ruleListModel.set(i, newRule);
                refreshRulesAndNodeViews();
                return;
            }
        }
        ruleListModel.addElement(newRule);
        refreshRulesAndNodeViews();
    }

    private void removeSelectedMappingRule() {
        int idx = ruleList.getSelectedIndex();
        if (idx >= 0) {
            ruleListModel.remove(idx);
            refreshRulesAndNodeViews();
        }
    }

    private List<ModelMappingRule> getMappingRules() {
        List<ModelMappingRule> rules = new ArrayList<ModelMappingRule>();
        for (int i = 0; i < ruleListModel.size(); i++) {
            rules.add(ruleListModel.get(i));
        }
        return rules;
    }

    private void applyAppFilters() {
        String keyword = appSearchField.getText().trim().toLowerCase();
        String modeFilter = (String) appModeFilterCombo.getSelectedItem();
        String modelFilter = (String) appModelFilterCombo.getSelectedItem();

        String filterProvider = "";
        String filterModelName = "";
        if (modelFilter != null && !ALL_MODELS_FILTER.equals(modelFilter) && modelFilter.contains(" | ")) {
            int sep = modelFilter.indexOf(" | ");
            filterProvider = modelFilter.substring(0, sep);
            filterModelName = modelFilter.substring(sep + 3);
        }

        List<DifyAppSummary> filtered = new ArrayList<DifyAppSummary>();
        for (DifyAppSummary app : allApps) {
            if (modeFilter != null && !"全部模式".equals(modeFilter) && !modeFilter.equalsIgnoreCase(app.getMode())) {
                continue;
            }
            if (!filterProvider.isEmpty() && !app.usesModel(filterProvider, filterModelName)) {
                continue;
            }
            if (!keyword.isEmpty()) {
                boolean matches = app.getAppName().toLowerCase().contains(keyword)
                        || app.getAppId().toLowerCase().contains(keyword)
                        || app.getTokensSummary().toLowerCase().contains(keyword)
                        || app.getModelsSummary().toLowerCase().contains(keyword);
                if (!matches) {
                    continue;
                }
            }
            filtered.add(app);
        }
        appTableModel.setRows(filtered, buildCurrentOptions());
        if (activeDetailApp != null) {
            for (int i = 0; i < filtered.size(); i++) {
                if (filtered.get(i).getAppId().equals(activeDetailApp.getAppId())) {
                    int viewRow = appTable.convertRowIndexToView(i);
                    if (viewRow >= 0) {
                        appTable.setRowSelectionInterval(viewRow, viewRow);
                    }
                    break;
                }
            }
        }
        onAppRowSelected();
        updateSelectionStatus();
    }

    private void refreshRulesAndNodeViews() {
        ReplacementOptions options = buildCurrentOptions();
        appTableModel.refreshHitCounts(options);
        nodeConfigTableModel.refresh(activeDetailApp, options);
    }

    private void onAppRowSelected() {
        int viewRow = appTable.getSelectedRow();
        if (viewRow < 0) {
            activeDetailApp = null;
            nodePanelTitleLabel.setText("请点击左侧应用查看并配置节点模型");
            nodeConfigTableModel.refresh(null, buildCurrentOptions());
            return;
        }
        int modelRow = appTable.convertRowIndexToModel(viewRow);
        activeDetailApp = appTableModel.getRow(modelRow);
        String overrideHint = nodeOverrides.containsKey(activeDetailApp.getAppId()) ? " [含节点单独配置]" : "";
        nodePanelTitleLabel.setText("当前应用：" + activeDetailApp.getAppName()
                + "（共 " + activeDetailApp.getModelRefs().size() + " 个模型节点）" + overrideHint);
        nodeConfigTableModel.refresh(activeDetailApp, buildCurrentOptions());
    }

    private void applyCurrentTargetToAllNodesOfSelectedApp() {
        if (activeDetailApp == null) {
            JOptionPane.showMessageDialog(this, "请先点击左侧列表选择一个应用", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DifyModelOption target = (DifyModelOption) targetModelCombo.getSelectedItem();
        if (target == null) {
            JOptionPane.showMessageDialog(this, "请先登录并选择目标模型", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (activeDetailApp.getModelRefs().isEmpty()) {
            JOptionPane.showMessageDialog(this, "该应用没有可配置的 LLM 模型节点", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Map<String, NodeTargetOverride> appMap = getOrCreateAppOverrideMap(activeDetailApp.getAppId());
        for (ModelRef ref : activeDetailApp.getModelRefs()) {
            appMap.put(ref.getSlotKey(), new NodeTargetOverride(true, target, null));
        }
        checkedAppIds.add(activeDetailApp.getAppId());
        onAppRowSelected();
        refreshRulesAndNodeViews();
        updateSelectionStatus();
    }

    private void clearOverridesOfSelectedApp() {
        if (activeDetailApp == null) {
            return;
        }
        nodeOverrides.remove(activeDetailApp.getAppId());
        onAppRowSelected();
        refreshRulesAndNodeViews();
    }

    private Map<String, NodeTargetOverride> getOrCreateAppOverrideMap(String appId) {
        Map<String, NodeTargetOverride> map = nodeOverrides.get(appId);
        if (map == null) {
            map = new LinkedHashMap<String, NodeTargetOverride>();
            nodeOverrides.put(appId, map);
        }
        return map;
    }

    private ReplacementOptions buildCurrentOptions() {
        DifyModelOption source = (DifyModelOption) sourceModelCombo.getSelectedItem();
        DifyModelOption target = (DifyModelOption) targetModelCombo.getSelectedItem();
        ParamStrategy strategy = (ParamStrategy) paramStrategyCombo.getSelectedItem();
        ThinkingMode thinkingMode = (ThinkingMode) thinkingModeCombo.getSelectedItem();
        String effort = (String) thinkingEffortCombo.getSelectedItem();
        ThinkingConfig thinkingConfig = new ThinkingConfig(thinkingMode, effort);
        return new ReplacementOptions(
                source == null ? "" : source.getProvider(),
                source == null ? "" : source.getModelName(),
                replaceAllNodesCheckBox.isSelected(),
                target,
                strategy,
                thinkingConfig,
                autoPublishCheckBox.isSelected(),
                getMappingRules(),
                nodeOverrides
        );
    }

    private void inspectTargetModelThinkingSupport() {
        DifyModelOption target = (DifyModelOption) targetModelCombo.getSelectedItem();
        if (target == null || !consoleClient.isLoggedIn()) {
            thinkingHintLabel.setText("");
            return;
        }
        final String provider = target.getProvider();
        final String modelName = target.getModelName();
        thinkingHintLabel.setText("正在检查思考参数...");
        new SwingWorker<ModelParameterSupport, Void>() {
            @Override
            protected ModelParameterSupport doInBackground() {
                try {
                    return consoleClient.getModelParameterSupport(provider, modelName);
                } catch (Exception e) {
                    return ModelParameterSupport.unknown();
                }
            }

            @Override
            protected void done() {
                DifyModelOption currentTarget = (DifyModelOption) targetModelCombo.getSelectedItem();
                if (currentTarget == null || !currentTarget.isSameModel(provider, modelName)) {
                    return;
                }
                try {
                    ModelParameterSupport support = get();
                    if (!support.isLoaded()) {
                        thinkingHintLabel.setText("");
                        return;
                    }
                    if (support.isSupportsReasoningEffort() && !support.getReasoningEffortOptions().isEmpty()) {
                        String prev = (String) thinkingEffortCombo.getSelectedItem();
                        DefaultComboBoxModel<String> m = new DefaultComboBoxModel<String>();
                        for (String opt : support.getReasoningEffortOptions()) {
                            m.addElement(opt);
                        }
                        thinkingEffortCombo.setModel(m);
                        if (prev != null && support.getReasoningEffortOptions().contains(prev)) {
                            thinkingEffortCombo.setSelectedItem(prev);
                        }
                        thinkingHintLabel.setText("支持 enable_thinking + reasoning_effort");
                    } else if (support.isSupportsEnableThinking()) {
                        thinkingHintLabel.setText("支持 enable_thinking 开关");
                    } else {
                        thinkingHintLabel.setText("模型未声明思考规则（仍可按需写入/关闭）");
                    }
                } catch (Exception ignored) {
                    thinkingHintLabel.setText("");
                }
            }
        }.execute();
    }
    private void setVisibleChecked(boolean checked) {
        for (DifyAppSummary app : appTableModel.getRows()) {
            if (checked) {
                checkedAppIds.add(app.getAppId());
            } else {
                checkedAppIds.remove(app.getAppId());
            }
        }
        appTableModel.fireTableDataChanged();
        updateSelectionStatus();
    }

    private void invertVisibleChecked() {
        for (DifyAppSummary app : appTableModel.getRows()) {
            if (checkedAppIds.contains(app.getAppId())) {
                checkedAppIds.remove(app.getAppId());
            } else {
                checkedAppIds.add(app.getAppId());
            }
        }
        appTableModel.fireTableDataChanged();
        updateSelectionStatus();
    }

    private void selectAppsMatchingCurrentRule() {
        ReplacementOptions options = buildCurrentOptions();
        boolean hasGlobalConfig = options.getTargetModel() != null || !options.getMappingRules().isEmpty();
        if (!hasGlobalConfig && nodeOverrides.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先选择规则、添加多规则或配置节点目标模型", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        checkedAppIds.clear();
        for (DifyAppSummary app : appTableModel.getRows()) {
            if (replacementEngine.planOneApp(app, options).isExecutable()) {
                checkedAppIds.add(app.getAppId());
            }
        }
        appTableModel.fireTableDataChanged();
        updateSelectionStatus();
    }

    private void selectPreselectedApps() {
        checkedAppIds.clear();
        checkedAppIds.addAll(preselectedAppIds);
        appTableModel.fireTableDataChanged();
        updateSelectionStatus();
    }

    private List<DifyAppSummary> getCheckedApps() {
        List<DifyAppSummary> selected = new ArrayList<DifyAppSummary>();
        for (DifyAppSummary app : allApps) {
            if (checkedAppIds.contains(app.getAppId())) {
                selected.add(app);
            }
        }
        return selected;
    }

    private int countCheckedInAllApps() {
        int count = 0;
        for (DifyAppSummary app : allApps) {
            if (checkedAppIds.contains(app.getAppId())) {
                count++;
            }
        }
        return count;
    }

    private void updateSelectionStatus() {
        statusLabel.setText("可见应用 " + appTableModel.getRowCount() + " / 总计 " + allApps.size()
                + "，已勾选 " + countCheckedInAllApps() + " 个");
    }

    private boolean validateReadyForPlan(ReplacementOptions options, List<DifyAppSummary> selectedApps) {
        if (selectedApps.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请至少勾选一个应用", "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        boolean anyOverride = false;
        for (DifyAppSummary app : selectedApps) {
            if (options.hasOverridesForApp(app.getAppId())) {
                anyOverride = true;
                break;
            }
        }
        if (anyOverride || !options.getMappingRules().isEmpty()) {
            return true;
        }
        if (options.getTargetModel() == null) {
            JOptionPane.showMessageDialog(this, "请先登录并选择目标模型，或在右侧为节点单独指定目标模型", "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (!options.isReplaceAllNodes() && (options.getSourceProvider().isEmpty() || options.getSourceModelName().isEmpty())) {
            JOptionPane.showMessageDialog(this, "请选择原模型、勾选“强制替换全部LLM节点”，或在右侧为工作流节点单独配置", "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void runDryRunPreview() {
        ReplacementOptions options = buildCurrentOptions();
        List<DifyAppSummary> selectedApps = getCheckedApps();
        if (!validateReadyForPlan(options, selectedApps)) {
            return;
        }
        List<ReplacementPlanItem> plan = replacementEngine.buildPlan(selectedApps, options);
        previewTableModel.setPlan(plan);
        bottomTabs.setSelectedIndex(0);

        int executableApps = 0;
        int totalSlots = 0;
        for (ReplacementPlanItem item : plan) {
            if (item.isExecutable()) {
                executableApps++;
                totalSlots += item.getChanges().size();
            }
        }
        statusLabel.setText("预演完成：勾选 " + selectedApps.size() + " 个应用，可执行 " + executableApps
                + " 个，预计替换 " + totalSlots + " 处模型槽位");
    }

    private void executeBatchReplace() {
        if (!consoleClient.isLoggedIn()) {
            promptLoginAndLoadModels();
            if (!consoleClient.isLoggedIn()) {
                return;
            }
        }
        ReplacementOptions options = buildCurrentOptions();
        List<DifyAppSummary> selectedApps = getCheckedApps();
        if (!validateReadyForPlan(options, selectedApps)) {
            return;
        }

        List<ReplacementPlanItem> plan = replacementEngine.buildPlan(selectedApps, options);
        previewTableModel.setPlan(plan);

        int executableApps = 0;
        int totalSlots = 0;
        int skippedApps = 0;
        for (ReplacementPlanItem item : plan) {
            if (item.isExecutable()) {
                executableApps++;
                totalSlots += item.getChanges().size();
            } else {
                skippedApps++;
            }
        }
        if (executableApps == 0) {
            bottomTabs.setSelectedIndex(0);
            JOptionPane.showMessageDialog(this, "勾选的应用中没有可替换的节点，请检查规则或右侧节点配置。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Path backupRoot = resolveBackupRootDir();
        AppBackupService backupService = new AppBackupService(backupRoot);
        Path batchDir;
        try {
            batchDir = backupService.createBatchDirectory();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "创建本地备份目录失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String targetDesc;
        if (!options.getMappingRules().isEmpty()) {
            targetDesc = "按多规则队列 (" + options.getMappingRules().size() + " 条) 及节点单独配置";
        } else if (options.getTargetModel() != null) {
            targetDesc = "[" + options.getTargetModel().getProvider() + "] " + options.getTargetModel().getModelName()
                    + (nodeOverrides.isEmpty() ? "" : "（含节点单独配置）");
        } else {
            targetDesc = "按右侧节点单独配置";
        }

        String confirmMsg = "即将批量替换模型：\n"
                + "1. 勾选应用：" + selectedApps.size() + " 个（可执行 " + executableApps + " 个，跳过 " + skippedApps + " 个，预计修改 " + totalSlots + " 处）\n"
                + "2. 目标策略：" + targetDesc + "\n"
                + "3. 默认参数策略：" + options.getParamStrategy().getDisplayName() + " | 思考层级：" + options.getThinkingConfig().getSummaryLabel() + "\n"
                + "4. 工作流发布：" + (options.isAutoPublishWorkflow() ? "更新草稿后自动发布上线" : "仅更新工作流草稿（不自动发布）") + "\n"
                + "5. 本地 YML 备份：" + batchDir.toAbsolutePath() + "\n\n"
                + "确认开始执行吗？";
        int choice = JOptionPane.showConfirmDialog(this, confirmMsg, "确认批量替换", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        lastBackupDir = batchDir;
        executionTableModel.clear();
        bottomTabs.setSelectedIndex(1);
        progressBar.setMinimum(0);
        progressBar.setMaximum(plan.size());
        progressBar.setValue(0);
        setWorking(true, "正在备份 YML 并执行批量替换...");

        BatchModelReplaceService service = new BatchModelReplaceService(consoleClient, backupService, replacementEngine);
        new SwingWorker<List<BatchExecutionResult>, BatchExecutionResult>() {
            @Override
            protected List<BatchExecutionResult> doInBackground() {
                return service.execute(plan, options, batchDir, new BatchModelReplaceService.ProgressListener() {
                    @Override
                    public void onProgress(int completedCount, int totalCount, BatchExecutionResult latestResult) {
                        publish(latestResult);
                    }
                });
            }

            @Override
            protected void process(List<BatchExecutionResult> chunks) {
                for (BatchExecutionResult r : chunks) {
                    executionTableModel.addRow(r);
                }
                progressBar.setValue(executionTableModel.getRowCount());
                statusLabel.setText("正在执行：" + executionTableModel.getRowCount() + " / " + plan.size());
            }

            @Override
            protected void done() {
                setWorking(false, statusLabel.getText());
                try {
                    List<BatchExecutionResult> results = get();
                    int success = 0;
                    int skipped = 0;
                    int failed = 0;
                    for (BatchExecutionResult r : results) {
                        if (r.getStatus() == BatchExecutionResult.Status.SUCCESS) success++;
                        else if (r.getStatus() == BatchExecutionResult.Status.SKIPPED) skipped++;
                        else failed++;
                    }
                    statusLabel.setText("执行完成：成功 " + success + "，跳过 " + skipped + "，失败 " + failed
                            + "；YML备份目录：" + batchDir.toAbsolutePath());
                    reloadModelsAndApps(false);
                } catch (Exception e) {
                    statusLabel.setText("执行异常：" + e.getMessage());
                    JOptionPane.showMessageDialog(BatchModelReplaceFrame.this, e.getMessage(), "批量执行失败", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void saveDatasourceCredentials(String email, String password) {
        datasource.setDifyUsername(email);
        datasource.setDifyPassword(password);
        if (appConfig == null || configService == null || appConfig.getDatasources() == null) {
            return;
        }
        for (DatasourceConfig item : appConfig.getDatasources()) {
            if (datasource.getId().equals(item.getId())) {
                item.setDifyUsername(email);
                item.setDifyPassword(password);
                break;
            }
        }
        try {
            configService.save(appConfig);
            if (onConfigSavedCallback != null) {
                onConfigSavedCallback.run();
            }
        } catch (IOException ignored) {
            // Do not interrupt login flow if saving config file fails.
        }
    }

    private Path resolveBackupRootDir() {
        String text = backupDirField.getText().trim();
        return text.isEmpty() ? AppBackupService.defaultBackupRootDir() : Paths.get(text);
    }

    private void chooseBackupDirectory() {
        JFileChooser chooser = new JFileChooser(resolveBackupRootDir().toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                backupDirField.setText(selected.getAbsolutePath());
            }
        }
    }

    private void openCurrentBackupDirectory() {
        Path target = lastBackupDir != null && Files.exists(lastBackupDir) ? lastBackupDir : resolveBackupRootDir();
        openDirectory(target);
    }

    private void openDirectory(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "打开目录失败：" + e.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void openUrl(String url) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            JOptionPane.showMessageDialog(this, "当前环境不支持自动打开浏览器：" + url, "打开编排", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "打开编排失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setWorking(boolean working, String text) {
        loginButton.setEnabled(!working);
        refreshAllButton.setEnabled(!working);
        dryRunButton.setEnabled(!working);
        executeButton.setEnabled(!working);
        chooseBackupDirButton.setEnabled(!working);
        statusLabel.setText(text);
    }

    private static DocumentListener simpleDocListener(Runnable action) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        };
    }

    private enum ParamStrategyChoice {
        INHERIT("继承全局策略", null),
        KEEP_COMMON("仅保留通用参数", ParamStrategy.KEEP_COMMON),
        CLEAR_ALL("清空全部参数", ParamStrategy.CLEAR_ALL),
        KEEP_ALL("保留全部原参数", ParamStrategy.KEEP_ALL);

        private final String label;
        private final ParamStrategy strategy;

        ParamStrategyChoice(String label, ParamStrategy strategy) {
            this.label = label;
            this.strategy = strategy;
        }

        static ParamStrategyChoice from(ParamStrategy strategy) {
            if (strategy == null) return INHERIT;
            for (ParamStrategyChoice c : values()) {
                if (c.strategy == strategy) return c;
            }
            return INHERIT;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum ThinkingChoice {
        INHERIT("继承全局", null),
        FOLLOW_STRATEGY("跟随参数策略", ThinkingConfig.followStrategy()),
        DISABLE("关闭思考 (false)", new ThinkingConfig(ThinkingMode.DISABLE, "")),
        ENABLE_DEFAULT("开启思考 (默认)", new ThinkingConfig(ThinkingMode.ENABLE_DEFAULT, "")),
        EFFORT_MINIMAL("开启 + minimal", new ThinkingConfig(ThinkingMode.ENABLE_WITH_EFFORT, "minimal")),
        EFFORT_LOW("开启 + low", new ThinkingConfig(ThinkingMode.ENABLE_WITH_EFFORT, "low")),
        EFFORT_MEDIUM("开启 + medium", new ThinkingConfig(ThinkingMode.ENABLE_WITH_EFFORT, "medium")),
        EFFORT_HIGH("开启 + high", new ThinkingConfig(ThinkingMode.ENABLE_WITH_EFFORT, "high")),
        EFFORT_XHIGH("开启 + xhigh", new ThinkingConfig(ThinkingMode.ENABLE_WITH_EFFORT, "xhigh")),
        EFFORT_MAX("开启 + max", new ThinkingConfig(ThinkingMode.ENABLE_WITH_EFFORT, "max")),
        OMIT("移除思考参数", new ThinkingConfig(ThinkingMode.OMIT, ""));

        private final String label;
        private final ThinkingConfig config;

        ThinkingChoice(String label, ThinkingConfig config) {
            this.label = label;
            this.config = config;
        }

        static ThinkingChoice from(ThinkingConfig cfg) {
            if (cfg == null) return INHERIT;
            if (cfg.getMode() == ThinkingMode.FOLLOW_STRATEGY) return FOLLOW_STRATEGY;
            if (cfg.getMode() == ThinkingMode.DISABLE) return DISABLE;
            if (cfg.getMode() == ThinkingMode.ENABLE_DEFAULT) return ENABLE_DEFAULT;
            if (cfg.getMode() == ThinkingMode.OMIT) return OMIT;
            if (cfg.getMode() == ThinkingMode.ENABLE_WITH_EFFORT) {
                for (ThinkingChoice c : values()) {
                    if (c.config != null && c.config.getMode() == ThinkingMode.ENABLE_WITH_EFFORT
                            && c.config.getReasoningEffort().equalsIgnoreCase(cfg.getReasoningEffort())) {
                        return c;
                    }
                }
                return EFFORT_MEDIUM;
            }
            return INHERIT;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class LoadSnapshot {
        private final List<DifyAppSummary> apps;
        private final List<DifyModelOption> models;

        LoadSnapshot(List<DifyAppSummary> apps, List<DifyModelOption> models) {
            this.apps = apps == null ? Collections.<DifyAppSummary>emptyList() : apps;
            this.models = models == null ? Collections.<DifyModelOption>emptyList() : models;
        }
    }

    private class AppTableModel extends AbstractTableModel {
        static final int LINK_COL = 7;
        private final String[] columns = {"选择", "应用名称", "模式", "工作流状态", "当前使用模型", "节点数", "命中数", "编排", "app_id", "API Token"};
        private final List<DifyAppSummary> rows = new ArrayList<DifyAppSummary>();
        private final List<Integer> hitCounts = new ArrayList<Integer>();

        void setRows(List<DifyAppSummary> newRows, ReplacementOptions options) {
            rows.clear();
            if (newRows != null) {
                rows.addAll(newRows);
            }
            refreshHitCounts(options);
        }

        void refreshHitCounts(ReplacementOptions options) {
            hitCounts.clear();
            for (DifyAppSummary app : rows) {
                if (options == null) {
                    hitCounts.add(0);
                } else {
                    ReplacementPlanItem item = replacementEngine.planOneApp(app, options);
                    hitCounts.add(item.getChanges().size());
                }
            }
            fireTableDataChanged();
        }

        List<DifyAppSummary> getRows() {
            return rows;
        }

        DifyAppSummary getRow(int row) {
            return rows.get(row);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Boolean.class;
            if (columnIndex == 5 || columnIndex == 6) return Integer.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0 && aValue instanceof Boolean) {
                String appId = rows.get(rowIndex).getAppId();
                if ((Boolean) aValue) {
                    checkedAppIds.add(appId);
                } else {
                    checkedAppIds.remove(appId);
                }
                fireTableCellUpdated(rowIndex, columnIndex);
                updateSelectionStatus();
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DifyAppSummary app = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return checkedAppIds.contains(app.getAppId());
                case 1: return app.getAppName();
                case 2: return app.getMode();
                case 3:
                    if (!app.isWorkflowMode()) return "直接生效";
                    return app.hasPublishedWorkflow() ? "已发布" : (app.isHasDraftWorkflow() ? "仅草稿" : "未初始化");
                case 4: return app.getModelsSummary();
                case 5: return app.getModelRefs().size();
                case 6: return rowIndex < hitCounts.size() ? hitCounts.get(rowIndex) : 0;
                case 7: return app.getConfigurationUrl().isEmpty() ? "" : "编排";
                case 8: return app.getAppId();
                case 9: return app.getTokensSummary();
                default: return "";
            }
        }
    }

    private class NodeConfigTableModel extends AbstractTableModel {
        private final String[] columns = {"替换", "节点/位置", "当前模型", "目标模型 (点击下拉可单独指定)", "参数策略", "思考层级"};
        private DifyAppSummary currentApp;
        private List<ModelRef> refs = Collections.emptyList();
        private ReplacementOptions currentOptions;

        void refresh(DifyAppSummary app, ReplacementOptions options) {
            this.currentApp = app;
            this.refs = app == null ? Collections.<ModelRef>emptyList() : app.getModelRefs();
            this.currentOptions = options;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return refs.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Boolean.class;
            if (columnIndex == 3) return DifyModelOption.class;
            if (columnIndex == 4) return ParamStrategyChoice.class;
            if (columnIndex == 5) return ThinkingChoice.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return currentApp != null && (columnIndex == 0 || columnIndex == 3 || columnIndex == 4 || columnIndex == 5);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (currentApp == null) return "";
            ModelRef ref = refs.get(rowIndex);
            NodeTargetOverride override = currentOptions == null ? null : currentOptions.getNodeOverride(currentApp.getAppId(), ref.getSlotKey());
            ResolvedTarget resolved = replacementEngine.resolveTargetForSlot(
                    currentApp.getAppId(), ref.getSlotKey(), ref.getProvider(), ref.getModelName(), currentOptions
            );

            switch (columnIndex) {
                case 0:
                    return override != null ? (override.isEnabled() && resolved != null) : (resolved != null);
                case 1:
                    return ref.getSlotLabel();
                case 2:
                    return ref.getProvider() + " : " + ref.getModelName()
                            + (ref.getModelMode().isEmpty() ? "" : " <" + ref.getModelMode() + ">");
                case 3:
                    if (override != null && override.getTargetModel() != null) {
                        return override.getTargetModel();
                    }
                    if (resolved != null) {
                        return "(全局规则) " + resolved.targetModel.getDisplayText();
                    }
                    return "(不替换 — 点此选择目标模型)";
                case 4:
                    return override == null ? ParamStrategyChoice.INHERIT : ParamStrategyChoice.from(override.getParamStrategy());
                case 5:
                    return override == null ? ThinkingChoice.INHERIT : ThinkingChoice.from(override.getThinkingConfig());
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (currentApp == null) return;
            ModelRef ref = refs.get(rowIndex);
            String appId = currentApp.getAppId();
            String slotKey = ref.getSlotKey();
            Map<String, NodeTargetOverride> appMap = getOrCreateAppOverrideMap(appId);
            NodeTargetOverride existing = appMap.get(slotKey);

            ThinkingConfig existingThinking = existing != null ? existing.getThinkingConfig() : null;
            ParamStrategy existingStrategy = existing != null ? existing.getParamStrategy() : null;

            if (columnIndex == 0 && aValue instanceof Boolean) {
                boolean checked = (Boolean) aValue;
                if (!checked) {
                    appMap.put(slotKey, new NodeTargetOverride(false,
                            existing != null ? existing.getTargetModel() : null,
                            existingStrategy, existingThinking));
                } else {
                    DifyModelOption chosenTarget = existing != null ? existing.getTargetModel() : null;
                    if (chosenTarget == null) {
                        ResolvedTarget globalResolved = replacementEngine.resolveTargetForSlot(
                                appId, slotKey, ref.getProvider(), ref.getModelName(),
                                new ReplacementOptions(
                                        currentOptions.getSourceProvider(), currentOptions.getSourceModelName(),
                                        currentOptions.isReplaceAllNodes(), currentOptions.getTargetModel(),
                                        currentOptions.getParamStrategy(), currentOptions.getThinkingConfig(),
                                        currentOptions.isAutoPublishWorkflow(),
                                        currentOptions.getMappingRules(), Collections.<String, Map<String, NodeTargetOverride>>emptyMap()
                                )
                        );
                        chosenTarget = globalResolved != null ? globalResolved.targetModel : (DifyModelOption) targetModelCombo.getSelectedItem();
                    }
                    if (chosenTarget == null) {
                        JOptionPane.showMessageDialog(BatchModelReplaceFrame.this, "请先在上方登录并加载目标模型，或在目标模型列选择", "提示", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    appMap.put(slotKey, new NodeTargetOverride(true, chosenTarget, existingStrategy, existingThinking));
                    checkedAppIds.add(appId);
                }
            } else if (columnIndex == 3 && aValue instanceof DifyModelOption) {
                DifyModelOption target = (DifyModelOption) aValue;
                appMap.put(slotKey, new NodeTargetOverride(true, target, existingStrategy, existingThinking));
                checkedAppIds.add(appId);
            } else if (columnIndex == 4 && aValue instanceof ParamStrategyChoice) {
                ParamStrategyChoice choice = (ParamStrategyChoice) aValue;
                DifyModelOption target = existing != null ? existing.getTargetModel() : (DifyModelOption) targetModelCombo.getSelectedItem();
                boolean enabled = existing != null ? existing.isEnabled() : (target != null);
                appMap.put(slotKey, new NodeTargetOverride(enabled, target, choice.strategy, existingThinking));
            } else if (columnIndex == 5 && aValue instanceof ThinkingChoice) {
                ThinkingChoice choice = (ThinkingChoice) aValue;
                DifyModelOption target = existing != null ? existing.getTargetModel() : (DifyModelOption) targetModelCombo.getSelectedItem();
                boolean enabled = existing != null ? existing.isEnabled() : (target != null);
                appMap.put(slotKey, new NodeTargetOverride(enabled, target, existingStrategy, choice.config));
                if (enabled) {
                    checkedAppIds.add(appId);
                }
            } else {
                return;
            }

            onAppRowSelected();
            refreshRulesAndNodeViews();
            updateSelectionStatus();
        }
    }

    private static class PreviewRow {
        final String executable;
        final String appName;
        final String mode;
        final String slotLabel;
        final String beforeProvider;
        final String beforeModel;
        final String afterProvider;
        final String afterModel;
        final String paramChange;
        final String note;

        PreviewRow(String executable, String appName, String mode, String slotLabel,
                   String beforeProvider, String beforeModel, String afterProvider, String afterModel,
                   String paramChange, String note) {
            this.executable = executable;
            this.appName = appName;
            this.mode = mode;
            this.slotLabel = slotLabel;
            this.beforeProvider = beforeProvider;
            this.beforeModel = beforeModel;
            this.afterProvider = afterProvider;
            this.afterModel = afterModel;
            this.paramChange = paramChange;
            this.note = note;
        }
    }

    private static class PreviewTableModel extends AbstractTableModel {
        private final String[] columns = {"可执行", "应用名称", "模式", "位置/节点", "原提供商", "原模型", "目标提供商", "目标模型", "参数变化", "说明"};
        private final List<PreviewRow> rows = new ArrayList<PreviewRow>();

        void setPlan(List<ReplacementPlanItem> plan) {
            rows.clear();
            if (plan != null) {
                for (ReplacementPlanItem item : plan) {
                    DifyAppSummary app = item.getApp();
                    String appName = app == null ? "" : app.getAppName();
                    String mode = app == null ? "" : app.getMode();
                    if (!item.isExecutable()) {
                        rows.add(new PreviewRow("跳过", appName, mode, "-", "-", "-", "-", "-", "-", item.getSkippedReason()));
                    } else {
                        for (ModelSlotChange ch : item.getChanges()) {
                            String paramDesc = ch.getBeforeParams().equals(ch.getAfterParams())
                                    ? "保持 " + ch.getAfterParams()
                                    : ch.getBeforeParams() + " -> " + ch.getAfterParams();
                            rows.add(new PreviewRow(
                                    "待更新", appName, mode, ch.getSlotLabel(),
                                    ch.getBeforeProvider(), ch.getBeforeModel(),
                                    ch.getAfterProvider(), ch.getAfterModel(),
                                    paramDesc, "模式: " + ch.getBeforeMode() + " -> " + ch.getAfterMode()
                            ));
                        }
                    }
                }
            }
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PreviewRow r = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return r.executable;
                case 1: return r.appName;
                case 2: return r.mode;
                case 3: return r.slotLabel;
                case 4: return r.beforeProvider;
                case 5: return r.beforeModel;
                case 6: return r.afterProvider;
                case 7: return r.afterModel;
                case 8: return r.paramChange;
                case 9: return r.note;
                default: return "";
            }
        }
    }

    private static class ExecutionResultTableModel extends AbstractTableModel {
        private final String[] columns = {"应用名称", "模式", "结果", "替换数", "已发布", "本地 YML 备份路径 (双击打开目录)", "详情/错误信息"};
        private final List<BatchExecutionResult> rows = new ArrayList<BatchExecutionResult>();

        void clear() {
            rows.clear();
            fireTableDataChanged();
        }

        void addRow(BatchExecutionResult row) {
            int idx = rows.size();
            rows.add(row);
            fireTableRowsInserted(idx, idx);
        }

        BatchExecutionResult getRow(int row) {
            return rows.get(row);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            BatchExecutionResult r = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return r.getAppName();
                case 1: return r.getMode();
                case 2: return r.getStatus().getLabel();
                case 3: return r.getChangedCount();
                case 4: return r.isPublished() ? "是" : "否";
                case 5:
                    if (!r.getPublishedBackupYmlPath().isEmpty()) {
                        return r.getBackupYmlPath() + " (+published)";
                    }
                    return r.getBackupYmlPath();
                case 6: return r.getMessage();
                default: return "";
            }
        }
    }

    private static class LinkRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = value == null ? "" : String.valueOf(value);
            label.setHorizontalAlignment(JLabel.CENTER);
            if ("编排".equals(text)) {
                String color = isSelected ? "#FFFFFF" : "#1677CC";
                label.setText("<html><a style='color:" + color + "' href=''>" + text + "</a></html>");
                label.setForeground(isSelected ? table.getSelectionForeground() : new Color(22, 119, 204));
            } else {
                label.setText("");
            }
            return label;
        }
    }
}