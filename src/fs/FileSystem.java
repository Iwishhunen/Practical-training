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

    public FileSystem(Disk disk, FAT fat) {
        this.disk = disk;
        this.fat = fat;
        this.resolver = new PathResolver(disk, fat);
        this.openFiles = new LinkedHashMap<>();
        this.currentDirBlock = DiskConstants.ROOT_DIR_BLOCK;
        this.currentPath = "/";
    }

    /** 格式化并初始化 */
    public void initFileSystem() {
        disk.format();
        currentDirBlock = DiskConstants.ROOT_DIR_BLOCK;
        currentPath = "/";
        openFiles.clear();
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
        int dblk = rr.entry.getStartBlock() & 0xFF;
        if (!resolver.listEntries(dblk).isEmpty()) return "Error: directory not empty";
        fat.freeChain(dblk);
        resolver.removeEntry(rr.entryBlock, rr.entryIndex);
        return "rmdir ok";
    }

    public List<DirectoryEntry> listDir() { return resolver.listEntries(currentDirBlock); }

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
            currentDirBlock = rr.entry.getStartBlock() & 0xFF;
            currentPath = (path.startsWith("/")) ? path : (currentPath.equals("/") ? "/" + path : currentPath + "/" + path);
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

    public int[] getFATSnapshot() {
        int[] f = new int[DiskConstants.BLOCK_COUNT];
        for (int i = 0; i < DiskConstants.BLOCK_COUNT; i++) f[i] = disk.getFATEntry(i) & 0xFF;
        return f;
    }

    // re-export for GUI
    public List<DirectoryEntry> listDir(int blk) { return resolver.listEntries(blk); }
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
