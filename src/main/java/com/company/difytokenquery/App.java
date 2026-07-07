package com.company.difytokenquery;

import com.company.difytokenquery.config.ConfigService;
import com.company.difytokenquery.db.DifyQueryService;
import com.company.difytokenquery.ui.MainFrame;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    FlatLightLaf.setup();
                    UIManager.put("Button.arc", 6);
                    UIManager.put("Component.arc", 6);
                    UIManager.put("TextComponent.arc", 6);
                } catch (Exception ignored) {
                    // Swing default look and feel is acceptable if FlatLaf cannot initialize.
                }
                MainFrame frame = new MainFrame(new ConfigService(), new DifyQueryService());
                frame.setVisible(true);
            }
        });
    }
}
