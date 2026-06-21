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

/** 左侧目录树面板（JTree），仿 Windows 资源管理器风格 */
public class FileTreePanel extends JPanel {

    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private final Consumer<String> onDirSelected;
    private final MainFrame mainFrame;
    private FileSystem fs;
    private volatile DefaultMutableTreeNode rightClickedNode;

    public FileTreePanel(FileSystem fs, Consumer<String> onDirSelected, MainFrame mainFrame) {
        super(new BorderLayout());
        this.fs = fs;
        this.onDirSelected = onDirSelected;
        this.mainFrame = mainFrame;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Folders"),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));

        rootNode = new DefaultMutableTreeNode(new DirNode("/",
                DiskConstants.ROOT_DIR_BLOCK, true, true, "/"));
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tree.setCellRenderer(new DirTreeCellRenderer());

        // 鼠标事件：左键选中/双击展开，右键选中节点
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    tree.setSelectionPath(path);
                    rightClickedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
                } else {
                    tree.setSelectionPath(path);
                    rightClickedNode = null;
                }
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node.getUserObject() instanceof DirNode dn) {
                        if (dn.isDir) {
                            expandNode(node, fs, dn.dirBlock, dn.fullPath);
                            onDirSelected.accept(dn.fullPath);
                        } else {
                            navigateToParentDir(node);
                            mainFrame.openFile(dn.name);
                        }
                    }
                }
            }
        });

        // 左键选中非右键时导航
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = getSelectedNode();
            if (node == null || rightClickedNode != null) return;
            if (node.getUserObject() instanceof DirNode dn && dn.isDir) {
                expandNode(node, fs, dn.dirBlock, dn.fullPath);
                onDirSelected.accept(dn.fullPath);
            }
        });

        buildPopupMenu();
        add(new JScrollPane(tree), BorderLayout.CENTER);
        refresh(fs);
    }

    // ===== 右键菜单 =====
    private void buildPopupMenu() {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem newFile = new JMenuItem("New File");
        newFile.addActionListener(e -> doInRightClickedDir(() -> mainFrame.doNewFile()));
        popup.add(newFile);

        JMenuItem newDir = new JMenuItem("New Folder");
        newDir.addActionListener(e -> doInRightClickedDir(() -> mainFrame.doNewDir()));
        popup.add(newDir);

        popup.addSeparator();

        JMenuItem copy = new JMenuItem("Copy");
        copy.addActionListener(e -> {
            DirNode dn = getRightClickedDirNode();
            if (dn == null || dn.isRoot || dn.isDir) return;
            navigateToParentOfRightClicked();
            mainFrame.doCopy(dn.name);
        });
        popup.add(copy);

        JMenuItem cut = new JMenuItem("Cut");
        cut.addActionListener(e -> {
            DirNode dn = getRightClickedDirNode();
            if (dn == null || dn.isRoot || dn.isDir) return;
            navigateToParentOfRightClicked();
            mainFrame.doCut(dn.name);
        });
        popup.add(cut);

        JMenuItem paste = new JMenuItem("Paste");
        paste.addActionListener(e -> {
            DirNode dn = getRightClickedDirNode();
            if (dn == null) return;
            if (!dn.isDir) navigateToParentOfRightClicked();
            else onDirSelected.accept(dn.fullPath);
            mainFrame.doPaste();
        });
        popup.add(paste);

        popup.addSeparator();

        JMenuItem rename = new JMenuItem("Rename");
        rename.addActionListener(e -> {
            DirNode dn = getRightClickedDirNode();
            if (dn == null || dn.isRoot) return;
            navigateToParentOfRightClicked();
            mainFrame.doRename(dn.name);
        });
        popup.add(rename);

        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> {
            DirNode dn = getRightClickedDirNode();
            if (dn == null || dn.isRoot) return;
            navigateToParentOfRightClicked();
            mainFrame.doDeleteByName(dn.name);
        });
        popup.add(delete);

        popup.addSeparator();

        JMenuItem chmod = new JMenuItem("Change Attributes");
        chmod.addActionListener(e -> {
            DirNode dn = getRightClickedDirNode();
            if (dn == null || dn.isRoot) return;
            navigateToParentOfRightClicked();
            mainFrame.doChmod(dn.name);
        });
        popup.add(chmod);

        JMenuItem props = new JMenuItem("Properties");
        props.addActionListener(e -> {
            DirNode dn = getRightClickedDirNode();
            if (dn == null) return;
            if (dn.isRoot) {
                JOptionPane.showMessageDialog(this,
                        "Root Directory\nBlock: " + DiskConstants.ROOT_DIR_BLOCK
                        + "\nTotal blocks: " + DiskConstants.BLOCK_COUNT
                        + "\nFree blocks: " + mainFrame.getFreeBlocks(),
                        "Properties", JOptionPane.INFORMATION_MESSAGE);
            } else {
                navigateToParentOfRightClicked();
                mainFrame.showFileInfo(dn.name);
            }
        });
        popup.add(props);

        tree.setComponentPopupMenu(popup);
    }

    private void doInRightClickedDir(Runnable action) {
        DirNode dn = getRightClickedDirNode();
        if (dn == null) return;
        if (!dn.isDir) navigateToParentOfRightClicked();
        else onDirSelected.accept(dn.fullPath);
        action.run();
    }

    // ===== 导航 =====
    private DefaultMutableTreeNode getSelectedNode() {
        TreePath p = tree.getSelectionPath();
        return p != null ? (DefaultMutableTreeNode) p.getLastPathComponent() : null;
    }

    private DirNode getRightClickedDirNode() {
        DefaultMutableTreeNode n = rightClickedNode != null ? rightClickedNode : getSelectedNode();
        return (n != null && n.getUserObject() instanceof DirNode dn) ? dn : null;
    }

    private void navigateToParentOfRightClicked() {
        DefaultMutableTreeNode n = rightClickedNode != null ? rightClickedNode : getSelectedNode();
        if (n != null) navigateToParentDir(n);
    }

    private void navigateToParentDir(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode p = (DefaultMutableTreeNode) node.getParent();
        if (p != null && p.getUserObject() instanceof DirNode pdn)
            onDirSelected.accept(pdn.fullPath);
    }

    // ===== refresh =====
    public void refresh(FileSystem fs) {
        this.fs = fs;
        rootNode.removeAllChildren();
        buildChildren(rootNode, fs, DiskConstants.ROOT_DIR_BLOCK, "/");
        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void buildChildren(DefaultMutableTreeNode parent, FileSystem fs,
                               int dirBlock, String parentPath) {
        int uid = fs.getCurrentUserId();
        for (DirectoryEntry e : fs.listDir(dirBlock, parentPath)) {
            // UI 层强制权限过滤
            if (uid > 1) {
                int owner = e.getOwnerId() & 0xFF;
                boolean allow = (owner == uid)
                        || "shared".equals(e.getFileName())
                        || ("home".equals(e.getFileName()) && "/".equals(parentPath));
                if (!allow) continue;
            }
            String full = parentPath.equals("/") ? "/" + e.getFileName()
                    : parentPath + "/" + e.getFileName();
            DefaultMutableTreeNode child;
            if (e.isDirectory()) {
                child = new DefaultMutableTreeNode(new DirNode(e.getFileName(),
                        e.getStartBlock() & 0xFF, true, false, full));
                buildChildren(child, fs, e.getStartBlock() & 0xFF, full);
            } else {
                child = new DefaultMutableTreeNode(new DirNode(e.getFileName(),
                        -1, false, false, full));
            }
            parent.add(child);
        }
    }

    private void expandNode(DefaultMutableTreeNode parent, FileSystem fs,
                            int dirBlock, String parentPath) {
        parent.removeAllChildren();
        buildChildren(parent, fs, dirBlock, parentPath);
    }

    // ===== 数据类 =====
    static class DirNode {
        String name; int dirBlock; boolean isDir, isRoot; String fullPath;
        DirNode(String n, int b, boolean d, boolean r, String f) {
            name = n; dirBlock = b; isDir = d; isRoot = r; fullPath = f;
        }
        @Override public String toString() { return name; }
    }

    static class DirTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean exp, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, focus);
            if (value instanceof DefaultMutableTreeNode n
                    && n.getUserObject() instanceof DirNode dn) {
                setIcon(dn.isDir ? (exp ? UIManager.getIcon("Tree.openIcon")
                        : UIManager.getIcon("Tree.closedIcon"))
                        : UIManager.getIcon("Tree.leafIcon"));
            }
            return this;
        }
    }
}
