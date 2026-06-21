package gui;

import fs.DirectoryEntry;
import fs.FileSystem;
import user.UserManager;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/** 右侧文件表格面板（JTable） */
public class FileTablePanel extends JPanel {

    private final FileSystem fs;
    private final MainFrame mainFrame;
    private JTable table;
    private DefaultTableModel tableModel;
    private List<DirectoryEntry> currentEntries;

    public FileTablePanel(FileSystem fs, UserManager um, MainFrame mf) {
        super(new BorderLayout());
        this.fs = fs; this.mainFrame = mf;
        setBorder(BorderFactory.createTitledBorder("Files & Directories"));

        tableModel = new DefaultTableModel(
                new String[]{"Name", "Type", "Size", "Owner", "Read-Only"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(50);

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelected();
            }
        });

        JPopupMenu popup = new JPopupMenu();
        popup.add(mi("Open", this::openSelected));
        popup.add(mi("Delete", () -> {
            DirectoryEntry sel = getSelectedEntry();
            if (sel != null) mainFrame.execCmd("delete " + sel.getFileName());
        }));
        popup.add(mi("Properties", () -> {
            DirectoryEntry sel = getSelectedEntry();
            if (sel != null) mainFrame.msg(fs.getFileInfo(sel.getFileName()));
        }));
        table.setComponentPopupMenu(popup);

        add(new JScrollPane(table), BorderLayout.CENTER);
        JLabel pathLabel = new JLabel("  /  ");
        pathLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(pathLabel, BorderLayout.NORTH);
        refresh();
    }

    public void refresh() {
        tableModel.setRowCount(0);
        currentEntries = fs.listDir();
        for (DirectoryEntry e : currentEntries) {
            tableModel.addRow(new Object[]{e.getFileName(),
                    e.isDirectory() ? "<DIR>" : "FILE", e.getFileSize(),
                    e.getOwnerId() & 0xFF, e.isReadOnly() ? "Yes" : "No"});
        }
        JLabel pl = (JLabel) getComponent(1); // path label
        pl.setText("  " + fs.getCurrentPath() + "  ");
    }

    public DirectoryEntry getSelectedEntry() {
        int row = table.getSelectedRow();
        return (row >= 0 && currentEntries != null && row < currentEntries.size())
                ? currentEntries.get(row) : null;
    }

    private void openSelected() {
        DirectoryEntry sel = getSelectedEntry();
        if (sel != null) mainFrame.openFile(sel.getFileName());
    }

    private JMenuItem mi(String t, Runnable r) {
        JMenuItem i = new JMenuItem(t); i.addActionListener(e -> r.run()); return i;
    }
}
