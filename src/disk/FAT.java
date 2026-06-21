package disk;

/**
 * FAT（文件分配表）管理器。
 * 提供磁盘块的分配、释放、链遍历等功能。
 */
public class FAT {

    private final Disk disk;

    public FAT(Disk disk) {
        this.disk = disk;
    }

    /** 分配一个空闲块，将其 FAT 标记为 EOF。无空闲返回 -1。 */
    public int allocateBlock() {
        int free = disk.findFreeBlock();
        if (free >= 0) {
            disk.setFATEntry(free, DiskConstants.FAT_EOF);
        }
        return free;
    }

    /** 为链尾块追加新块，返回新块号。 */
    public int appendBlock(int lastBlock) {
        int newBlock = allocateBlock();
        if (newBlock >= 0) {
            disk.setFATEntry(lastBlock, newBlock);
        }
        return newBlock;
    }

    /** 释放从 startBlock 开始的整条块链。 */
    public void freeChain(int startBlock) {
        int current = startBlock;
        int safety = 0;
        while (current != DiskConstants.FAT_EOF && current != DiskConstants.FAT_FREE
                && safety < DiskConstants.BLOCK_COUNT) {
            int next = disk.getFATEntry(current) & 0xFF;
            disk.setFATEntry(current, DiskConstants.FAT_FREE);
            byte[] zeros = new byte[DiskConstants.BLOCK_SIZE];
            disk.writeBlock(current, zeros);
            current = next;
            safety++;
        }
    }

    /** 获取下一块号。 */
    public int getNextBlock(int blockNo) {
        return disk.getFATEntry(blockNo) & 0xFF;
    }

    /** 获取块链尾块号。 */
    public int getChainTail(int startBlock) {
        int current = startBlock;
        int safety = 0;
        while (safety < DiskConstants.BLOCK_COUNT) {
            int next = disk.getFATEntry(current) & 0xFF;
            if (next == DiskConstants.FAT_EOF) return current;
            current = next;
            safety++;
        }
        return current;
    }

    /** 获取块链长度（块数）。 */
    public int getChainLength(int startBlock) {
        int count = 0;
        int current = startBlock;
        int safety = 0;
        while (current != DiskConstants.FAT_EOF && current != DiskConstants.FAT_FREE
                && safety < DiskConstants.BLOCK_COUNT) {
            count++;
            int next = disk.getFATEntry(current) & 0xFF;
            if (next == DiskConstants.FAT_EOF || next == DiskConstants.FAT_FREE) break;
            current = next;
            safety++;
        }
        return count;
    }

    /** 获取块链中第 index 个块号。 */
    public int getBlockInChain(int startBlock, int index) {
        int current = startBlock;
        for (int i = 0; i < index; i++) {
            int next = disk.getFATEntry(current) & 0xFF;
            if (next == DiskConstants.FAT_EOF || next == DiskConstants.FAT_FREE) return -1;
            current = next;
        }
        return current;
    }

    public int getFreeBlockCount() { return disk.getFreeBlockCount(); }

    public boolean isAllocated(int blockNo) {
        int entry = disk.getFATEntry(blockNo) & 0xFF;
        return entry != DiskConstants.FAT_FREE;
    }
}
