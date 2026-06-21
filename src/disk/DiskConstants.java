package disk;

/**
 * 磁盘系统常量定义
 */
public final class DiskConstants {
    private DiskConstants() {}

    /** 物理块总数 */
    public static final int BLOCK_COUNT = 128;

    /** 每块字节数 */
    public static final int BLOCK_SIZE = 64;

    /** 磁盘总容量（字节） */
    public static final int DISK_SIZE = BLOCK_COUNT * BLOCK_SIZE; // 8192 = 8KB

    /** FAT 表起始块号 */
    public static final int FAT_START_BLOCK = 0;

    /** FAT 表占用块数 */
    public static final int FAT_BLOCK_COUNT = 2;

    /** 根目录起始块号 */
    public static final int ROOT_DIR_BLOCK = 2;

    /** 根目录占用的初始块数 */
    public static final int ROOT_DIR_INITIAL_BLOCKS = 1;

    /** FAT 表项值——空闲块 */
    public static final int FAT_FREE = 0;

    /** FAT 表项值——文件结束 */
    public static final int FAT_EOF = 0xFF;

    /** FAT 表项值——坏块 */
    public static final int FAT_BAD = 0xFE;

    /** 每个目录项占用字节数 */
    public static final int DIR_ENTRY_SIZE = 16;

    /** 每个块可容纳的目录项数量 */
    public static final int ENTRIES_PER_BLOCK = BLOCK_SIZE / DIR_ENTRY_SIZE; // 4

    /** 文件名最大长度（字节） */
    public static final int MAX_FILENAME_LEN = 8;

    /** 目录项类型——普通文件 */
    public static final byte TYPE_FILE = 0;

    /** 目录项类型——目录 */
    public static final byte TYPE_DIRECTORY = 1;

    /** 文件属性——只读 */
    public static final byte ATTR_READONLY = 0x01;

    /** 文件属性——隐藏 */
    public static final byte ATTR_HIDDEN = 0x02;

    /** 系统用户 ID */
    public static final int ROOT_USER_ID = 0;

    /** 持久化文件名 */
    public static final String DISK_FILE_NAME = "disk.img";

    /** 默认磁盘文件路径 */
    public static final String DEFAULT_DISK_PATH = System.getProperty("user.dir") + "/" + DISK_FILE_NAME;
}
