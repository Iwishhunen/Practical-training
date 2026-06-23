package user;

import disk.DiskConstants;
import fs.FileSystem;
import java.io.*;
import java.util.*;

/**
 * 用户管理器。用户数据持久化到 users.dat。
 */
public class UserManager {

    private static final String USERS_FILE = "users.dat";

    private final FileSystem fs;
    private final Map<String, User> userCache;
    private User currentUser;
    private byte nextUserId;

    public UserManager(FileSystem fs) {
        this.fs = fs;
        this.userCache = new LinkedHashMap<>();
        this.currentUser = null;

        if (!loadUsers()) {
            addUserInternal("root", "root123", (byte) DiskConstants.ROOT_USER_ID, "/");
            addUserInternal("admin", "admin123", (byte) 1);
            addUserInternal("user1", "111111", (byte) 2);
            nextUserId = 3;
            saveUsers();
        }
    }

    private boolean loadUsers() {
        File f = new File(USERS_FILE);
        if (!f.exists()) return false;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            byte maxId = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|");
                if (p.length < 4) continue;
                byte id = Byte.parseByte(p[0]);
                userCache.put(p[1], new User(id, p[1], p[2], p[3]));
                if (id > maxId) maxId = id;
            }
            nextUserId = (byte) (maxId + 1);
            return !userCache.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void saveUsers() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(USERS_FILE))) {
            pw.println("# userId|username|passwordHash|homeDir");
            for (User u : userCache.values()) {
                pw.println((u.getUserId() & 0xFF) + "|" + u.getUsername()
                        + "|" + u.getPasswordHash() + "|" + u.getHomeDirectory());
            }
        } catch (IOException e) {
            System.err.println("Failed to save users: " + e.getMessage());
        }
    }

    public String login(String username, String password) {
        User user = userCache.get(username);
        if (user == null) return "Error: user not found";
        if (!user.checkPassword(password)) return "Error: wrong password";
        currentUser = user;
        ensureUserHome(user);
        fs.setCurrentUser(user.getUserId() & 0xFF);
        if (user.getUserId() != 0) {
            fs.cd("/"); fs.cd("home"); fs.cd(user.getUsername());
        }
        return "Login ok. Welcome, " + username + "!";
    }

    public String register(String username, String password) {
        if (username == null || username.length() < 2 || username.length() > 8)
            return "Error: username must be 2-8 chars";
        if (password == null || password.length() < 4)
            return "Error: password at least 4 chars";
        if (userCache.containsKey(username))
            return "Error: username exists";
        if (nextUserId > 127) return "Error: max users";

        addUserInternal(username, password, nextUserId);
        nextUserId++;
        saveUsers();
        return "Register ok. Please login.";
    }

    public void logout() { currentUser = null; }
    public User getCurrentUser() { return currentUser; }
    public boolean isLoggedIn() { return currentUser != null; }
    public Collection<User> getAllUsers() { return userCache.values(); }

    /** 为所有用户创建主目录（文件系统初始化后调用） */
    public String createAllUserDirectories() {
        StringBuilder sb = new StringBuilder();
        int saved = fs.getCurrentDirBlock();
        String savedPath = fs.getCurrentPath();

        fs.cd("/");
        // 确保 /home 目录存在
        var rr = fs.findInDir(DiskConstants.ROOT_DIR_BLOCK, "home");
        if (!rr.found) {
            String r = fs.mkdir("home", (byte) 0);
            sb.append(r).append("\n");
        }

        // 找到 /home 的起始块
        rr = fs.findInDir(DiskConstants.ROOT_DIR_BLOCK, "home");
        if (rr.found && rr.entry.isDirectory()) {
            int homeBlock = rr.entry.getStartBlock() & 0xFF;
            // 为每个用户创建主目录
            for (User user : userCache.values()) {
                if (user.getUserId() == 0) continue; // root 不需要 home 目录
                var ur = fs.findInDir(homeBlock, user.getUsername());
                if (!ur.found) {
                    fs.cd("home");
                    String r = fs.mkdir(user.getUsername(), user.getUserId());
                    sb.append(r).append("\n");
                }
            }
        }

        // 恢复当前目录
        fs.cd("/");
        return sb.toString();
    }

    private void ensureUserHome(User user) {
        if (user.getUserId() == 0) return; // root 不需要 home
        // 确保 /home 存在
        var rr = fs.findInDir(DiskConstants.ROOT_DIR_BLOCK, "home");
        if (!rr.found) {
            fs.cd("/");
            fs.mkdir("home", (byte) 0);
        }
        // 确保 /home/<username> 存在
        rr = fs.findInDir(DiskConstants.ROOT_DIR_BLOCK, "home");
        if (rr.found && rr.entry.isDirectory()) {
            int homeBlock = rr.entry.getStartBlock() & 0xFF;
            var ur = fs.findInDir(homeBlock, user.getUsername());
            if (!ur.found) {
                fs.cd("home");
                fs.mkdir(user.getUsername(), user.getUserId());
            }
        }
    }

    private void addUserInternal(String username, String password, byte userId) {
        User user = new User(userId, username, password);
        userCache.put(username, user);
    }
    private void addUserInternal(String username, String password, byte userId, String homeDir) {
        User user = new User(userId, username, User.hashPassword(password), homeDir);
        userCache.put(username, user);
    }

    public String getUsersInfo() {
        StringBuilder sb = new StringBuilder();
        for (User u : userCache.values()) {
            sb.append("[").append(u.getUserId() & 0xFF).append("] ")
              .append(u.getUsername()).append(" → ").append(u.getHomeDirectory()).append("\n");
        }
        return sb.toString();
    }

    public User getUserByName(String username) { return userCache.get(username); }
}
