package fs;

import disk.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 文件系统核心门面。
 * 提供文件/目录的创建、删除、打开、关闭、读写操作。
 */
public class FileSystem {

    private final Disk disk;
    private final FAT fat;
    private final PathResolver resolver;
    private final Map<String, OpenFileEntry> openFiles;
    private int currentDirBlock;
    private String currentPath;
    private int currentUserId = 0; // 0=root, 1=admin, >=2=普通

    public FileSystem(Disk disk, FAT fat) {
        this.disk = disk;
        this.fat = fat;
        this.resolver = new PathResolver(disk, fat);
        this.openFiles = new LinkedHashMap<>();
        this.currentDirBlock = DiskConstants.ROOT_DIR_BLOCK;
        this.currentPath = "/";
    }

    /** 格式化并初始化基础目录结构 */
    public void initFileSystem() {
        disk.format();
        currentDirBlock = DiskConstants.ROOT_DIR_BLOCK;
        currentPath = "/";
        openFiles.clear();
        mkdir("home", (byte) 0);
        mkdir("shared", (byte) 0);
    }

    public boolean loadFromFile(String path) {
        try { disk.loadFromFile(path); currentDirBlock = DiskConstants.ROOT_DIR_BLOCK;
              currentPath = "/"; openFiles.clear(); return true; }
        catch (IOException e) { return false; }
    }

    public boolean saveToFile(String path) {
        try { disk.saveToFile(path); return true; }
        catch (IOException e) { return false; }
    }

    // ========== 目录操作 ==========

    public String mkdir(String name, byte ownerId) {
        if (name == null || name.isEmpty()) return "Error: empty name";
        if (name.length() > DiskConstants.MAX_FILENAME_LEN) return "Error: name too long";
        if (resolver.findInDirectory(currentDirBlock, name).found) return "Error: already exists";
        int blk = fat.allocateBlock();
        if (blk < 0) return "Error: disk full";
        DirectoryEntry e = new DirectoryEntry(name, DiskConstants.TYPE_DIRECTORY,
                (byte) 0, ownerId, (byte) blk, 0);
        if (!resolver.writeEntry(currentDirBlock, e)) { fat.freeChain(blk); return "Error: dir full"; }
        return "mkdir ok: " + name;
    }

    public String rmdir(String name) {
        PathResolver.ResolveResult rr = resolver.findInDirectory(currentDirBlock, name);
        if (!rr.found) return "Error: not found";
        if (!rr.entry.isDirectory()) return "Error: not a directory";
        if (!canModify(rr.entry)) return "Error: permission denied";
        int dblk = rr.entry.getStartBlock() & 0xFF;
        if (!resolver.listEntries(dblk).isEmpty()) return "Error: directory not empty";
        fat.freeChain(dblk);
        resolver.removeEntry(rr.entryBlock, rr.entryIndex);
        return "rmdir ok";
    }

    public List<DirectoryEntry> listDir() {
        List<DirectoryEntry> all = resolver.listEntries(currentDirBlock);
        if (currentUserId <= 1) return all;
        if (currentPath.startsWith("/shared")) return all; // 共享目录全可见
        java.util.List<DirectoryEntry> filtered = new java.util.ArrayList<>();
        for (DirectoryEntry e : all) {
            int owner = e.getOwnerId() & 0xFF;
            if (owner == currentUserId) filtered.add(e);
            else if ("shared".equals(e.getFileName())) filtered.add(e);
            else if ("home".equals(e.getFileName()) && "/".equals(currentPath)) filtered.add(e);
        }
        return filtered;
    }

    public String cd(String path) {
        if (path == null || path.isEmpty()) return "Error: empty path";
        if (path.equals("/")) {
            currentDirBlock = DiskConstants.ROOT_DIR_BLOCK; currentPath = "/"; return "cd: /";
        }
        PathResolver.ResolveResult rr;
        if (path.startsWith("/")) {
            rr = resolver.resolve(path, currentDirBlock);
        } else {
            rr = resolver.findInDirectory(currentDirBlock, path);
        }
        if (rr.found && rr.entry.isDirectory()) {
            String newPath = path.startsWith("/") ? path
                    : (currentPath.equals("/") ? "/" + path : currentPath + "/" + path);
            // 权限检查：普通用户不能进入其他人的主目录
            if (currentUserId > 1 && newPath.startsWith("/home/")) {
                int owner = rr.entry.getOwnerId() & 0xFF;
                if (owner != currentUserId && owner != 0) {
                    return "Error: access denied";
                }
            }
            currentDirBlock = rr.entry.getStartBlock() & 0xFF;
            currentPath = newPath;
            return "cd: " + currentPath;
        }
        return "Error: directory not found";
    }

    public String getCurrentPath() { return currentPath; }
    public int getCurrentDirBlock() { return currentDirBlock; }

    // ========== 文件操作 ==========

    public String createFile(String name, byte ownerId) {
        if (name == null || name.isEmpty()) return "Error: empty name";
        if (name.length() > DiskConstants.MAX_FILENAME_LEN) return "Error: name too long";
        if (resolver.findInDirectory(currentDirBlock, name).found) return "Error: already exists";
        int blk = fat.allocateBlock();
        if (blk < 0) return "Error: disk full";
        DirectoryEntry e = new DirectoryEntry(name, DiskConstants.TYPE_FILE,
                (byte) 0, ownerId, (byte) blk, 0);
        if (!resolver.writeEntry(currentDirBlock, e)) { fat.freeChain(blk); return "Error: dir full"; }
        return "create ok: " + name;
    }

    public String deleteFile(String name) {
        PathResolver.ResolveResult rr = resolver.findInDirectory(currentDirBlock, name);
        if (!rr.found) return "Error: not found";
        if (!rr.entry.isFile()) return "Error: not a file";
        if (!canModify(rr.entry)) return "Error: permission denied";
        openFiles.remove(buildPath(name));
        fat.freeChain(rr.entry.getStartBlock() & 0xFF);
        resolver.removeEntry(rr.entryBlock, rr.entryIndex);
        return "delete ok";
    }

    public OpenFileEntry openFile(String name, int mode) {
        PathResolver.ResolveResult rr = resolver.findInDirectory(currentDirBlock, name);
        if (!rr.found || !rr.entry.isFile()) return null;
        String fp = buildPath(name);
        openFiles.remove(fp);
        OpenFileEntry of = new OpenFileEntry(rr.entry, mode);
        openFiles.put(fp, of);
        return of;
    }

    public String closeFile(String name) {
        OpenFileEntry of = openFiles.remove(buildPath(name));
        if (of == null) return "Error: file not open";
        of.close();
        return "close ok";
    }

    public String readFile(String name) {
        PathResolver.ResolveResult rr = resolver.findInDirectory(currentDirBlock, name);
        if (!rr.found || !rr.entry.isFile()) return "Error: file not found";
        int start = rr.entry.getStartBlock() & 0xFF;
        int size = rr.entry.getFileSize();
        if (size == 0) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int cur = start, safety = 0, total = 0;
        while (cur != DiskConstants.FAT_EOF && cur != DiskConstants.FAT_FREE
                && safety < DiskConstants.BLOCK_COUNT && total < size) {
            byte[] blk = disk.readBlock(cur);
            int n = Math.min(DiskConstants.BLOCK_SIZE, size - total);
            bos.write(blk, 0, n); total += n;
            int nx = disk.getFATEntry(cur) & 0xFF;
            if (nx == DiskConstants.FAT_EOF || nx == DiskConstants.FAT_FREE) break;
            cur = nx; safety++;
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public String writeFile(String name, String content, byte ownerId) {
        PathResolver.ResolveResult rr = resolver.findInDirectory(currentDirBlock, name);
        if (!rr.found) {
            String r = createFile(name, ownerId);
            if (!r.startsWith("create ok")) return r;
            rr = resolver.findInDirectory(currentDirBlock, name);
            if (!rr.found) return "Error: create failed";
        }
        if (!rr.entry.isFile()) return "Error: not a file";
        if (!canModify(rr.entry)) return "Error: permission denied";
        if (rr.entry.isReadOnly()) return "Error: read-only file";
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        int sblk = rr.entry.getStartBlock() & 0xFF;
        fat.freeChain(sblk);
        if (data.length == 0) {
            int nb = fat.allocateBlock(); if (nb < 0) return "Error: disk full";
            rr.entry.setStartBlock((byte) nb); rr.entry.setFileSize(0);
            updateEntry(rr); return "write ok (empty)";
        }
        int need = (data.length + DiskConstants.BLOCK_SIZE - 1) / DiskConstants.BLOCK_SIZE;
        int first = fat.allocateBlock(); if (first < 0) return "Error: disk full";
        int cur = first, w = 0;
        for (int b = 0; b < need; b++) {
            byte[] blk = new byte[DiskConstants.BLOCK_SIZE];
            int tw = Math.min(DiskConstants.BLOCK_SIZE, data.length - w);
            System.arraycopy(data, w, blk, 0, tw); disk.writeBlock(cur, blk); w += tw;
            if (b < need - 1) { int nx = fat.allocateBlock();
                if (nx < 0) return "Error: disk full during write";
                disk.setFATEntry(cur, nx); cur = nx; }
        }
        rr.entry.setStartBlock((byte) first); rr.entry.setFileSize(data.length);
        updateEntry(rr);
        return "write ok, " + data.length + " bytes";
    }

    public String appendFile(String name, String content, byte ownerId) {
        String exist = readFile(name);
        if (exist.startsWith("Error")) {
            if (exist.contains("not found")) return writeFile(name, content, ownerId);
            return exist;
        }
        return writeFile(name, exist + content, ownerId);
    }

    /** 复制文件到目标目录 */
    public String copyFile(String srcName, String destDirPath, byte ownerId) {
        // 读源文件
        PathResolver.ResolveResult srcRr = resolver.findInDirectory(currentDirBlock, srcName);
        if (!srcRr.found || !srcRr.entry.isFile())
            return "Error: source not found or not a file";

        // 保存当前目录
        int savedBlock = currentDirBlock;
        String savedPath = currentPath;

        // 切换到目标目录
        String cdResult = cd(destDirPath);
        if (cdResult.startsWith("Error")) return "Error: dest dir not found";

        // 检查目标目录中是否有同名文件
        PathResolver.ResolveResult destRr = resolver.findInDirectory(currentDirBlock,
                srcRr.entry.getFileName());
        if (destRr.found) {
            // 恢复并返回
            currentDirBlock = savedBlock;
            currentPath = savedPath;
            return "Error: file already exists in destination";
        }

        // 读源内容
        String content = readFileAt(srcRr.entry);

        // 恢复当前目录到源
        currentDirBlock = savedBlock;
        currentPath = savedPath;

        // 在目标目录创建
        String createResult = createFileAt(destDirPath, srcRr.entry.getFileName(),
                content, ownerId);
        return createResult;
    }

    /** 移动文件到目标目录 */
    public String moveFile(String srcName, String destDirPath, byte ownerId) {
        String copyResult = copyFile(srcName, destDirPath, ownerId);
        if (copyResult.startsWith("Error")) return copyResult;
        // 删除源文件
        deleteFile(srcName);
        return "move ok: " + srcName + " → " + destDirPath;
    }

    /** 在指定目录下创建文件 */
    private String createFileAt(String dirPath, String name, String content, byte ownerId) {
        int savedBlock = currentDirBlock;
        String savedPath = currentPath;
        if (!cd(dirPath).startsWith("Error")) {
            String r = createFile(name, ownerId);
            if (r.startsWith("create ok")) {
                writeFile(name, content, ownerId);
            }
            currentDirBlock = savedBlock;
            currentPath = savedPath;
            return "copy ok: " + name + " → " + dirPath;
        }
        currentDirBlock = savedBlock;
        currentPath = savedPath;
        return "Error: dest not found";
    }

    /** 从目录项直接读取文件内容（不依赖当前目录） */
    private String readFileAt(DirectoryEntry entry) {
        int startBlock = entry.getStartBlock() & 0xFF;
        int size = entry.getFileSize();
        if (size == 0) return "";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        int cur = startBlock, safety = 0, total = 0;
        while (cur != DiskConstants.FAT_EOF && cur != DiskConstants.FAT_FREE
                && safety < DiskConstants.BLOCK_COUNT && total < size) {
            byte[] blk = disk.readBlock(cur);
            int n = Math.min(DiskConstants.BLOCK_SIZE, size - total);
            bos.write(blk, 0, n); total += n;
            int nx = disk.getFATEntry(cur) & 0xFF;
            if (nx == DiskConstants.FAT_EOF || nx == DiskConstants.FAT_FREE) break;
            cur = nx; safety++;
        }
        return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    public String getFileInfo(String name) {
        PathResolver.ResolveResult rr = resolver.findInDirectory(currentDirBlock, name);
        if (!rr.found) return "Error: not found";
        DirectoryEntry e = rr.entry;
        return "Name: " + e.getFileName() + "\nType: "
                + (e.isDirectory() ? "Directory" : "File") + "\nSize: "
                + e.getFileSize() + " bytes\nOwner ID: " + (e.getOwnerId() & 0xFF)
                + "\nRead-only: " + (e.isReadOnly() ? "Yes" : "No")
                + "\nStart block: " + (e.getStartBlock() & 0xFF);
    }

    /** 修改文件属性 */
    public String setAttribute(String name, byte attrs) {
        PathResolver.ResolveResult rr = resolver.findInDirectory(currentDirBlock, name);
        if (!rr.found) return "Error: not found";
        if (!canModify(rr.entry)) return "Error: permission denied";
        rr.entry.setAttributes(attrs);
        byte[] blk = disk.readBlock(rr.entryBlock);
        byte[] eb = rr.entry.toBytes();
        System.arraycopy(eb, 0, blk, rr.entryIndex * DiskConstants.DIR_ENTRY_SIZE,
                DiskConstants.DIR_ENTRY_SIZE);
        disk.writeBlock(rr.entryBlock, blk);
        return "attr ok: " + name;
    }

    /** 重命名 */
    public String rename(String oldName, String newName) {
        if (newName == null || newName.isEmpty()
                || newName.length() > DiskConstants.MAX_FILENAME_LEN)
            return "Error: invalid new name";
        PathResolver.ResolveResult rr = resolver.findInDirectory(currentDirBlock, oldName);
        if (!rr.found) return "Error: not found";
        if (!canModify(rr.entry)) return "Error: permission denied";
        if (resolver.findInDirectory(currentDirBlock, newName).found)
            return "Error: name exists";
        rr.entry.setFileName(newName);
        byte[] blk = disk.readBlock(rr.entryBlock);
        byte[] eb = rr.entry.toBytes();
        System.arraycopy(eb, 0, blk, rr.entryIndex * DiskConstants.DIR_ENTRY_SIZE,
                DiskConstants.DIR_ENTRY_SIZE);
        disk.writeBlock(rr.entryBlock, blk);
        return "rename ok: " + oldName + " → " + newName;
    }

    public int getFreeBlockCount() { return disk.getFreeBlockCount(); }
    public int getTotalBlockCount() { return DiskConstants.BLOCK_COUNT; }

    /** 设置当前用户 */
    public void setCurrentUser(int userId) { this.currentUserId = userId; }
    public int getCurrentUserId() { return currentUserId; }
    public boolean isRoot() { return currentUserId == 0; }
    public boolean isAdmin() { return currentUserId <= 1; }

    /** 检查是否有权修改指定条目：root 可改一切，admin 不可改 root 的文件 */
    private boolean canModify(DirectoryEntry entry) {
        if (currentUserId == 0) return true; // root 无敌
        int owner = entry.getOwnerId() & 0xFF;
        if (currentUserId == 1 && owner == 0) return false; // admin 不能动 root 的文件
        if (currentUserId >= 2 && owner != currentUserId) return false; // 普通用户只能动自己的
        return true;
    }

    /** 判断当前用户是否可以访问指定条目 */
    private boolean canAccess(DirectoryEntry entry, String parentPath) {
        if (currentUserId <= 1) return true; // root/admin 看全部
        int owner = entry.getOwnerId() & 0xFF;
        if (owner == currentUserId) return true; // 自己的文件/目录

        // /shared/ 下全部可见
        if (parentPath != null && parentPath.startsWith("/shared")) return true;

        // / 根目录：只显示 home 和 shared（不显示所有者=0 的其他文件）
        if (parentPath != null && parentPath.equals("/")) {
            return entry.getFileName().equals("home")
                    || entry.getFileName().equals("shared");
        }

        // /home/ 下：只看自己拥有的目录
        if (parentPath != null && parentPath.equals("/home")) return false;

        // /home/<xxx>/ 下：也只有自己拥有的条目可见
        if (parentPath != null && parentPath.startsWith("/home/")) return false;

        return false;
    }

    public int[] getFATSnapshot() {
        int[] f = new int[DiskConstants.BLOCK_COUNT];
        for (int i = 0; i < DiskConstants.BLOCK_COUNT; i++) f[i] = disk.getFATEntry(i) & 0xFF;
        return f;
    }

    public List<DirectoryEntry> listDir(int blk, String parentPath) {
        List<DirectoryEntry> all = resolver.listEntries(blk);
        if (currentUserId <= 1) return all;
        if (parentPath != null && parentPath.startsWith("/shared")) return all;
        java.util.List<DirectoryEntry> filtered = new java.util.ArrayList<>();
        for (DirectoryEntry e : all) {
            int owner = e.getOwnerId() & 0xFF;
            if (owner == currentUserId) filtered.add(e);
            else if ("shared".equals(e.getFileName())) filtered.add(e);
            else if ("home".equals(e.getFileName()) && "/".equals(parentPath)) filtered.add(e);
        }
        return filtered;
    }
    public List<DirectoryEntry> listDir(int blk) { return listDir(blk, null); }
    public PathResolver.ResolveResult findInDir(int dirBlock, String name) {
        return resolver.findInDirectory(dirBlock, name);
    }

    private String buildPath(String name) {
        return currentPath.equals("/") ? "/" + name : currentPath + "/" + name;
    }
    private void updateEntry(PathResolver.ResolveResult rr) {
        byte[] blk = disk.readBlock(rr.entryBlock);
        byte[] eb = rr.entry.toBytes();
        System.arraycopy(eb, 0, blk, rr.entryIndex * DiskConstants.DIR_ENTRY_SIZE,
                DiskConstants.DIR_ENTRY_SIZE);
        disk.writeBlock(rr.entryBlock, blk);
    }
}
