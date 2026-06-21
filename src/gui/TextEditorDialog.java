package gui;

import fs.FileSystem;
import user.UserManager;
import javax.swing.*;
import java.awt.*;

/** 文本编辑器对话框 */
public class TextEditorDialog extends JDialog {

    public TextEditorDialog(JFrame parent, FileSystem fs, UserManager um,
                            String fileName, String content, Runnable onClose) {
        super(parent, "Edit: " + fileName, true);
        JTextArea textArea = new JTextArea(content != null ? content : "");
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        textArea.setTabSize(4);
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> {
            if (!um.isLoggedIn()) {
                JOptionPane.showMessageDialog(this, "Please login first"); return;
            }
            String r = fs.writeFile(fileName, textArea.getText(), um.getCurrentUser().getUserId());
            JOptionPane.showMessageDialog(this, r);
            if (r.startsWith("write ok")) { onClose.run(); dispose(); }
        });
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        bp.add(saveBtn); bp.add(cancelBtn);
        add(bp, BorderLayout.SOUTH);

        textArea.getInputMap().put(KeyStroke.getKeyStroke("control S"), "save");
        textArea.getActionMap().put("save", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { saveBtn.doClick(); }
        });

        setSize(600, 450);
        setLocationRelativeTo(parent);
    }
}
