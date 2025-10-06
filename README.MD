# LinuxSSH

> A Chinese version can be [found here](docs/zh/README.MD)

A powerful tool that allows you to manage your Linux PC via SSH in Minecraft as a terminal.

## Requirements

* Fabric Loader 0.17.2
* Fabric API v0.134.1+1.21.10
* Fabric Language Kotlin v1.13.6+kotlin.2.2.20
* Minecraft 1.21.10-rc1

## How to Use?

1. Drag the release JAR file into your mods folder.
2. Launch Minecraft using your preferred launcher (Official, MultiMC, etc.).
3. Load a world and enjoy!

## Command Usage

We provide a command `/ssh` to interact with this mod.

1. Connect to a host

   ```text
   /ssh connect <user@hostname>
   ```

   For example:

   ```text
   /ssh connect celestia@192.168.1.129
   ```

2. Confirm the host's fingerprint

   If you connect to the host for the first time, you may need to confirm a fingerprint from the Linux host. Type `/ssh confirm <yes/no>` to accept or reject the fingerprint.

3. Enter the password

   Then, type `/ssh password` to proceed. We do not store your password locally or transmit it over the Internet.

4. Type the Linux command

   You can type `/ssh command [command]` to interact with the Linux PC.

   For example:

   ```text
   /ssh command apt install xfce4 xfce4-goodies fonts-wqy* fastfetch -qq -y
   ```

5. Disconnect

   You can type `/ssh disconnect` to disconnect from the Linux PC.

6. Import the public/private key (Under Construction)

   We will add the public/private key functionality in the future. You can type `/ssh import [Path]` to import it.

## License and Source Code

© Moerain Software Developments and NuanRMxi Lazy Team 2025

This mod is licensed [under GNU GPLv3](LICENSE.txt).

Source code can be found at [GitHub](https://github.com/NuanRMxi-Lazy-Team/LinuxSSH-MC)
