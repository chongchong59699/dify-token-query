package com.company.difytokenquery.ui;

import com.company.difytokenquery.db.DatasourceConfig;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

public class DatasourceEditDialog extends JDialog {
    private final JTextField nameField = new JTextField(28);
    private final JTextField hostField = new JTextField(28);
    private final JTextField portField = new JTextField(28);
    private final JTextField databaseField = new JTextField(28);
    private final JTextField jdbcUrlField = new JTextField(28);
    private final JTextField difyFrontendUrlField = new JTextField(28);
    private final JTextField usernameField = new JTextField(28);
    private final JPasswordField passwordField = new JPasswordField(28);
    private final JCheckBox enabledCheckBox = new JCheckBox("启用", true);

    private DatasourceConfig source;
    private DatasourceConfig result;

    public DatasourceEditDialog(Window owner, DatasourceConfig datasource) {
        super(owner, datasource == null ? "新增Dify配置" : "编辑Dify配置", ModalityType.APPLICATION_MODAL);
        this.source = datasource == null ? new DatasourceConfig() : datasource.copy();
        initComponents();
        fillFields();
        pack();
        setLocationRelativeTo(owner);
    }

    public DatasourceConfig getResult() {
        return result;
    }

    private void initComponents() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));

        int row = 0;
        addRow(formPanel, row++, "名称", nameField, null);
        addRow(formPanel, row++, "Host", hostField, null);
        addRow(formPanel, row++, "Port", portField, null);
        addRow(formPanel, row++, "Database", databaseField, null);

        JButton buildJdbcButton = new JButton("生成JDBC");
        buildJdbcButton.addActionListener(e -> jdbcUrlField.setText(buildJdbcUrlFromFields()));
        addRow(formPanel, row++, "JDBC URL", jdbcUrlField, buildJdbcButton);

        addRow(formPanel, row++, "Dify前端地址", difyFrontendUrlField, null);
        addRow(formPanel, row++, "用户名", usernameField, null);
        addRow(formPanel, row++, "密码", passwordField, null);

        GridBagConstraints checkBoxConstraints = new GridBagConstraints();
        checkBoxConstraints.gridx = 1;
        checkBoxConstraints.gridy = row;
        checkBoxConstraints.gridwidth = 2;
        checkBoxConstraints.anchor = GridBagConstraints.WEST;
        checkBoxConstraints.insets = new Insets(4, 4, 4, 4);
        formPanel.add(enabledCheckBox, checkBoxConstraints);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");
        okButton.addActionListener(e -> onOk());
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        setLayout(new BorderLayout());
        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okButton);
    }

    private void addRow(JPanel panel, int row, String label, JTextField field, JButton actionButton) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.EAST;
        labelConstraints.insets = new Insets(4, 4, 4, 8);
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(4, 4, 4, 4);
        panel.add(field, fieldConstraints);

        if (actionButton != null) {
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.gridx = 2;
            buttonConstraints.gridy = row;
            buttonConstraints.insets = new Insets(4, 4, 4, 4);
            panel.add(actionButton, buttonConstraints);
        }
    }

    private void fillFields() {
        nameField.setText(nullToEmpty(source.getName()));
        hostField.setText(nullToEmpty(source.getHost()));
        portField.setText(String.valueOf(source.getPort() <= 0 ? 5432 : source.getPort()));
        databaseField.setText(nullToEmpty(source.getDatabase()));
        jdbcUrlField.setText(nullToEmpty(source.getJdbcUrl()));
        difyFrontendUrlField.setText(nullToEmpty(source.getDifyFrontendUrl()));
        usernameField.setText(nullToEmpty(source.getUsername()));
        passwordField.setText(nullToEmpty(source.getPassword()));
        enabledCheckBox.setSelected(source.isEnabled());
    }

    private void onOk() {
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        String database = databaseField.getText().trim();
        String jdbcUrl = jdbcUrlField.getText().trim();
        String difyFrontendUrl = difyFrontendUrlField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写Dify配置名称", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException("port out of range");
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "端口必须是 1-65535 之间的数字", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (jdbcUrl.isEmpty()) {
            if (host.isEmpty() || database.isEmpty()) {
                JOptionPane.showMessageDialog(this, "JDBC URL 为空时，请填写 Host 和 Database", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            jdbcUrl = DatasourceConfig.buildJdbcUrl(host, port, database);
        }

        DatasourceConfig datasource = source.copy();
        datasource.setName(name);
        datasource.setHost(host);
        datasource.setPort(port);
        datasource.setDatabase(database);
        datasource.setJdbcUrl(jdbcUrl);
        datasource.setDifyFrontendUrl(difyFrontendUrl);
        datasource.setUsername(username);
        datasource.setPassword(password);
        datasource.setEnabled(enabledCheckBox.isSelected());
        datasource.normalize();
        result = datasource;
        dispose();
    }

    private String buildJdbcUrlFromFields() {
        int port = 5432;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ignored) {
            portField.setText("5432");
        }
        return DatasourceConfig.buildJdbcUrl(hostField.getText(), port, databaseField.getText());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
