package com.mineclone;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

/**
 * Dialogos de salvar/carregar mundo. O JavaFX original usava FileChooser;
 * aqui usamos javax.swing.JFileChooser (parte do JDK, nao depende de JavaFX
 * nem de LWJGL) para manter a mesma experiencia de "escolher arquivo".
 */
public class WorldIO {

    private static final FileNameExtensionFilter FILTER = new FileNameExtensionFilter("Mundo Minecraft (*.txt)", "txt");

    public interface Callback { void onDone(File file, boolean isSave); }

    public static void saveDialog(File initialDir, Callback cb) {
        SwingUtilities.invokeLater(() -> {
            JFileChooser fc = new JFileChooser(initialDir);
            fc.setDialogTitle("Salvar Mundo");
            fc.setFileFilter(FILTER);
            fc.setSelectedFile(new File(initialDir, "mundo.txt"));
            int r = fc.showSaveDialog(null);
            if (r == JFileChooser.APPROVE_OPTION) cb.onDone(fc.getSelectedFile(), true);
        });
    }

    public static void loadDialog(File initialDir, Callback cb) {
        SwingUtilities.invokeLater(() -> {
            JFileChooser fc = new JFileChooser(initialDir);
            fc.setDialogTitle("Carregar Mundo");
            fc.setFileFilter(FILTER);
            int r = fc.showOpenDialog(null);
            if (r == JFileChooser.APPROVE_OPTION) cb.onDone(fc.getSelectedFile(), false);
        });
    }

    public static void showInfo(String msg) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, msg, "Minecraft Clone", JOptionPane.INFORMATION_MESSAGE));
    }

    public static void showError(String msg) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, msg, "Erro", JOptionPane.ERROR_MESSAGE));
    }
}
