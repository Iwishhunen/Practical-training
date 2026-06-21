package fs;

/**
 * 已打开文件的运行时状态。
 */
public class OpenFileEntry {

    public static final int MODE_READ = 1;
    public static final int MODE_WRITE = 2;
    public static final int MODE_READWRITE = 3;

    private final DirectoryEntry entry;
    private final int mode;
    private int position;
    private boolean closed;

    public OpenFileEntry(DirectoryEntry entry, int mode) {
        this.entry = entry;
        this.mode = mode;
        this.position = 0;
        this.closed = false;
    }

    public DirectoryEntry getEntry() { return entry; }
    public int getMode() { return mode; }
    public int getPosition() { return position; }
    public void setPosition(int p) { this.position = p; }
    public boolean isClosed() { return closed; }
    public void close() { this.closed = true; }
    public boolean canRead() { return mode == MODE_READ || mode == MODE_READWRITE; }
    public boolean canWrite() { return mode == MODE_WRITE || mode == MODE_READWRITE; }
}
