package gui;

import disk.DiskConstants;
import fs.DirectoryEntry;
import fs.FileSystem;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;

/** 左侧目录树面板（JTree），支持右键菜单创建/删除 */
public class FileTreePanel extends JPanel {

    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private final Consumer<String> onDirSelected;
    private final MainFrame mainFrame;
    private FileSystem fs;

    public FileTreePanel(FileSystem fs, Consumer<String> onDirSelected, MainFrame mainFrame) {
        super(new BorderLayout());
        this.fs = fs;
        this.onDirSelected = onDirSelected;
        this.mainFrame = mainFrame;
        setBorder(BorderFactory.createTitledBorder("Directory Tree"));
        rootNode = new DefaultMutableTreeNode(
                new DirNode("/", DiskConstants.ROOT_DIR_BLOCK, true, true, "/"));
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tree.setCellRenderer(new DirTreeCellRenderer());

        // 单击选择 → 导航到该目录
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = getSelectedNode();
            if (node == null) return;
            if (node.getUserObject() instanceof DirNode dn && dn.isDir) {
                expandNode(node, fs, dn.dirBlock, dn.fullPath);
                onDirSelected.accept(dn.fullPath);
            }
        });

        // 双击 → 目录导航 / 文件打开
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    DefaultMutableTreeNode node =
                            (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node.getUserObject() instanceof DirNode dn) {
                        if (dn.isDir) {
                            onDirSelected.accept(dn.fullPath);
                        } else {
                            // 先导航到父目录再打开文件
                            DefaultMutableTreeNode parent =
                                    (DefaultMutableTreeNode) node.getParent();
                            if (parent != null
                                    && parent.getUserObject() instanceof DirNode pdn) {
                                onDirSelected.accept(pdn.fullPath);
                            }
                            mainFrame.openFile(dn.name);
                        }
                    }
                }
            }
        });

        // ====== 右键菜单 ======
        JPopupMenu popup = new JPopupMenu();

        JMenuItem newFileItem = new JMenuItem("New File");
        newFileItem.addActionListener(e -> {
            DirNode dn = getSelectedDirNode();
            if (dn == null) return;
            // 导航到选中目录
            onDirSelected.accept(dn.fullPath);
            mainFrame.doNewFile();
        });
        popup.add(newFileItem);

        JMenuItem newDirItem = new JMenuItem("New Directory");
        newDirItem.addActionListener(e -> {
            DirNode dn = getSelectedDirNode();
            if (dn == null) return;
            onDirSelected.accept(dn.fullPath);
            mainFrame.doNewDir();
        });
        popup.add(newDirItem);

        popup.addSeparator();

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> {
            DirNode dn = getSelectedDirNode();
            if (dn == null) return;
            if (dn.isRoot) {
                JOptionPane.showMessageDialog(this,
                        "Cannot delete root directory", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            // 先导航到父目录再删除
            DefaultMutableTreeNode node = getSelectedNode();
            DefaultMutableTreeNode parent =
                    (DefaultMutableTreeNode) node.getParent();
            if (parent != null && parent.getUserObject() instanceof DirNode pdn) {
                onDirSelected.accept(pdn.fullPath);
            }
            mainFrame.doDeleteByName(dn.name);
        });
        popup.add(deleteItem);

        popup.addSeparator();

        JMenuItem refreshItem = new JMenuItem("Refresh");
        refreshItem.addActionListener(e -> mainFrame.refreshAll());
        popup.add(refreshItem);

        JMenuItem propsItem = new JMenuItem("Properties");
        propsItem.addActionListener(e -> {
            DirNode dn = getSelectedDirNode();
            if (dn == null) return;
            if (dn.isRoot) {
                JOptionPane.showMessageDialog(this,
                        "Root Directory\nBlock: " + DiskConstants.ROOT_DIR_BLOCK
                        + "\nTotal blocks: " + DiskConstants.BLOCK_COUNT,
                        "Properties", JOptionPane.INFORMATION_MESSAGE);
            } else {
                DefaultMutableTreeNode node = getSelectedNode();
                DefaultMutableTreeNode parent =
                        (DefaultMutableTreeNode) node.getParent();
                if (parent != null && parent.getUserObject() instanceof DirNode pdn) {
                    onDirSelected.accept(pdn.fullPath);
                }
                mainFrame.showFileInfo(dn.name);
            }
        });
        popup.add(propsItem);

        tree.setComponentPopupMenu(popup);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        refresh(fs);
    }

    private DefaultMutableTreeNode getSelectedNode() {
        TreePath path = tree.getSelectionPath();
        return path != null ? (DefaultMutableTreeNode) path.getLastPathComponent() : null;
    }

    /** 获取选中节点的 DirNode；如果不是目录则取其父目录 */
    private DirNode getSelectedDirNode() {
        DefaultMutableTreeNode node = getSelectedNode();
        if (node == null) return null;
        if (node.getUserObject() instanceof DirNode dn) {
            if (dn.isDir) return dn;
            // 文件节点 → 取其父目录
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent != null && parent.getUserObject() instanceof DirNode pdn) return pdn;
        }
        return null;
    }

    // ====================== refresh ======================

    public void refresh(FileSystem fs) {
        this.fs = fs;
        rootNode.removeAllChildren();
        buildChildren(rootNode, fs, DiskConstants.ROOT_DIR_BLOCK, "/");
        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void buildChildren(DefaultMutableTreeNode parentNode, FileSystem fs,
                               int dirBlock, String parentPath) {
        for (DirectoryEntry e : fs.listDir(dirBlock)) {
            String fullPath = parentPath.equals("/")
                    ? "/" + e.getFileName()
                    : parentPath + "/" + e.getFileName();
            DefaultMutableTreeNode child;
            if (e.isDirectory()) {
                child = new DefaultMutableTreeNode(new DirNode(
                        e.getFileName(), e.getStartBlock() & 0xFF,
                        true, false, fullPath));
                buildChildren(child, fs, e.getStartBlock() & 0xFF, fullPath);
            } else {
                child = new DefaultMutableTreeNode(new DirNode(
                        e.getFileName(), -1, false, false, fullPath));
            }
            parentNode.add(child);
        }
    }

    private void expandNode(DefaultMutableTreeNode parent, FileSystem fs,
                            int dirBlock, String parentPath) {
        parent.removeAllChildren();
        buildChildren(parent, fs, dirBlock, parentPath);
    }

    // ====================== 数据类 ======================

    /** 树节点数据，包含完整路径 */
    static class DirNode {
        String name;
        int dirBlock;
        boolean isDir;
        boolean isRoot;
        String fullPath; // 绝对路径

        DirNode(String name, int dirBlock, boolean isDir, boolean isRoot, String fullPath) {
            this.name = name; this.dirBlock = dirBlock;
            this.isDir = isDir; this.isRoot = isRoot;
            this.fullPath = fullPath;
        }

        @Override
        public String toString() {
            if (isRoot) return name + " (root)";
            return isDir ? "[DIR]  " + name : "[FILE] " + name;
        }
    }

    /** 自定义渲染：目录蓝色文件夹图标，文件黑色文件图标 */
    static class DirTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof DirNode dn) {
                if (dn.isDir) {
                    setIcon(UIManager.getIcon("FileView.directoryIcon"));
                    if (!sel) setForeground(new Color(0, 70, 140));
                } else {
                    setIcon(UIManager.getIcon("FileView.fileIcon"));
                    if (!sel) setForeground(Color.BLACK);
                }
            }
            return this;
        }
    }
}
