# 答辩 PPT 文档

> 以下所有数据、结构、功能描述均直接来自项目源代码。
> 括号内标注了对应的源文件和行数，可在答辩时佐证。

---

## 第 1 页：封面

**标题**：多用户多级目录文件系统（图形用户接口）

**副标题**：操作系统课程设计答辩

**答辩人**：Kaisheng Xu

**环境**：JDK 17 + Swing  |  代码量：2,462 行  |  源文件：16 个

---

## 第 2 页：项目背景与目标

**课程要求**（来自 `doc_output.txt` 提取）：
- 模拟操作系统的文件管理子系统
- 多用户、多级目录、图形用户接口
- 实现文档列出的 12 项核心操作命令

**我的完成**：
- 完整虚拟磁盘：128 块 × 64 字节 = 8KB（`DiskConstants.java` 第 6-8 行）
- FAT 表显式链接（`FAT.java` 99 行）
- 多用户系统：SHA-256 哈希 + /home 隔离 + 四级权限（`UserManager.java`）
- Swing 图形界面：仿 Windows 资源管理器（`gui/` 6 文件）

---

## 第 3 页：系统总体架构

```
┌──────────────────────────────────────────┐
│  GUI 层 (6 文件, 1,222 行)                │
│  Login / MainFrame / FileTree / FileTable │
├──────────────────────────────────────────┤
│  用户层 (2 文件, 221 行)                   │
│  UserManager + User                       │
├──────────────────────────────────────────┤
│  文件系统层 (4 文件, 730 行)                │
│  FileSystem / PathResolver / DirEntry     │
├──────────────────────────────────────────┤
│  磁盘层 (3 文件, 289 行)                   │
│  Disk / FAT / DiskConstants               │
└──────────────────────────────────────────┘
```

**分层统计**（来自 `wc -l`）：

| 层 | 文件 | 行数 |
|----|------|------|
| 磁盘层 | Disk(122) + DiskConstants(68) + FAT(99) | 289 |
| 文件系统层 | FileSystem(433) + PathResolver(163) + DirectoryEntry(102) + OpenFileEntry(32) | 730 |
| 用户层 | UserManager(175) + User(46) | 221 |
| GUI 层 | MainFrame(519) + FileTreePanel(283) + FileTablePanel(198) + LoginDialog(74) + DiskUsagePanel(64) + TextEditorDialog(42) + FileSystemApp(42) | 1,222 |

---

## 第 4 页：磁盘结构设计

**磁盘参数**（来自 `DiskConstants.java`）：

| 参数 | 值 | 对应常量 |
|------|-----|---------|
| 物理块总数 | 128 | `BLOCK_COUNT = 128` |
| 每块字节数 | 64 | `BLOCK_SIZE = 64` |
| 总容量 | 8,192 字节 | `DISK_SIZE = 128 × 64` |
| FAT 占用 | 块 0、块 1 | `FAT_BLOCK_COUNT = 2` |
| 根目录起始 | 块 2 | `ROOT_DIR_BLOCK = 2` |

**FAT 表项**（1 字节 / 块，`Disk.getFATEntry()`）：
- `0x00`（`FAT_FREE`）：空闲块
- `0xFF`（`FAT_EOF`）：链尾
- 其他值：下一块号

**目录项布局**（16 字节，`DIR_ENTRY_SIZE = 16`）：

| 偏移 | 大小 | 含义 |
|------|------|------|
| 0-7 | 8B | 文件名（`MAX_FILENAME_LEN = 8`，ASCII，空位补 0） |
| 8 | 1B | 类型（`TYPE_FILE = 0` / `TYPE_DIRECTORY = 1`） |
| 9 | 1B | 属性（`ATTR_READONLY = 0x01` / `ATTR_HIDDEN = 0x02`） |
| 10 | 1B | 所有者用户 ID |
| 11 | 1B | 起始块号 |
| 12-15 | 4B | 文件大小（big-endian int） |

每块容纳 4 个目录项（`ENTRIES_PER_BLOCK = 64 / 16 = 4`）。

---

## 第 5 页：文件系统核心层

**全部公开方法**（来自 `FileSystem.java` 的方法签名）：

| 分类 | 方法 | 功能 |
|------|------|------|
| 目录 | `mkdir(name, ownerId)` | 创建目录，分配新块 |
| | `rmdir(name)` | 删除空目录，释放块链 |
| | `cd(path)` | 绝对/相对路径切换，含权限检查 |
| | `listDir()` | 列出当前目录，含权限过滤 |
| 文件 | `createFile(name, ownerId)` | 创建文件，分配起始块 |
| | `deleteFile(name)` | 删除文件，释放 FAT 链 |
| | `openFile(name, mode)` | 打开文件，记录读写位置 |
| | `closeFile(name)` | 关闭文件 |
| | `readFile(name)` | 读取，遍历 FAT 链重构内容 |
| | `writeFile(name, content, ownerId)` | 写入，计算块数分配链 |
| 扩展 | `copyFile(src, dest, ownerId)` | 跨目录复制 |
| | `moveFile(src, dest, ownerId)` | 跨目录移动（复制后删源） |
| | `rename(oldName, newName)` | 重命名目录项 |
| | `setAttribute(name, attrs)` | 修改只读/隐藏属性 |

**写文件流程**（`writeFile()`）：计算所需块数 → while 循环逐块分配 → `fat.allocateBlock()` → `disk.setFATEntry()` 链接 → `disk.writeBlock()` 写入内容 → 更新目录项文件大小。

**读文件流程**（`readFile()`）：从起始块开始 → 循环 `disk.getFATEntry()` 获取下一块 → 逐块读取到 `ByteArrayOutputStream` → 按文件大小截断。

---

## 第 6 页：多用户与权限系统

**用户管理**（来自 `UserManager.java`，175 行）：

- SHA-256 密码哈希（`User.hashPassword()`，`User.java` 第 18-24 行）
- 用户持久化到 `users.dat` 文件（`loadUsers()` 加载，`saveUsers()` 保存）
- 文件格式：`userId|username|passwordHash|homeDir`（`saveUsers()` 第 63-65 行）
- 注册时自动在 `/home/` 下创建用户目录（`ensureUserHome()` 调用 `mkdir()`）

**四级权限**（来自 `FileSystem.java`）：

| 角色 | UID | 查看 | 修改 | 格式化 |
|------|-----|------|------|:----:|
| root | 0 | 全部 | 全部 | ✅ |
| admin | 1 | 全部 | 不可改 owner=0 的文件 | ❌ |
| user | ≥2 | 自己+/shared | 仅自己 | ❌ |
| guest | 999 | 无 | 无 | ❌ |

**关键代码**（`FileSystem.java`）：
- `canModify()`：root 直接通过；admin 遇 owner=0 拒绝；user 遇 owner≠uid 拒绝
- `listDir()`：uid≤1 不过滤；/shared 路径不过滤；其余按 owner==uid 过滤
- `cd()`：普通用户 cd 到他人 /home/ 目录返回 "access denied"

---

## 第 7 页：GUI 界面

**组件结构**（来自 `gui/` 包）：

- `FileSystemApp`（42 行）：入口，初始化磁盘→FS→用户→GUI
- `LoginDialog`（74 行）：登录/注册/访客三种入口
- `MainFrame`（519 行）：核心窗口
  - 菜单栏：File / View / User / Help
  - 工具栏：Home / Up / New File / New Dir / Delete / Refresh / Disk
  - 左右分栏：左侧 JTree 目录树 + 右侧 JTable 文件列表
  - 命令行输入：底部 `JTextField`
  - 状态栏：当前路径 + 用户 + 空闲块数
- `FileTreePanel`（283 行）：目录树，节点存 `fullPath` 绝对路径，递归构建子节点
- `FileTablePanel`（198 行）：文件列表，右键菜单 12 项，双击打开
- `TextEditorDialog`（42 行）：文本编辑，Ctrl+S 保存
- `DiskUsagePanel`（64 行）：128 块颜色网格可视化

**右键菜单项**（来自 `FileTreePanel.buildPopupMenu()` 和 `FileTablePanel` 构造函数）：

Open | Copy | Cut | Paste | New File | New Folder | Rename | Delete | Change Attributes | Properties | Refresh

**键盘快捷键**：

| 按键 | 功能 | 绑定的方法 |
|------|------|----------|
| Enter | 打开文件/进入目录 | `openSelected()` |
| Delete | 删除选中项 | `deleteSelected()` |
| F2 | 重命名 | `renameSelected()` |
| Ctrl+S | 保存（编辑器内） | `saveBtn.doClick()` |
| Ctrl+C | 复制到剪贴板 | `doCopy()` |
| Ctrl+X | 剪切到剪贴板 | `doCut()` |
| Ctrl+V | 从剪贴板粘贴 | `doPaste()` |

---

## 第 8 页：开发中遇到的问题与解决

**共记录 10 个实际问题**（详见 `问题与解决方案.md`）

| # | 问题 | 解决方法 |
|---|------|---------|
| 1 | 权限过滤三层各自独立，遗漏一层即失效 | 统一维护 FileSystem + UI 双过滤 |
| 2 | 登录后 `currentUserId` 未设置，UID 显示 999 | 收敛到 `login()` 内唯一调用点 |
| 3 | Guest 默认 uid=0 等同 root 权限 | 设为 999 触发完整过滤 |
| 4 | Cut/Paste 文件名加 `copy_` 前缀超过 8 字符限制 | 去掉前缀，write 本身就是覆盖 |
| 5 | `/home/root` 对普通用户可见 | root 不占 `/home`，home 设为 `/` |
| 6 | 注册用户重启后消失，只有内存存储 | `users.dat` 文件持久化 |
| 7 | admin 和 root 权限完全相同 | 新增 `canModify()` 区分 |
| 8 | JTree 右键不自动选中节点，导航走错目录 | `getPathForLocation` + `fullPath` |
| 9 | Logout 在 Guest 状态下无可见变化 | 未登录提示 + 已登录则隐藏主窗弹登录框 |
| 10 | `/shared` 内文件对非创建者不可见 | 三层过滤加 `startsWith("/shared")` 豁免 |

---

## 第 9 页：成果展示

**功能完整性**（对照文档 13 项命令）：

| # | 命令 | 实现 | # | 命令 | 实现 |
|---|------|:--:|---|------|:--:|
| 1 | login | ✅ | 8 | delete | ✅ |
| 2 | 系统说明 | ✅ | 9 | mkdir | ✅ |
| 3 | create | ✅ | 10 | rmdir | ✅ |
| 4 | open | ✅ | 11 | cd | ✅ |
| 5 | read | ✅ | 12 | dir | ✅ |
| 6 | write | ✅ | — | logout | ✅ |
| 7 | close | ✅ | | | |

**超出文档的扩展功能**：

| 功能 | 实现 |
|------|------|
| Copy/Cut/Paste 跨目录操作 | `FileSystem.copyFile/moveFile` |
| Rename 重命名 | `FileSystem.rename` |
| 文件属性修改 | `FileSystem.setAttribute` |
| 四级权限体系 | `canModify()` + `canAccess()` |
| /shared 共享目录 | 三层过滤豁免 |
| 磁盘使用可视化 | `DiskUsagePanel` 128 块网格 |
| 用户数据持久化 | `users.dat` 文件 |
| 磁盘数据持久化 | `disk.img` 二进制文件 |

---

## 第 10 页：总结与收获

**项目数据**：

| 指标 | 数值 |
|------|------|
| 源文件 | 16 个 `.java` |
| 代码行数 | 2,462 行 |
| 文档命令 | 13 项全部 |
| Git 提交 | 18 次 |
| 最大文件 | `MainFrame.java`（519 行） |
| 核心文件 | `FileSystem.java`（433 行） |

**技术收获**：
1. 理解了 FAT 文件系统的块分配策略和显式链接原理
2. 实践了字节级别的磁盘 I/O 模拟和二进制数据序列化
3. 掌握了 Swing 的 JTree、JTable、JMenuBar、右键菜单等组件开发
4. 学会了多层权限系统的设计与过滤逻辑的维护

**可改进方向**：
- 文件名 8 字符限制过短，可扩展目录项格式
- 8KB 磁盘容量偏小，增大块数或块大小
- 可增加文件内容搜索、多文件批量操作等功能
