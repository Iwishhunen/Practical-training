# 多用户多级目录文件系统（图形用户接口）

> 操作系统课程设计 | JDK 17 + Swing | 参考 sensnow/os-system-exp

## 项目概述

模拟操作系统的文件管理功能，实现**多用户权限隔离**、**多级目录树**、**完整文件 CRUD 操作**，并提供 **Windows 资源管理器风格**的 GUI 图形界面。

### 核心特性

- **四层架构**：磁盘模拟 → 文件系统 → 用户管理 → GUI
- **虚拟磁盘**：128 块 × 64 字节 = 8KB，FAT 表显式链接分配
- **多用户**：SHA-256 密码哈希，`/home/` 目录隔离
- **四级权限**：root → admin → user → guest
- **12 项核心命令**：login / 系统说明 / create / open / read / write / close / delete / mkdir / cd / dir / logout
- **Copy/Cut/Paste**：跨目录复制移动文件
- **磁盘持久化**：disk.img 二进制文件

---

## 快速启动

### 环境要求

- **JDK 17+**（无需 Maven/Gradle，零额外依赖）
- Windows / Linux / macOS

### 编译

```bash
cd "项目目录"
javac -encoding UTF-8 -d out src/disk/*.java src/fs/*.java src/user/*.java src/gui/*.java
```

### 运行

```bash
java -cp out gui.FileSystemApp
```

### 默认用户

| 用户名 | 密码 | 角色 | 权限 |
|--------|------|------|------|
| `root` | `root123` | 超级管理员 | 全部 + 格式化 |
| `admin` | `admin123` | 管理员 | 全部（不可动 root 文件） |
| `user1` | `111111` | 普通用户 | 仅自己的文件 + /shared |

也可以点击 **Guest** 进入（无任何权限），然后通过菜单 `User → Login` 登录。

---

## 系统架构

```
┌─────────────────────────────────────────────┐
│   GUI 层 (gui/)                              │
│   LoginDialog → MainFrame                    │
│   ├── FileTreePanel  (左侧目录树)             │
│   ├── FileTablePanel (右侧文件列表)           │
│   ├── TextEditorDialog (文本编辑)             │
│   └── DiskUsagePanel (磁盘可视化)             │
├─────────────────────────────────────────────┤
│   用户管理层 (user/)                          │
│   UserManager: login / logout / register     │
│   User: 密码哈希 / 主目录                     │
├─────────────────────────────────────────────┤
│   文件系统核心层 (fs/)                        │
│   FileSystem: create/delete/open/read/write  │
│   PathResolver: 路径解析 / 树遍历             │
│   DirectoryEntry: 目录项序列化 (16字节)        │
├─────────────────────────────────────────────┤
│   磁盘模拟层 (disk/)                          │
│   Disk: byte[128][64] + disk.img 持久化       │
│   FAT: 块分配 / 释放 / 链遍历                 │
│   DiskConstants: 系统常量                     │
└─────────────────────────────────────────────┘
```

---

## 磁盘结构

| 参数 | 值 |
|------|-----|
| 块总数 | 128 |
| 块大小 | 64 字节 |
| 总容量 | 8 KB |
| FAT 位置 | 块 0~1 |
| 根目录 | 块 2 |
| FAT 空闲标记 | `0` |
| FAT 文件尾 | `0xFF` |

### 目录项布局（16 字节）

| 偏移 | 大小 | 字段 |
|------|------|------|
| 0–7 | 8 B | 文件名（ASCII） |
| 8 | 1 B | 类型（0=文件, 1=目录） |
| 9 | 1 B | 属性（bit0=只读, bit1=隐藏） |
| 10 | 1 B | 所有者 ID |
| 11 | 1 B | 起始块号 |
| 12–15 | 4 B | 文件大小（big-endian） |

---

## 命令参考

| 命令 | 用法 | 说明 |
|------|------|------|
| `login` | `login <user> <pass>` | 用户登录 |
| `logout` | `logout` | 登出 |
| `register` | `register <user> <pass>` | 注册新用户 |
| `mkdir` | `mkdir <name>` | 创建目录 |
| `cd` | `cd <path>` | 切换目录 |
| `dir` | `dir` / `ls` | 列出当前目录 |
| `create` | `create <name>` | 创建文件 |
| `open` | `open <name>` | 打开文件编辑 |
| `read` | `read <name>` | 读取文件内容 |
| `write` | `write <name>` | 写入文件 |
| `delete` | `delete <name>` | 删除文件/空目录 |
| `format` | `format` | 格式化磁盘（仅 root） |
| `save` | `save` | 保存磁盘到 disk.img |
| `help` | `help` | 显示帮助 |

---

## 键盘快捷键

| 按键 | 功能 |
|------|------|
| Enter | 打开文件/进入目录 |
| Delete | 删除选中项 |
| F2 | 重命名 |
| Ctrl+S | 保存（编辑器内） |
| Ctrl+C | 复制 |
| Ctrl+X | 剪切 |
| Ctrl+V | 粘贴 |

---

## 权限矩阵

| 操作 | root | admin | user | guest |
|------|:----:|:-----:|:----:|:-----:|
| 查看全部文件 | ✅ | ✅ | ❌ | ❌ |
| 查看自己的文件 | ✅ | ✅ | ✅ | ❌ |
| 创建/修改/删除自己的文件 | ✅ | ✅ | ✅ | ❌ |
| 修改/删除他人文件 | ✅ | ⚠️ | ❌ | ❌ |
| 格式化磁盘 | ✅ | ❌ | ❌ | ❌ |
| 访问 /shared | ✅ | ✅ | ✅ | ❌ |

> ⚠ admin 不能删除/修改 root（owner=0）的文件

---

## 项目结构

```
src/
├── disk/
│   ├── DiskConstants.java    # 系统常量
│   ├── Disk.java             # 虚拟磁盘读写
│   └── FAT.java              # 文件分配表
├── fs/
│   ├── DirectoryEntry.java   # 目录项模型
│   ├── FileSystem.java       # 文件系统核心
│   ├── PathResolver.java     # 路径解析
│   └── OpenFileEntry.java    # 打开文件状态
├── user/
│   ├── User.java             # 用户模型
│   └── UserManager.java      # 用户管理
└── gui/
    ├── FileSystemApp.java    # 程序入口
    ├── LoginDialog.java      # 登录窗口
    ├── MainFrame.java        # 主窗口
    ├── FileTreePanel.java    # 目录树
    ├── FileTablePanel.java   # 文件列表
    ├── TextEditorDialog.java # 文本编辑器
    └── DiskUsagePanel.java   # 磁盘可视化
```

---

## 参考资料

- [sensnow/os-system-exp](https://github.com/sensnow/os-system-exp) — 华南农业大学，JavaFX
- [BangBOOM/File-System](https://github.com/BangBOOM/File-System) — 东北大学，Python

---

## 作者

zhuhuinan
