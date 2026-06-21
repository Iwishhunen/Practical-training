package gui;

import disk.Disk;
import disk.DiskConstants;
import disk.FAT;
import fs.FileSystem;
import user.UserManager;
import javax.swing.*;

/** 主入口 */
public class FileSystemApp {

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception e) {}

        Disk disk = new Disk();
        boolean loaded = false;
        try { disk.loadFromFile(DiskConstants.DEFAULT_DISK_PATH); loaded = true; }
        catch (Exception e) { disk.format(); }

        FAT fat = new FAT(disk);
        FileSystem fs = new FileSystem(disk, fat);
        if (!loaded) fs.initFileSystem();

        UserManager userManager = new UserManager(fs);
        if (!loaded) {
            userManager.createAllUserDirectories();
            try { disk.saveToFile(DiskConstants.DEFAULT_DISK_PATH); } catch (Exception ex) {}
        }

        SwingUtilities.invokeLater(() -> {
            LoginDialog login = new LoginDialog(null, userManager);
            login.setVisible(true);
            // 同步用户 ID 给文件系统
            if (userManager.isLoggedIn()) {
                fs.setCurrentUser(userManager.getCurrentUser().getUserId() & 0xFF);
            } else {
                fs.setCurrentUser(999); // Guest：无权限，什么也看不到
            }
            MainFrame mf = new MainFrame(fs, userManager, disk);
            mf.setVisible(true);
        });
    }
}
