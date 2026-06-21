package disk;

import java.io.*;

/**
 * 虚拟磁盘模拟层。
 * 在内存中维护 byte[128][64] 数组，支持持久化到磁盘文件。
 */
public class Disk {

    private final byte[][] blocks;
    private boolean dirty = false;

    public Disk() {
        blocks = new byte[DiskConstants.BLOCK_COUNT][DiskConstants.BLOCK_SIZE];
    }

    /**
     * 格式化磁盘：清零所有块，初始化 FAT 表（全部标为空闲），
     * 标记 FAT 本身占用的块和根目录块。
     */
    public void format() {
        for (int i = 0; i < DiskConstants.BLOCK_COUNT; i++) {
            for (int j = 0; j < DiskConstants.BLOCK_SIZE; j++) {
                blocks[i][j] = 0;
            }
        }
        setFATEntry(0, DiskConstants.FAT_EOF);
        setFATEntry(1, DiskConstants.FAT_EOF);
        setFATEntry(DiskConstants.ROOT_DIR_BLOCK, DiskConstants.FAT_EOF);
        dirty = true;
    }

    public byte[] readBlock(int blockNo) {
        if (blockNo < 0 || blockNo >= DiskConstants.BLOCK_COUNT) {
            throw new IllegalArgumentException("Invalid block number: " + blockNo);
        }
        byte[] copy = new byte[DiskConstants.BLOCK_SIZE];
        System.arraycopy(blocks[blockNo], 0, copy, 0, DiskConstants.BLOCK_SIZE);
        return copy;
    }

    public void writeBlock(int blockNo, byte[] data) {
        if (blockNo < 0 || blockNo >= DiskConstants.BLOCK_COUNT) {
            throw new IllegalArgumentException("Invalid block number: " + blockNo);
        }
        if (data.length > DiskConstants.BLOCK_SIZE) {
            throw new IllegalArgumentException("Data exceeds block size");
        }
        byte[] target = blocks[blockNo];
        System.arraycopy(data, 0, target, 0, Math.min(data.length, DiskConstants.BLOCK_SIZE));
        if (data.length < DiskConstants.BLOCK_SIZE) {
            for (int i = data.length; i < DiskConstants.BLOCK_SIZE; i++) {
                target[i] = 0;
            }
        }
        dirty = true;
    }

    public int getFATEntry(int blockNo) {
        if (blockNo < 0 || blockNo >= DiskConstants.BLOCK_COUNT) {
            throw new IllegalArgumentException("Invalid block number: " + blockNo);
        }
        int fatBlockIndex = blockNo < 64 ? 0 : 1;
        int offset = blockNo % 64;
        return blocks[DiskConstants.FAT_START_BLOCK + fatBlockIndex][offset] & 0xFF;
    }

    public void setFATEntry(int blockNo, int value) {
        if (blockNo < 0 || blockNo >= DiskConstants.BLOCK_COUNT) {
            throw new IllegalArgumentException("Invalid block number: " + blockNo);
        }
        int fatBlockIndex = blockNo < 64 ? 0 : 1;
        int offset = blockNo % 64;
        blocks[DiskConstants.FAT_START_BLOCK + fatBlockIndex][offset] = (byte) (value & 0xFF);
        dirty = true;
    }

    public void saveToFile(String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            for (int i = 0; i < DiskConstants.BLOCK_COUNT; i++) {
                fos.write(blocks[i]);
            }
        }
        dirty = false;
    }

    public void loadFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Disk file not found: " + filePath);
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            for (int i = 0; i < DiskConstants.BLOCK_COUNT; i++) {
                int bytesRead = fis.read(blocks[i]);
                if (bytesRead < DiskConstants.BLOCK_SIZE) {
                    for (int j = bytesRead; j < DiskConstants.BLOCK_SIZE; j++) {
                        blocks[i][j] = 0;
                    }
                }
            }
        }
        dirty = false;
    }

    public boolean isDirty() { return dirty; }

    public int getFreeBlockCount() {
        int count = 0;
        for (int i = DiskConstants.ROOT_DIR_BLOCK + 1; i < DiskConstants.BLOCK_COUNT; i++) {
            if (getFATEntry(i) == DiskConstants.FAT_FREE) count++;
        }
        return count;
    }

    public int findFreeBlock() {
        for (int i = DiskConstants.ROOT_DIR_BLOCK + 1; i < DiskConstants.BLOCK_COUNT; i++) {
            if (getFATEntry(i) == DiskConstants.FAT_FREE) return i;
        }
        return -1;
    }
}
