package com.company.difytokenquery.ui;

import com.company.difytokenquery.config.SqlFileService;
import com.company.difytokenquery.db.DatasourceConfig;
import com.company.difytokenquery.db.DifyDatabaseService;
import com.company.difytokenquery.db.SqlQueryResult;
import com.company.difytokenquery.db.SqlQueryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Database/schema/table browser with persistent SQL editor tabs. */
public class DatabaseExplorerFrame extends JFrame {
    private final DatasourceConfig datasource;
    private final DifyDatabaseService metadataService;
    private final SqlQueryService sqlService;
    private final SqlFileService fileService;
    private final DefaultMutableTreeNode root;
    private final JTree tree;
    private final JTable structureTable = new JTable(new StructureTableModel());
    private final JTabbedPane sqlTabs = new JTabbedPane();
    private final JLabel statusLabel = new JLabel("正在加载数据库...");
    private final List<SqlEditorTab> editors = new ArrayList<SqlEditorTab>();

    public DatabaseExplorerFrame(Frame owner, DatasourceConfig datasource) {
        super("数据库浏览与 SQL 查询 - " + (datasource == null ? "" : datasource.getName()));
        this.datasource = datasource == null ? new DatasourceConfig() : datasource.copy();
        this.metadataService = new DifyDatabaseService();
        this.sqlService = new SqlQueryService();
        this.fileService = new SqlFileService();
        this.root = new DefaultMutableTreeNode(new BrowserNode(BrowserNode.ROOT, this.datasource.getName(), null, null, null));
        this.tree = new JTree(new DefaultTreeModel(root));
        initComponents();
        loadDatabases();
        loadSavedQueries();
    }

    private void initComponents() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(new Dimension(Math.min(1500, Toolkit.getDefaultToolkit().getScreenSize().width - 80),
                Math.min(930, Toolkit.getDefaultToolkit().getScreenSize().height - 80)));
        setMinimumSize(new Dimension(1050, 700));
        setLocationRelativeTo(getOwner());

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            public void treeWillExpand(TreeExpansionEvent event) { loadChildren((DefaultMutableTreeNode) event.getPath().getLastPathComponent()); }
            public void treeWillCollapse(TreeExpansionEvent event) { }
        });
        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                TreePath path = tree.getPathForLocation(event.getX(), event.getY());
                if (path == null) return;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                BrowserNode data = (BrowserNode) node.getUserObject();
                if (event.getClickCount() == 1 && data.type == BrowserNode.TABLE) showColumns(data);
                if (event.getClickCount() == 2 && (data.type == BrowserNode.DATABASE || data.type == BrowserNode.TABLE)) openQueryFor(data);
            }
        });

        structureTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        structureTable.setAutoCreateRowSorter(true);
        structureTable.setRowHeight(25);
        structureTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {55, 190, 130, 130, 85, 220, 220};
        for (int i = 0; i < widths.length; i++) structureTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JPanel left = new JPanel(new BorderLayout(4, 4));
        JButton refresh = new JButton("刷新数据库");
        refresh.addActionListener(e -> loadDatabases());
        left.add(new JScrollPane(tree), BorderLayout.CENTER);
        left.add(refresh, BorderLayout.SOUTH);

        JButton newQuery = new JButton("新建 SQL");
        JButton save = new JButton("保存");
        JButton saveAs = new JButton("另存为");
        JButton close = new JButton("关闭");
        newQuery.addActionListener(e -> {
            BrowserNode selected = selectedBrowserNode();
            openQueryFor(selected == null || selected.type == BrowserNode.ROOT ? null : selected);
        });
        save.addActionListener(e -> saveCurrent(false));
        saveAs.addActionListener(e -> saveCurrent(true));
        close.addActionListener(e -> dispose());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(newQuery); toolbar.add(save); toolbar.add(saveAs); toolbar.add(close);

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(toolbar, BorderLayout.NORTH);
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(structureTable), sqlTabs);
        rightSplit.setResizeWeight(0.27);
        right.add(rightSplit, BorderLayout.CENTER);
        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        mainSplit.setDividerLocation(260);
        content.add(mainSplit, BorderLayout.CENTER);
        content.add(statusLabel, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void loadDatabases() {
        statusLabel.setText("正在加载数据库列表...");
        root.removeAllChildren();
        ((DefaultTreeModel) tree.getModel()).reload();
        new SwingWorker<List<String>, Void>() {
            private Exception failure;
            protected List<String> doInBackground() { try { return metadataService.listDatabases(datasource); } catch (Exception e) { failure = e; return new ArrayList<String>(); } }
            protected void done() {
                if (failure != null) { statusLabel.setText("加载数据库失败：" + failure.getMessage()); return; }
                for (String db : getQuiet()) {
                    DefaultMutableTreeNode databaseNode = new DefaultMutableTreeNode(new BrowserNode(BrowserNode.DATABASE, db, db, null, null));
                    databaseNode.add(new DefaultMutableTreeNode("加载中..."));
                    root.add(databaseNode);
                }
                ((DefaultTreeModel) tree.getModel()).reload(root);
                statusLabel.setText("已加载 " + root.getChildCount() + " 个数据库");
            }
            private List<String> getQuiet() { try { return get(); } catch (Exception e) { return new ArrayList<String>(); } }
        }.execute();
    }

    private void loadChildren(DefaultMutableTreeNode node) {
        BrowserNode data = (BrowserNode) node.getUserObject();
        if (data.type != BrowserNode.ROOT && node.getChildCount() > 0
                && !(node.getChildAt(0).toString().equals("加载中..."))) return;
        if (data.type != BrowserNode.DATABASE && data.type != BrowserNode.SCHEMA) return;
        node.removeAllChildren();
        statusLabel.setText("正在加载 " + data.label + "...");
        new SwingWorker<List<String>, Void>() {
            private Exception failure;
            protected List<String> doInBackground() {
                try { return data.type == BrowserNode.DATABASE ? metadataService.listSchemas(datasource, data.database) : metadataService.listTables(datasource, data.database, data.schema); }
                catch (Exception e) { failure = e; return new ArrayList<String>(); }
            }
            protected void done() {
                if (failure != null) { statusLabel.setText("加载失败：" + failure.getMessage()); return; }
                for (String value : getQuiet()) {
                    int type = data.type == BrowserNode.DATABASE ? BrowserNode.SCHEMA : BrowserNode.TABLE;
                    DefaultMutableTreeNode child = new DefaultMutableTreeNode(new BrowserNode(type, value, data.database, data.type == BrowserNode.DATABASE ? value : data.schema, type == BrowserNode.TABLE ? value : null));
                    if (type == BrowserNode.SCHEMA) child.add(new DefaultMutableTreeNode("加载中..."));
                    node.add(child);
                }
                ((DefaultTreeModel) tree.getModel()).reload(node);
                statusLabel.setText("已加载 " + node.getChildCount() + " 项");
            }
            private List<String> getQuiet() { try { return get(); } catch (Exception e) { return new ArrayList<String>(); } }
        }.execute();
    }

    private void showColumns(BrowserNode table) {
        statusLabel.setText("正在读取表结构：" + table.schema + "." + table.table);
        new SwingWorker<List<DifyDatabaseService.ColumnInfo>, Void>() {
            private Exception failure;
            protected List<DifyDatabaseService.ColumnInfo> doInBackground() { try { return metadataService.listColumns(datasource, table.database, table.schema, table.table); } catch (Exception e) { failure = e; return new ArrayList<DifyDatabaseService.ColumnInfo>(); } }
            protected void done() {
                if (failure != null) { statusLabel.setText("读取表结构失败：" + failure.getMessage()); return; }
                try { ((StructureTableModel) structureTable.getModel()).setColumns(get()); statusLabel.setText("表结构：" + table.schema + "." + table.table); } catch (Exception e) { statusLabel.setText("读取表结构失败：" + e.getMessage()); }
            }
        }.execute();
    }

    private void openQueryFor(BrowserNode node) {
        String database = node == null ? datasource.getDatabase() : node.database;
        String sql = "";
        if (node != null && node.type == BrowserNode.TABLE) sql = "select * from \"" + quote(node.schema) + "\".\"" + quote(node.table) + "\";";
        SqlEditorTab editor = new SqlEditorTab(database, sql, null);
        editors.add(editor);
        sqlTabs.addTab(database == null ? "SQL" : database, editor);
        sqlTabs.setSelectedComponent(editor);
    }

    private BrowserNode selectedBrowserNode() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return value instanceof BrowserNode ? (BrowserNode) value : null;
    }

    private String quote(String value) { return value == null ? "" : value.replace("\"", "\"\""); }

    private void loadSavedQueries() {
        try { for (Path file : fileService.list()) { SqlEditorTab editor = new SqlEditorTab(datasource.getDatabase(), fileService.read(file), file); editors.add(editor); sqlTabs.addTab(file.getFileName().toString(), editor); } }
        catch (IOException e) { statusLabel.setText("读取已保存 SQL 失败：" + e.getMessage()); }
    }

    private SqlEditorTab currentEditor() { int index = sqlTabs.getSelectedIndex(); return index < 0 ? null : (SqlEditorTab) sqlTabs.getComponentAt(index); }

    private void saveCurrent(boolean saveAs) {
        SqlEditorTab editor = currentEditor();
        if (editor == null) { JOptionPane.showMessageDialog(this, "请先打开 SQL 查询页", "保存 SQL", JOptionPane.INFORMATION_MESSAGE); return; }
        if (saveAs || editor.file == null) {
            JFileChooser chooser = new JFileChooser(fileService.getDirectory().toFile());
            chooser.setDialogTitle("保存 SQL 文件"); chooser.setSelectedFile(fileService.createPath("query.sql").toFile());
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path selected = chooser.getSelectedFile().toPath();
            if (!selected.getFileName().toString().toLowerCase().endsWith(".sql")) selected = selected.resolveSibling(selected.getFileName() + ".sql");
            editor.file = selected;
        }
        try { fileService.save(editor.file, editor.textArea.getText()); sqlTabs.setTitleAt(sqlTabs.getSelectedIndex(), editor.file.getFileName().toString()); statusLabel.setText("已保存：" + editor.file); }
        catch (IOException e) { JOptionPane.showMessageDialog(this, e.getMessage(), "保存 SQL 失败", JOptionPane.ERROR_MESSAGE); }
    }

    private class SqlEditorTab extends JPanel {
        private final JTextArea textArea = new JTextArea();
        private final JTable resultTable = new JTable(new SqlResultTableModel());
        private final JLabel resultStatus = new JLabel("未执行");
        private final JLabel totalLabel = new JLabel("总量：-");
        private final JComboBox<Integer> pageSize = new JComboBox<Integer>(new Integer[]{100, 500, 1000});
        private final JButton previous = new JButton("上一页");
        private final JButton next = new JButton("下一页");
        private Path file;
        private String database;
        private int page;
        private long total = -1L;
        private int requestId;

        SqlEditorTab(String database, String sql, Path file) {
            super(new BorderLayout(4, 4)); this.database = database; this.file = file;
            textArea.setText(sql); textArea.setLineWrap(false); textArea.setTabSize(4);
            textArea.getInputMap(JTextArea.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("control S"), "saveSql");
            textArea.getActionMap().put("saveSql", new javax.swing.AbstractAction() {
                public void actionPerformed(java.awt.event.ActionEvent event) { saveCurrent(false); }
            });
            resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); resultTable.setAutoCreateRowSorter(true); resultTable.setRowHeight(24);
            JButton execute = new JButton("执行");
            execute.addActionListener(e -> executeSql()); previous.addActionListener(e -> { if (page > 0) { page--; executeSql(); } }); next.addActionListener(e -> { if (total < 0 || (long) (page + 1) * pageSizeValue() < total) { page++; executeSql(); } });
            previous.setEnabled(false); next.setEnabled(false);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT)); actions.add(execute); actions.add(new JLabel("每页")); actions.add(pageSize); actions.add(previous); actions.add(next); actions.add(totalLabel); actions.add(resultStatus);
            pageSize.addActionListener(e -> { page = 0; executeSql(); });
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(textArea), new JScrollPane(resultTable)); split.setResizeWeight(0.42);
            add(actions, BorderLayout.NORTH); add(split, BorderLayout.CENTER);
        }
        private int pageSizeValue() { return (Integer) pageSize.getSelectedItem(); }
        private void executeSql() {
            String selected = textArea.getSelectedText(); String sql = selected == null || selected.trim().isEmpty() ? textArea.getText() : selected;
            final int currentRequest = ++requestId; final int currentPage = page; final int size = pageSizeValue();
            total = -1L;
            resultStatus.setText("执行中..."); previous.setEnabled(false); next.setEnabled(false);
            new SwingWorker<SqlQueryResult, Void>() {
                private Exception failure;
                protected SqlQueryResult doInBackground() { try { return sqlService.executePage(sql, databaseDatasource(), currentPage, size); } catch (Exception e) { failure = e; return null; } }
                protected void done() {
                    if (currentRequest != requestId) return;
                    if (failure != null) { resultStatus.setText("失败：" + failure.getMessage()); totalLabel.setText("总量：-"); return; }
                    try { SqlQueryResult result = get(); ((SqlResultTableModel) resultTable.getModel()).setResult(result); if (result.isResultSet()) { resultStatus.setText("第 " + (currentPage + 1) + " 页，共 " + result.getRows().size() + " 条"); updatePaging(); countAsync(sql, currentRequest); } else { resultStatus.setText("执行成功，影响 " + result.getAffectedRows() + " 行"); totalLabel.setText("总量：-"); } } catch (Exception e) { resultStatus.setText("失败：" + e.getMessage()); }
                }
            }.execute();
        }
        private DatasourceConfig databaseDatasource() { return database == null || database.trim().isEmpty() ? datasource.copy() : datasource.copyForDatabase(database); }
        private void countAsync(final String sql, final int currentRequest) {
            new SwingWorker<Long, Void>() { private Exception failure; protected Long doInBackground() { try { return sqlService.count(sql, databaseDatasource()); } catch (Exception e) { failure = e; return -1L; } } protected void done() { if (currentRequest != requestId) return; try { total = get(); totalLabel.setText(total >= 0 ? "总量：" + total : "总量：未知"); updatePaging(); } catch (Exception e) { totalLabel.setText("总量：未知"); } } }.execute();
        }
        private void updatePaging() { previous.setEnabled(page > 0); next.setEnabled(total < 0 || (long) (page + 1) * pageSizeValue() < total); }
    }

    private static class BrowserNode {
        static final int ROOT=0, DATABASE=1, SCHEMA=2, TABLE=3;
        final int type; final String label, database, schema, table;
        BrowserNode(int type, String label, String database, String schema, String table) { this.type=type; this.label=label; this.database=database; this.schema=schema; this.table=table; }
        public String toString() { return label; }
    }

    private static class StructureTableModel extends javax.swing.table.AbstractTableModel {
        private final String[] columns = {"序号", "列名", "数据类型", "内部类型", "可空", "默认值", "备注"};
        private List<DifyDatabaseService.ColumnInfo> rows = new ArrayList<DifyDatabaseService.ColumnInfo>();
        void setColumns(List<DifyDatabaseService.ColumnInfo> rows) { this.rows = rows == null ? new ArrayList<DifyDatabaseService.ColumnInfo>() : rows; fireTableDataChanged(); }
        public int getRowCount() { return rows.size(); } public int getColumnCount() { return columns.length; } public String getColumnName(int c) { return columns[c]; }
        public Object getValueAt(int r, int c) { DifyDatabaseService.ColumnInfo v=rows.get(r); switch(c){case 0:return v.getOrdinal();case 1:return v.getName();case 2:return v.getDataType();case 3:return v.getUdtName();case 4:return v.getNullable();case 5:return v.getDefaultValue();case 6:return v.getComment();default:return "";} }
    }
}
