package fs;

import disk.Disk;
import disk.DiskConstants;
import disk.FAT;
import java.util.ArrayList;
import java.util.List;

/**
 * 路径解析器。支持绝对路径遍历和目录条目管理。
 */
public class PathResolver {

    private final Disk disk;
    private final FAT fat;

    public PathResolver(Disk disk, FAT fat) {
        this.disk = disk;
        this.fat = fat;
    }

    /** 解析结果 */
    public static class ResolveResult {
        public final DirectoryEntry entry;
        public final int parentBlock;
        public final int entryBlock;
        public final int entryIndex;
        public final boolean found;

        public ResolveResult(DirectoryEntry e, int parentBlock, int entryBlock,
                             int idx, boolean found) {
            this.entry = e; this.parentBlock = parentBlock;
            this.entryBlock = entryBlock; this.entryIndex = idx; this.found = found;
        }
        public static ResolveResult notFound(int parent) {
            return new ResolveResult(null, parent, parent, -1, false);
        }
        public static ResolveResult found(DirectoryEntry e, int parent, int blk, int idx) {
            return new ResolveResult(e, parent, blk, idx, true);
        }
    }

    /** 列出目录块链中所有非空条目 */
    public List<DirectoryEntry> listEntries(int dirStartBlock) {
        List<DirectoryEntry> list = new ArrayList<>();
        int cur = dirStartBlock, safety = 0;
        while (cur != DiskConstants.FAT_EOF && cur != DiskConstants.FAT_FREE
                && safety < DiskConstants.BLOCK_COUNT) {
            byte[] b = disk.readBlock(cur);
            for (int i = 0; i < DiskConstants.ENTRIES_PER_BLOCK; i++) {
                DirectoryEntry e = DirectoryEntry.fromBytes(b,
                        i * DiskConstants.DIR_ENTRY_SIZE);
                if (!e.isEmpty()) list.add(e);
            }
            int nxt = disk.getFATEntry(cur) & 0xFF;
            if (nxt == DiskConstants.FAT_EOF || nxt == DiskConstants.FAT_FREE) break;
            cur = nxt; safety++;
        }
        return list;
    }

    /** 在目录中查找指定名称的条目 */
    public ResolveResult findInDirectory(int dirStartBlock, String name) {
        int cur = dirStartBlock, safety = 0;
        while (cur != DiskConstants.FAT_EOF && cur != DiskConstants.FAT_FREE
                && safety < DiskConstants.BLOCK_COUNT) {
            byte[] b = disk.readBlock(cur);
            for (int i = 0; i < DiskConstants.ENTRIES_PER_BLOCK; i++) {
                DirectoryEntry e = DirectoryEntry.fromBytes(b,
                        i * DiskConstants.DIR_ENTRY_SIZE);
                if (!e.isEmpty() && e.getFileName().equals(name))
                    return ResolveResult.found(e, dirStartBlock, cur, i);
            }
            int nxt = disk.getFATEntry(cur) & 0xFF;
            if (nxt == DiskConstants.FAT_EOF || nxt == DiskConstants.FAT_FREE) break;
            cur = nxt; safety++;
        }
        return ResolveResult.notFound(dirStartBlock);
    }

    /** 在目录块链中找空闲条目位置，{blockNo, index}，index=-1 表示最后一个块已满 */
    public int[] findFreeSlot(int dirStartBlock) {
        int cur = dirStartBlock, safety = 0;
        while (cur != DiskConstants.FAT_EOF && cur != DiskConstants.FAT_FREE
                && safety < DiskConstants.BLOCK_COUNT) {
            byte[] b = disk.readBlock(cur);
            for (int i = 0; i < DiskConstants.ENTRIES_PER_BLOCK; i++) {
                DirectoryEntry e = DirectoryEntry.fromBytes(b,
                        i * DiskConstants.DIR_ENTRY_SIZE);
                if (e.isEmpty()) return new int[]{cur, i};
            }
            int nxt = disk.getFATEntry(cur) & 0xFF;
            if (nxt == DiskConstants.FAT_EOF || nxt == DiskConstants.FAT_FREE) break;
            cur = nxt; safety++;
        }
        return new int[]{cur, -1};
    }

    /** 解析绝对路径 */
    public ResolveResult resolve(String absPath, int currentDirBlock) {
        if (absPath == null || absPath.isEmpty())
            return ResolveResult.notFound(currentDirBlock);

        int parentBlock = DiskConstants.ROOT_DIR_BLOCK;
        String[] parts = splitPath(absPath);
        if (parts.length == 0) {
            DirectoryEntry root = new DirectoryEntry("/",
                    DiskConstants.TYPE_DIRECTORY, (byte) 0, (byte) 0,
                    (byte) DiskConstants.ROOT_DIR_BLOCK, 0);
            return ResolveResult.found(root, DiskConstants.ROOT_DIR_BLOCK,
                    DiskConstants.ROOT_DIR_BLOCK, 0);
        }

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.equals(".")) continue;

            ResolveResult rr = findInDirectory(parentBlock, part);
            if (!rr.found) return ResolveResult.notFound(parentBlock);
            if (i == parts.length - 1) return rr;
            if (!rr.entry.isDirectory()) return ResolveResult.notFound(parentBlock);
            parentBlock = rr.entry.getStartBlock() & 0xFF;
        }
        return ResolveResult.notFound(parentBlock);
    }

    private String[] splitPath(String path) {
        String t = path.replace('\\', '/');
        if (t.startsWith("/")) t = t.substring(1);
        if (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t.isEmpty() ? new String[0] : t.split("/");
    }

    /** 清除指定位置的目录项 */
    public void removeEntry(int blockNo, int entryIndex) {
        byte[] b = disk.readBlock(blockNo);
        int off = entryIndex * DiskConstants.DIR_ENTRY_SIZE;
        for (int i = 0; i < DiskConstants.DIR_ENTRY_SIZE; i++) b[off + i] = 0;
        disk.writeBlock(blockNo, b);
    }

    /** 在目录中写入条目，自动找空位或扩展目录块链 */
    public boolean writeEntry(int dirStartBlock, DirectoryEntry entry) {
        int[] slot = findFreeSlot(dirStartBlock);
        if (slot[1] >= 0) {
            writeEntryAt(slot[0], slot[1], entry);
            return true;
        }
        int tail = fat.getChainTail(dirStartBlock);
        int nb = fat.appendBlock(tail);
        if (nb < 0) return false;
        writeEntryAt(nb, 0, entry);
        return true;
    }

    private void writeEntryAt(int blockNo, int idx, DirectoryEntry entry) {
        byte[] b = disk.readBlock(blockNo);
        byte[] eb = entry.toBytes();
        System.arraycopy(eb, 0, b, idx * DiskConstants.DIR_ENTRY_SIZE,
                DiskConstants.DIR_ENTRY_SIZE);
        disk.writeBlock(blockNo, b);
    }
}
