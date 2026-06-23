package gui;

import fs.DirectoryEntry;
import fs.FileSystem;
import user.UserManager;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/** 右侧文件列表面板，仿 Windows 资源管理器详情视图 */
public class FileTablePanel extends JPanel {

    private final FileSystem fs;
    private final MainFrame mainFrame;
    private JTable table;
    private DefaultTableModel tableModel;
    private List<DirectoryEntry> currentEntries;
    private JLabel pathLabel;

    public FileTablePanel(FileSystem fs, UserManager um, MainFrame mf) {
        super(new BorderLayout());
        this.fs = fs;
        this.mainFrame = mf;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Contents"),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));

        tableModel = new DefaultTableModel(
                new String[]{"Name", "Type", "Size", "Owner", "Read-Only"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);

        // 自定义渲染：文件夹/文件图标
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean sel, boolean focused, int row, int col) {
                super.getTableCellRendererComponent(t, value, sel, focused, row, col);
                setIcon(null);
                if (currentEntries != null && row < currentEntries.size()) {
                    setIcon(currentEntries.get(row).isDirectory()
                            ? UIManager.getIcon("FileView.directoryIcon")
                            : UIManager.getIcon("FileView.fileIcon"));
                }
                return this;
            }
        });

        // 双击打开
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) openSelected();
            }
        });

        // Enter=打开, Delete=删除, F2=重命名
        bindKey("ENTER", "open", this::openSelected);
        bindKey("DELETE", "delete", this::deleteSelected);
        bindKey("F2", "rename", this::renameSelected);
        // Ctrl+C=复制, Ctrl+X=剪切, Ctrl+V=粘贴
        bindKey("ctrl C", "copy", () -> {
            DirectoryEntry s = getSelectedEntry();
            if (s != null) mainFrame.doCopy(s.getFileName());
        });
        bindKey("ctrl X", "cut", () -> {
            DirectoryEntry s = getSelectedEntry();
            if (s != null) mainFrame.doCut(s.getFileName());
        });
        bindKey("ctrl V", "paste", mainFrame::doPaste);

        // 右键菜单
        JPopupMenu popup = new JPopupMenu();
        popup.add(mi("Open", this::openSelected));
        popup.addSeparator();
        popup.add(mi("Copy", () -> {
            DirectoryEntry s = getSelectedEntry();
            if (s != null) mainFrame.doCopy(s.getFileName());
        }));
        popup.add(mi("Cut", () -> {
            DirectoryEntry s = getSelectedEntry();
            if (s != null) mainFrame.doCut(s.getFileName());
        }));
        popup.add(mi("Paste", mainFrame::doPaste));
        popup.addSeparator();
        popup.add(mi("New File", mainFrame::doNewFile));
        popup.add(mi("New Folder", mainFrame::doNewDir));
        popup.addSeparator();
        popup.add(mi("Rename", this::renameSelected));
        popup.add(mi("Delete", this::deleteSelected));
        popup.addSeparator();
        popup.add(mi("Change Attributes", () -> {
            DirectoryEntry s = getSelectedEntry();
            if (s != null) mainFrame.doChmod(s.getFileName());
        }));
        popup.add(mi("Properties", () -> {
            DirectoryEntry s = getSelectedEntry();
            if (s != null) mainFrame.showFileInfo(s.getFileName());
        }));

        // 右键自动选中行
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int r = table.rowAtPoint(e.getPoint());
                    if (r >= 0) table.setRowSelectionInterval(r, r);
                }
            }
        });
        table.setComponentPopupMenu(popup);

        pathLabel = new JLabel("  /  ");
        pathLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pathLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(pathLabel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        refresh();
    }

    private void bindKey(String key, String name, Runnable action) {
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
        table.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    public void refresh() {
        tableModel.setRowCount(0);
        java.util.List<DirectoryEntry> all = fs.listDir();
        // UI 层强制权限过滤
        int uid = fs.getCurrentUserId();
        String curPath = fs.getCurrentPath();
        currentEntries = new java.util.ArrayList<>();
        for (DirectoryEntry e : all) {
            if (uid <= 1 || curPath.startsWith("/shared")) currentEntries.add(e);
            else if ((e.getOwnerId() & 0xFF) == uid) currentEntries.add(e);
            else if ("shared".equals(e.getFileName())) currentEntries.add(e);
            else if ("home".equals(e.getFileName()) && "/".equals(curPath)) currentEntries.add(e);
        }
        for (DirectoryEntry e : currentEntries) {
            tableModel.addRow(new Object[]{
                e.getFileName(),
                e.isDirectory() ? "Folder" : "File",
                formatSize(e.getFileSize()),
                e.getOwnerId() & 0xFF,
                e.isReadOnly() ? "Yes" : ""
            });
        }
        pathLabel.setText("    " + fs.getCurrentPath());
    }

    public DirectoryEntry getSelectedEntry() {
        int row = table.getSelectedRow();
        if (row >= 0 && currentEntries != null) {
            int mr = table.convertRowIndexToModel(row);
            if (mr < currentEntries.size()) return currentEntries.get(mr);
        }
        return null;
    }

    private void openSelected() {
        DirectoryEntry s = getSelectedEntry();
        if (s != null) mainFrame.openFile(s.getFileName());
    }
    private void deleteSelected() {
        DirectoryEntry s = getSelectedEntry();
        if (s != null) mainFrame.doDeleteByName(s.getFileName());
    }
    private void renameSelected() {
        DirectoryEntry s = getSelectedEntry();
        if (s != null) mainFrame.doRename(s.getFileName());
    }

    private String formatSize(int b) {
        if (b == 0) return "0 B";
        if (b < 1024) return b + " B";
        return String.format("%.1f KB", b / 1024.0);
    }

    private JMenuItem mi(String t, Runnable r) {
        JMenuItem i = new JMenuItem(t); i.addActionListener(e -> r.run()); return i;
    }
}
