package fs;

import disk.DiskConstants;
import java.nio.charset.StandardCharsets;

/**
 * 目录项模型。16 字节布局：
 *   [0-7]   文件名（8B ASCII，不足补 0）
 *   [8]     类型（0=文件，1=目录）
 *   [9]     属性（bit0=只读，bit1=隐藏）
 *   [10]    所有者 ID
 *   [11]    起始块号
 *   [12-15] 文件大小（big-endian int）
 */
public class DirectoryEntry {

    private String fileName;
    private byte type;
    private byte attributes;
    private byte ownerId;
    private byte startBlock;
    private int fileSize;

    public DirectoryEntry() {
        this("", DiskConstants.TYPE_FILE, (byte) 0, (byte) 0, (byte) 0, 0);
    }

    public DirectoryEntry(String fileName, byte type, byte attributes,
                          byte ownerId, byte startBlock, int fileSize) {
        this.fileName = clipName(fileName);
        this.type = type;
        this.attributes = attributes;
        this.ownerId = ownerId;
        this.startBlock = startBlock;
        this.fileSize = fileSize;
    }

    private static String clipName(String name) {
        if (name == null || name.isEmpty()) return "";
        return name.length() > DiskConstants.MAX_FILENAME_LEN
                ? name.substring(0, DiskConstants.MAX_FILENAME_LEN) : name;
    }

    /** 从 16 字节数组偏移处反序列化 */
    public static DirectoryEntry fromBytes(byte[] data, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < offset + DiskConstants.MAX_FILENAME_LEN; i++) {
            if (data[i] == 0) break;
            sb.append((char) (data[i] & 0xFF));
        }
        return new DirectoryEntry(
            sb.toString(),
            data[offset + 8],
            data[offset + 9],
            data[offset + 10],
            data[offset + 11],
            ((data[offset + 12] & 0xFF) << 24) | ((data[offset + 13] & 0xFF) << 16)
                    | ((data[offset + 14] & 0xFF) << 8) | (data[offset + 15] & 0xFF)
        );
    }

    /** 序列化为 16 字节 */
    public byte[] toBytes() {
        byte[] d = new byte[DiskConstants.DIR_ENTRY_SIZE];
        byte[] nb = fileName.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nb, 0, d, 0, Math.min(nb.length, DiskConstants.MAX_FILENAME_LEN));
        d[8] = type;
        d[9] = attributes;
        d[10] = ownerId;
        d[11] = startBlock;
        d[12] = (byte) ((fileSize >> 24) & 0xFF);
        d[13] = (byte) ((fileSize >> 16) & 0xFF);
        d[14] = (byte) ((fileSize >> 8) & 0xFF);
        d[15] = (byte) (fileSize & 0xFF);
        return d;
    }

    public boolean isEmpty() { return fileName.isEmpty(); }
    public boolean isDirectory() { return type == DiskConstants.TYPE_DIRECTORY; }
    public boolean isFile() { return type == DiskConstants.TYPE_FILE; }
    public boolean isReadOnly() { return (attributes & DiskConstants.ATTR_READONLY) != 0; }

    // --- getters/setters ---
    public String getFileName() { return fileName; }
    public void setFileName(String n) { this.fileName = clipName(n); }
    public byte getType() { return type; }
    public void setType(byte t) { this.type = t; }
    public byte getAttributes() { return attributes; }
    public void setAttributes(byte a) { this.attributes = a; }
    public byte getOwnerId() { return ownerId; }
    public void setOwnerId(byte o) { this.ownerId = o; }
    public byte getStartBlock() { return startBlock; }
    public void setStartBlock(byte b) { this.startBlock = b; }
    public int getFileSize() { return fileSize; }
    public void setFileSize(int s) { this.fileSize = s; }

    @Override
    public String toString() {
        return (isDirectory() ? "[DIR]  " : "[FILE] ") + fileName
                + "  size=" + fileSize + "  owner=" + (ownerId & 0xFF);
    }
}
