package gui;

import disk.Disk;
import disk.DiskConstants;
import fs.DirectoryEntry;
import fs.FileSystem;
import user.User;
import user.UserManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

/**
 * 主窗口。集成菜单栏、工具栏、文件树/表格、命令行和状态栏。
 */
public class MainFrame extends JFrame {

    final FileSystem fs;
    final UserManager userManager;
    final Disk disk;
    FileTreePanel treePanel;
    FileTablePanel tablePanel;
    JLabel statusLabel, freeSpaceLabel;
    JTextField cmdField;

    // 剪贴板（用于 Copy/Cut → Paste）
    String clipboardFile;
    String clipboardSrcPath; // 源文件所在目录的完整路径
    boolean clipboardCut;    // true=移动, false=复制

    public MainFrame(FileSystem fs, UserManager um, Disk disk) {
        super("Multi-User Multi-Level Directory File System");
        this.fs = fs; this.userManager = um; this.disk = disk;
        initUI();
        initSharedDir();
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { doExit(); }
        });
    }

    /** 初始化 /shared 共享目录（多用户可读写） */
    void initSharedDir() {
        fs.cd("/");
        var rr = fs.findInDir(fs.getCurrentDirBlock(), "shared");
        if (!rr.found) {
            fs.mkdir("shared", (byte) 0);
        }
        fs.cd("/");
    }

    void initUI() {
        setLayout(new BorderLayout());
        setJMenuBar(buildMenu());
        add(buildToolbar(), BorderLayout.NORTH);

        treePanel = new FileTreePanel(fs, this::onDirSelected, this);
        tablePanel = new FileTablePanel(fs, userManager, this);
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(treePanel), tablePanel);
        sp.setDividerLocation(250);
        add(sp, BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);
    }

    JMenuBar buildMenu() {
        JMenuBar mb = new JMenuBar();
        JMenu fm = new JMenu("File");
        fm.add(mi("New File", this::doNewFile));
        fm.add(mi("New Dir", this::doNewDir));
        fm.add(mi("Delete", this::doDelete));
        fm.addSeparator();
        fm.add(mi("Save Disk", this::doSave));
        fm.add(mi("Format", this::doFormat));
        fm.addSeparator();
        fm.add(mi("Exit", this::doExit));
        mb.add(fm);
        JMenu vm = new JMenu("View");
        vm.add(mi("Refresh", this::refreshAll));
        vm.add(mi("Disk Usage", this::showDiskUsage));
        mb.add(vm);
        JMenu um = new JMenu("User");
        um.add(mi("Login", this::doLogin));
        um.add(mi("Logout", this::doLogout));
        um.add(mi("Register", this::doRegister));
        um.add(mi("Users", this::showUsers));
        mb.add(um);
        JMenu hm = new JMenu("Help");
        hm.add(mi("System Help", this::showHelp));
        hm.add(mi("About", () -> JOptionPane.showMessageDialog(this,
                "Multi-User Multi-Level Directory File System\n" +
                "128 blocks x 64 bytes = 8KB Disk, FAT allocation\n" +
                "Reference: sensnow/os-system-exp (SCAU)\n" +
                "JDK 17 + Swing | Kaisheng Xu",
                "About", JOptionPane.INFORMATION_MESSAGE)));
        mb.add(hm);
        return mb;
    }

    JToolBar buildToolbar() {
        JToolBar tb = new JToolBar(); tb.setFloatable(false);
        tb.add(tb("Home", () -> { fs.cd("/"); refreshAll(); }));
        tb.add(tb("Up", () -> { fs.cd(".."); refreshAll(); }));
        tb.addSeparator();
        tb.add(tb("New File", this::doNewFile));
        tb.add(tb("New Dir", this::doNewDir));
        tb.add(tb("Delete", this::doDelete));
        tb.addSeparator();
        tb.add(tb("Refresh", this::refreshAll));
        tb.add(tb("Disk", this::showDiskUsage));
        return tb;
    }

    JPanel buildBottom() {
        JPanel bp = new JPanel(new BorderLayout());
        JPanel cp = new JPanel(new BorderLayout());
        cp.setBorder(BorderFactory.createTitledBorder("Command Line"));
        cmdField = new JTextField();
        cmdField.addActionListener(e -> execCmd(cmdField.getText()));
        cp.add(cmdField, BorderLayout.CENTER);
        JButton runBtn = new JButton("Run");
        runBtn.addActionListener(e -> execCmd(cmdField.getText()));
        cp.add(runBtn, BorderLayout.EAST);
        bp.add(cp, BorderLayout.CENTER);
        JPanel sb = new JPanel(new BorderLayout(5,0));
        sb.setBorder(BorderFactory.createLoweredBevelBorder());
        statusLabel = new JLabel(" Ready. Path: /");
        freeSpaceLabel = new JLabel(" Free: " + disk.getFreeBlockCount() + " blocks  ");
        sb.add(statusLabel, BorderLayout.CENTER);
        sb.add(freeSpaceLabel, BorderLayout.EAST);
        bp.add(sb, BorderLayout.SOUTH);
        return bp;
    }

    // --- actions ---
    public void doNewFile() {
        if (!checkLogin()) return;
        String n = JOptionPane.showInputDialog(this, "File name (max 8 chars):");
        if (n != null && !n.trim().isEmpty()) {
            setStatus(fs.createFile(n.trim(), userManager.getCurrentUser().getUserId()));
            refreshAll();
        }
    }
    public void doNewDir() {
        if (!checkLogin()) return;
        String n = JOptionPane.showInputDialog(this, "Dir name (max 8 chars):");
        if (n != null && !n.trim().isEmpty()) {
            setStatus(fs.mkdir(n.trim(), userManager.getCurrentUser().getUserId()));
            refreshAll();
        }
    }
    void doDelete() {
        if (!checkLogin()) return;
        DirectoryEntry sel = tablePanel.getSelectedEntry();
        String n = sel != null ? sel.getFileName() :
                JOptionPane.showInputDialog(this, "Name to delete:");
        if (n != null && !n.trim().isEmpty()) {
            doDeleteByName(n.trim());
        }
    }

    /** 按名称删除 */
    public void doDeleteByName(String name) {
        if (!checkLogin()) return;
        DirectoryEntry e = findEntry(name);
        String r = (e != null && e.isDirectory()) ? fs.rmdir(name) : fs.deleteFile(name);
        setStatus(r);
        refreshAll();
    }

    // ====== Copy / Cut / Paste ======

    /** 复制：将文件放入剪贴板 */
    public void doCopy(String name) {
        if (!checkLogin()) return;
        clipboardFile = name;
        clipboardSrcPath = fs.getCurrentPath();
        clipboardCut = false;
        JOptionPane.showMessageDialog(this,
            "Copied: " + name + "\nSource: " + clipboardSrcPath
            + "\n\nNavigate to destination, then right-click → Paste",
            "Copy", JOptionPane.INFORMATION_MESSAGE);
    }

    /** 剪切：将文件放入剪贴板 */
    public void doCut(String name) {
        if (!checkLogin()) return;
        clipboardFile = name;
        clipboardSrcPath = fs.getCurrentPath();
        clipboardCut = true;
        JOptionPane.showMessageDialog(this,
            "Cut: " + name + "\nSource: " + clipboardSrcPath
            + "\n\nNavigate to destination, then right-click → Paste",
            "Cut", JOptionPane.INFORMATION_MESSAGE);
    }

    /** 粘贴：从剪贴板复制/移动文件到当前目录 */
    public void doPaste() {
        if (!checkLogin()) return;
        String srcName = clipboardFile;
        String srcPath = clipboardSrcPath;
        boolean cut = clipboardCut;
        if (srcName == null) {
            JOptionPane.showMessageDialog(this, "Nothing to paste.\nUse Copy or Cut first.",
                "Paste", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String destPath = fs.getCurrentPath();
        byte ownerId = userManager.getCurrentUser().getUserId();

        // 1. 读源文件
        String savedPath = fs.getCurrentPath();
        fs.cd(srcPath);
        String content = fs.readFile(srcName);
        if (content.startsWith("Error")) {
            fs.cd(savedPath);
            JOptionPane.showMessageDialog(this, "Paste failed: " + content,
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 2. 写目标文件
        fs.cd(destPath);
        String r = fs.writeFile(srcName, content, ownerId);
        if (r.startsWith("Error")) {
            JOptionPane.showMessageDialog(this, r, "Error", JOptionPane.ERROR_MESSAGE);
            refreshAll(); return;
        }

        // 3. Cut → 删源文件
        if (cut) {
            fs.cd(srcPath);
            fs.deleteFile(srcName);
            clipboardFile = null;
            JOptionPane.showMessageDialog(this,
                "Moved: " + srcName + "\n" + srcPath + " → " + destPath,
                "Paste", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                "Copied: " + srcName + "\n" + srcPath + " → " + destPath,
                "Paste", JOptionPane.INFORMATION_MESSAGE);
        }
        refreshAll();
    }

    // ====== 状态栏反馈 ======
    void setStatus(String msg) {
        statusLabel.setText(" " + msg);
        // 3秒后恢复
        javax.swing.Timer timer = new javax.swing.Timer(4000, e ->
            statusLabel.setText(" Path: " + fs.getCurrentPath()
                + (userManager.isLoggedIn() ? "  |  User: "
                + userManager.getCurrentUser().getUsername() : "  |  (guest)")));
        timer.setRepeats(false);
        timer.start();
    }

    /** 显示文件属性 */
    public void showFileInfo(String name) {
        DirectoryEntry e = findEntry(name);
        if (e == null) { msg("Not found: " + name); return; }
        msg(fs.getFileInfo(name));
    }

    public void doRename(String oldName) {
        if (!checkLogin()) return;
        String newName = JOptionPane.showInputDialog(this,
                "Rename '" + oldName + "' to:", oldName);
        if (newName != null && !newName.trim().isEmpty() && !newName.trim().equals(oldName)) {
            setStatus(fs.rename(oldName, newName.trim()));
            refreshAll();
        }
    }

    /** 空闲块数 */
    public int getFreeBlocks() { return disk.getFreeBlockCount(); }

    /** 修改文件属性 */
    public void doChmod(String name) {
        if (!checkLogin()) return;
        DirectoryEntry e = findEntry(name);
        if (e == null) { msg("Not found"); return; }

        JPanel panel = new JPanel(new GridLayout(2, 1, 5, 5));
        JCheckBox readOnlyCb = new JCheckBox("Read-Only", e.isReadOnly());
        JCheckBox hiddenCb = new JCheckBox("Hidden",
                (e.getAttributes() & DiskConstants.ATTR_HIDDEN) != 0);
        panel.add(readOnlyCb);
        panel.add(hiddenCb);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Change Attributes: " + name,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            byte attrs = 0;
            if (readOnlyCb.isSelected()) attrs |= DiskConstants.ATTR_READONLY;
            if (hiddenCb.isSelected()) attrs |= DiskConstants.ATTR_HIDDEN;
            msg(fs.setAttribute(name, attrs));
            refreshAll();
        }
    }

    public void openFile(String name) {
        DirectoryEntry e = findEntry(name);
        if (e != null && e.isDirectory()) { fs.cd(name); refreshAll(); return; }
        String content = e != null ? fs.readFile(name) : "";
        new TextEditorDialog(this, fs, userManager, name, content, this::refreshAll).setVisible(true);
    }

    void doSave() {
        try { disk.saveToFile(DiskConstants.DEFAULT_DISK_PATH);
            msg("Saved to " + DiskConstants.DEFAULT_DISK_PATH); }
        catch (IOException ex) { msg("Save error: " + ex.getMessage()); }
    }
    void doFormat() {
        if (!fs.isRoot()) { setStatus("Error: only root can format disk"); return; }
        if (JOptionPane.showConfirmDialog(this, "Format? All data lost!",
                "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            fs.initFileSystem(); userManager.createAllUserDirectories();
            refreshAll(); msg("Disk formatted.");
        }
    }
    void doExit() {
        if (disk.isDirty() && JOptionPane.showConfirmDialog(this,
                "Save before exit?", "Exit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
            doSave();
        dispose(); System.exit(0);
    }
    void doLogin() {
        new LoginDialog(this, userManager).setVisible(true);
        refreshAll();
    }
    void doLogout() {
        if (!userManager.isLoggedIn()) {
            msg("Not logged in.");
            return;
        }
        String username = userManager.getCurrentUser().getUsername();
        userManager.logout();
        fs.setCurrentUser(999); // Guest: 无权限
        fs.cd("/");
        refreshAll();

        // 隐藏主窗口，弹出登录对话框
        setVisible(false);
        LoginDialog login = new LoginDialog(this, userManager);
        login.setVisible(true);
        setVisible(true);
        refreshAll();

        if (userManager.isLoggedIn()) {
            msg("Welcome back, " + userManager.getCurrentUser().getUsername() + "!");
        } else {
            msg("You are browsing as guest.");
        }
    }
    void doRegister() {
        String u = JOptionPane.showInputDialog(this, "Username (2-8 chars):");
        if (u != null) { String p = JOptionPane.showInputDialog(this, "Password (min 4):");
            if (p != null) msg(userManager.register(u.trim(), p)); }
    }
    void showUsers() { msg(userManager.getUsersInfo()); }
    void showDiskUsage() { new DiskUsagePanel(this, fs, disk).setVisible(true); }

    void showHelp() {
        String help = """
            ╔══════════════════════════════════════════════╗
            ║  多用户多级目录文件系统 — 系统说明          ║
            ╚══════════════════════════════════════════════╝

            ┌─ 系统概述 ──────────────────────────┐
            │ 模拟操作系统的文件管理功能             │
            │ 支持多用户、多级目录、GUI 图形界面      │
            │ 磁盘: 128块 x 64字节 = 8KB           │
            │ 分配: FAT表（显式链接）                │
            │ 目录项: 16字节（8B名+类型+属性+大小）   │
            └────────────────────────────────────┘

            ┌─ 用户命令 ──────────────────────────┐
            │ login   <user> <pass>   用户登录     │
            │ logout                  用户登出     │
            │ register <user> <pass>  注册新用户   │
            └────────────────────────────────────┘

            ┌─ 目录命令 ──────────────────────────┐
            │ mkdir <name>            创建目录     │
            │ cd   <path>             切换目录     │
            │ dir / ls                列出目录内容 │
            │ rmdir <name>            删除空目录   │
            └────────────────────────────────────┘

            ┌─ 文件命令 ──────────────────────────┐
            │ create <name>           创建文件     │
            │ open   <name>           打开文件     │
            │ read   <name>           读取内容     │
            │ write  <name>           写入文件     │
            │ close  <name>           关闭文件     │
            │ delete <name>           删除文件     │
            │ rename <old> <new>      重命名       │
            └────────────────────────────────────┘

            ┌─ 系统命令 ──────────────────────────┐
            │ format                  格式化磁盘   │
            │ save                    保存到文件   │
            │ help                    显示帮助     │
            │ exit / quit             退出程序     │
            └────────────────────────────────────┘

            ┌─ 键盘快捷键 ───────────────────────┐
            │ Enter   打开文件/进入目录            │
            │ Delete  删除选中项                  │
            │ F2      重命名                      │
            │ Ctrl+S  保存文件（编辑器中）         │
            └────────────────────────────────────┘

            ┌─ 默认用户 ─────────────────────────┐
            │ root   / root123    (管理员)        │
            │ admin  / admin123   (管理员)        │
            │ user1  / 111111     (普通用户)      │
            └────────────────────────────────────┘

            ┌─ 文件属性 ─────────────────────────┐
            │ R = 只读 (Read-Only)               │
            │ H = 隐藏 (Hidden)                  │
            │ 可通过右键 → Change Attributes 修改 │
            └────────────────────────────────────┘
            """;
        JTextArea ta = new JTextArea(help);
        ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
        ta.setEditable(false);
        ta.setBackground(new Color(250, 250, 250));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(580, 500));
        JOptionPane.showMessageDialog(this, sp,
                "System Help", JOptionPane.INFORMATION_MESSAGE);
    }

    void execCmd(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) return;
        cmd = cmd.trim(); cmdField.setText("");
        String[] p = cmd.split("\\s+", 2);
        String op = p[0].toLowerCase(), arg = p.length > 1 ? p[1] : "";
        User cu = userManager.getCurrentUser();
        switch (op) {
            case "login": { doLogin(); return; }
            case "logout": { doLogout(); return; }
            case "register": { doRegister(); return; }
            case "mkdir": if (!checkLogin()) return;
                msg(fs.mkdir(arg, cu.getUserId())); refreshAll(); return;
            case "cd": msg(fs.cd(arg)); refreshAll(); return;
            case "dir": case "ls":
                StringBuilder sb = new StringBuilder("Dir of " + fs.getCurrentPath() + ":\n");
                for (DirectoryEntry e : fs.listDir()) sb.append(e).append("\n");
                msg(sb.toString()); return;
            case "create": if (!checkLogin()) return;
                msg(fs.createFile(arg, cu.getUserId())); refreshAll(); return;
            case "open": if (arg.isEmpty()) { msg("Usage: open <name>"); return; }
                openFile(arg); return;
            case "read": msg(fs.readFile(arg)); return;
            case "write": if (!checkLogin()) return;
                if (arg.isEmpty()) { msg("Usage: write <name>"); return; }
                new TextEditorDialog(this, fs, userManager, arg, "", this::refreshAll).setVisible(true);
                return;
            case "delete": if (!checkLogin()) return;
                DirectoryEntry de = findEntry(arg);
                msg(de != null && de.isDirectory() ? fs.rmdir(arg) : fs.deleteFile(arg));
                refreshAll(); return;
            case "format": doFormat(); return;
            case "save": doSave(); return;
            case "exit": case "quit": doExit(); return;
            case "help":
                msg("Commands: login logout register mkdir cd dir create open read write delete format save exit"); return;
            default: msg("Unknown: " + op); return;
        }
    }

    boolean checkLogin() {
        if (!userManager.isLoggedIn()) {
            if (JOptionPane.showConfirmDialog(this, "Login required. Login now?",
                    "Login", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) doLogin();
            return userManager.isLoggedIn();
        }
        return true;
    }

    DirectoryEntry findEntry(String name) {
        var rr = fs.findInDir(fs.getCurrentDirBlock(), name);
        return rr.found ? rr.entry : null;
    }

    void onDirSelected(String path) {
        fs.cd(path);
        refreshAll();
    }

    public void refreshAll() {
        treePanel.refresh(fs); tablePanel.refresh();
        setTitle("Multi-User File System  |  UID=" + fs.getCurrentUserId()
                + (fs.isRoot() ? " (root)" : fs.isAdmin() ? " (admin)" : " (user)"));
        User cu = userManager.getCurrentUser();
        statusLabel.setText(" Path: " + fs.getCurrentPath()
                + (cu != null ? "  |  User: " + cu.getUsername() : "  |  (guest)"));
        freeSpaceLabel.setText(" Free: " + disk.getFreeBlockCount() + " blocks (" +
                disk.getFreeBlockCount() * DiskConstants.BLOCK_SIZE + "B)  ");
    }

    void msg(String s) { JOptionPane.showMessageDialog(this, s); }

    JMenuItem mi(String t, Runnable r) {
        JMenuItem i = new JMenuItem(t); i.addActionListener(e -> r.run()); return i;
    }
    JButton tb(String t, Runnable r) {
        JButton b = new JButton(t); b.addActionListener(e -> r.run()); return b;
    }
}
