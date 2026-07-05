# 更新日志 (What's New)

## 版本 1.8-Milestone-1 (2026-05-11)

### 版本升级
- Minecraft 版本升级至 26.2-snapshot-6 (26.2-alpha.6)
- Fabric Loader 升级至 0.19.2
- Fabric API 升级至 0.148.1+26.2
- Gradle 升级至 9.2.1
- Kotlin 保持 2.3.20
- Java 工具链保持 JDK 25

### 新功能
- 新增 `MinecraftBridge` 桥接类：通过纯反射机制访问 Minecraft 26.2-snapshot 中重命名的 API（如 `setScreen`、Toast Manager），避免因 Mojang 重映射导致的编译失败
- 新增 `DebugScreenOverlayMixin`：在 F3 调试屏幕中显示 SSH 连接状态信息
- 标题屏幕（Title Screen）新增 SSH 配置入口按钮，与暂停屏幕按钮位置一致
- `PauseScreenMixin` 在暂停菜单中添加 SSH 配置入口按钮

![新功能演示](docs/img/SSHWindowInGame.png)

### 改进
- SSH 终端屏幕（`LinuxsshTerminalScreen`）增加线程安全锁（`terminalLock`），防止读写操作并发冲突
- SSH 终端写入操作独立为 `writeExecutor`，避免阻塞读取线程
- 终端初始化失败时正确关闭界面（`onClose`），而非直接清除屏幕
- 统一所有界面中的 SSH 按钮风格：使用 `"SSH"` 文字标签，尺寸 45×20，位于屏幕右下角
- 所有界面切换统一通过 `MinecraftBridge.setScreen()` 进行，确保对 API 变更的兼容性
- 清理终端代码中的冗余注释和未使用的导入（`java.lang.reflect.Modifier`）
- 优化构建配置：新增 `foojay-resolver-convention` Gradle 插件以自动管理 JDK 工具链

## 版本 1.8（Release，2026-07-05）

### 版本升级
- Minecraft 版本升级至 26.2 (正式版)
- Fabric Loader 升级至 0.19.3
- Fabric API 升级至 0.154.0+26.2

### 新功能
- 本次版本主要升级 Minecraft 版本至正式版，修复了一些 bug 并优化了一些性能。