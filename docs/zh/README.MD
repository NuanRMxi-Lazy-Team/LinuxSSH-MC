# LinuxSSH

一款强大的工具，可在 Minecraft 中通过 SSH 以终端的形式管理 Linux 电脑。

## 系统要求

* Fabric Loader 0.17.0
* Fabric API v0.130.0+1.21.8
* Fabric Language Kotlin v1.13.4+kotlin.2.2.0
* Minecraft 1.21.8

## 如何使用？

1. 将发布版的 JAR 文件拖入你的 mods 文件夹。
2. 使用你喜欢的启动器（官方、PCL2 等）启动 Minecraft。
3. 加载一个世界并开始享受吧！

## 命令用法

我们提供了一个 `/ssh` 命令来与这个模组进行交互。

1. 连接到主机

   ```text
   /ssh connect <user@hostname>
   ```

   例如：

   ```text
   /ssh connect celestia@192.168.1.129
   ```

2. 确认主机的指纹

   如果你第一次连接到主机，你可能需要确认来自 Linux 主机的指纹。输入 `/ssh confirm <yes/no>` 来接受或拒绝指纹。

3. 输入密码

   然后，输入 `/ssh password` 来进行下一步操作。我们不会在本地存储你的密码，也不会通过互联网传输。

4. 输入 Linux 命令

   你可以输入 `/ssh command [command]` 来与 Linux 电脑进行交互。

   例如：

   ```text
   /ssh command apt install xfce4 xfce4-goodies fonts-wqy* fastfetch -qq -y
   ```

5. 断开连接

   你可以输入 `/ssh disconnect` 来断开与 Linux 电脑的连接。

6. 导入公钥/私钥（正在开发中）

   我们将在未来添加公钥/私钥功能。你可以输入 `/ssh import [Path]` 来导入它。

## 许可证和源代码

© Moerain Software Developments 和 NuanRMxi Lazy Team 2025

本模组根据 [GNU GPLv3](LICENSE.txt) 授权。

源代码可在 [GitHub](https://github.com/NuanRMxi-Lazy-Team/LinuxSSH-MC) 上找到。
