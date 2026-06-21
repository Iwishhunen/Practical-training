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
import java.util.List;

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

    public MainFrame(FileSystem fs, UserManager um, Disk disk) {
        super("Multi-User Multi-Level Directory File System");
        this.fs = fs; this.userManager = um; this.disk = disk;
        initUI();
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { doExit(); }
        });
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
        hm.add(mi("About", () -> JOptionPane.showMessageDialog(this,
                "Multi-User Multi-Level Directory File System\n" +
                "128 blocks x 64 bytes = 8KB Disk, FAT allocation\n" +
                "Ref: sensnow/os-system-exp (SCAU)\nJDK 17 + Swing",
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
            msg(fs.createFile(n.trim(), userManager.getCurrentUser().getUserId())); refreshAll();
        }
    }
    public void doNewDir() {
        if (!checkLogin()) return;
        String n = JOptionPane.showInputDialog(this, "Dir name (max 8 chars):");
        if (n != null && !n.trim().isEmpty()) {
            msg(fs.mkdir(n.trim(), userManager.getCurrentUser().getUserId())); refreshAll();
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

    /** 按名称删除（供树右键菜单调用） */
    public void doDeleteByName(String name) {
        if (!checkLogin()) return;
        DirectoryEntry e = findEntry(name);
        String r = (e != null && e.isDirectory()) ? fs.rmdir(name) : fs.deleteFile(name);
        msg(r);
        refreshAll();
    }

    /** 显示文件属性（供树右键菜单调用） */
    public void showFileInfo(String name) {
        DirectoryEntry e = findEntry(name);
        if (e == null) { msg("Not found: " + name); return; }
        msg(fs.getFileInfo(name));
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
