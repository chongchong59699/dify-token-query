package com.company.difytokenquery.ui;

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

public class DifyLoginDialog extends JDialog {
    private final JTextField emailField = new JTextField(26);
    private final JPasswordField passwordField = new JPasswordField(26);
    private final JCheckBox saveCredentialsCheckBox = new JCheckBox("保存到当前 Dify 配置中", true);

    private boolean confirmed;

    public DifyLoginDialog(Window owner, String difyFrontendUrl, String initialEmail, String initialPassword) {
        super(owner, "登录 Dify 控制台", ModalityType.APPLICATION_MODAL);
        emailField.setText(initialEmail == null ? "" : initialEmail);
        passwordField.setText(initialPassword == null ? "" : initialPassword);
        initComponents(difyFrontendUrl);
        pack();
        setLocationRelativeTo(owner);
    }

    private void initComponents(String difyFrontendUrl) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));

        int row = 0;
        addLabelAndValue(form, row++, "Dify 地址", new JLabel(difyFrontendUrl == null || difyFrontendUrl.isEmpty() ? "(未配置)" : difyFrontendUrl));
        addLabelAndValue(form, row++, "登录邮箱", emailField);
        addLabelAndValue(form, row++, "登录密码", passwordField);

        GridBagConstraints cb = new GridBagConstraints();
        cb.gridx = 1;
        cb.gridy = row;
        cb.anchor = GridBagConstraints.WEST;
        cb.insets = new Insets(4, 4, 4, 4);
        form.add(saveCredentialsCheckBox, cb);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("登录");
        JButton cancelButton = new JButton("取消");
        okButton.addActionListener(e -> onOk());
        cancelButton.addActionListener(e -> dispose());
        buttons.add(okButton);
        buttons.add(cancelButton);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okButton);
    }

    private void addLabelAndValue(JPanel panel, int row, String label, java.awt.Component comp) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.gridy = row;
        lc.anchor = GridBagConstraints.EAST;
        lc.insets = new Insets(4, 4, 4, 8);
        panel.add(new JLabel(label), lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1;
        fc.gridy = row;
        fc.weightx = 1;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.insets = new Insets(4, 4, 4, 4);
        panel.add(comp, fc);
    }

    private void onOk() {
        if (emailField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写 Dify 登录邮箱", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (passwordField.getPassword().length == 0) {
            JOptionPane.showMessageDialog(this, "请填写 Dify 登录密码", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        confirmed = true;
        dispose();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getEmail() {
        return emailField.getText().trim();
    }

    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    public boolean isSaveCredentials() {
        return saveCredentialsCheckBox.isSelected();
    }
}