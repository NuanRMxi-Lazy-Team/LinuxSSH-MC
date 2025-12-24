package cn.moerain.linuxssh.client.config

import cn.moerain.linuxssh.config.LinuxsshConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FileOutputStream

/**
 * Simple custom configuration screen for LinuxSSH without Cloth Config
 * @author Celesita
 */
object LinuxsshConfigScreen {
    fun create(parent: Screen?): Screen {
        return object : Screen(Component.translatable("linuxssh.config.title")) {
            private val config = LinuxsshConfig.getInstance()
            private var parentScreen: Screen? = parent
            private var showMessage: String? = null

            override fun init() {
                super.init()
                this.clearWidgets()

                var y = 40
                val x = this.width / 2 - 150
                val buttonWidth = 300
                val buttonHeight = 20

                // Toggle: prefer key authentication
                addRenderableWidget(
                    CycleButton.onOffBuilder(config.preferKeyAuthentication)
                        .create(x, y, buttonWidth, buttonHeight,
                            Component.translatable("linuxssh.config.option.prefer_key_authentication")
                        ) { _, value: Boolean ->
                            config.preferKeyAuthentication = value
                            LinuxsshConfig.save()
                        }
                )
                y += 24

                // Toggle: enable key generation
                addRenderableWidget(
                    CycleButton.onOffBuilder(config.enableKeyGeneration)
                        .create(x, y, buttonWidth, buttonHeight,
                            Component.translatable("linuxssh.config.option.enable_key_generation")
                        ) { _, value: Boolean ->
                            config.enableKeyGeneration = value
                            LinuxsshConfig.save()
                            this.refreshWidgets()
                        }
                )
                y += 24

                // Toggle: show public key
                addRenderableWidget(
                    CycleButton.onOffBuilder(config.showPublicKeyPassword)
                        .create(x, y, buttonWidth, buttonHeight,
                            Component.translatable("linuxssh.config.option.show_public_key_password")
                        ) { _, value: Boolean ->
                            config.showPublicKeyPassword = value
                            LinuxsshConfig.save()
                            this.refreshWidgets()
                        }
                )
                y += 24

                // Toggle: delete host fingerprint flag
                addRenderableWidget(
                    CycleButton.onOffBuilder(config.deleteHostFingerprint)
                        .create(x, y, buttonWidth, buttonHeight,
                            Component.translatable("linuxssh.config.option.delete_host_fingerprint")
                        ) { _, value: Boolean ->
                            config.deleteHostFingerprint = value
                            LinuxsshConfig.save()
                        }
                )
                y += 28

                val client = Minecraft.getInstance()
                val playerUuid = client.player?.uuid?.toString() ?: "unknown"
                val playerKeyDir = File("config/linuxssh/keys/$playerUuid").apply { if (!exists()) mkdirs() }
                val privateKeyFile = File(playerKeyDir, "id_rsa")
                val publicKeyFile = File(playerKeyDir, "id_rsa.pub")

                // Generate keys button
                if (config.enableKeyGeneration) {
                    addRenderableWidget(Button.builder(
                        Component.translatable("linuxssh.config.option.generate_keys")
                    ) {
                        try {
                            val keyPair = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 2048)
                            keyPair.writePrivateKey(FileOutputStream(privateKeyFile))
                            keyPair.writePublicKey(FileOutputStream(publicKeyFile), "")
                            keyPair.dispose()
                            showMessage = "Keys generated"
                            this.refreshWidgets()
                        } catch (e: Exception) {
                            showMessage = "Failed to generate keys: ${e.message}"
                            e.printStackTrace()
                        }
                    }.bounds(x, y, buttonWidth, buttonHeight).build())
                    y += 24
                }

                // Show/copy public key if requested
                if (publicKeyFile.exists() && config.showPublicKeyPassword) {
                    val content = try { publicKeyFile.readText() } catch (e: Exception) { "" }
                    addRenderableWidget(Button.builder(
                        Component.translatable("linuxssh.config.option.copy_public_key")
                    ) {
                        try {
                            val selection = StringSelection(content)
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(selection, selection)
                            showMessage = "Public key copied"
                        } catch (e: Exception) {
                            showMessage = "Failed to copy: ${e.message}"
                        }
                    }.bounds(x, y, buttonWidth, buttonHeight).build())
                    y += 24
                }

                // Manage known_hosts: simple clear button
                addRenderableWidget(Button.builder(
                    Component.translatable("linuxssh.hostfingerprint.clear_all")
                ) {
                    try {
                        val knownHostsFile = File("config/linuxssh/known_hosts")
                        if (knownHostsFile.exists()) {
                            knownHostsFile.writeText("")
                        }
                        showMessage = "Known hosts cleared"
                    } catch (e: Exception) {
                        showMessage = "Failed to clear: ${e.message}"
                    }
                }.bounds(x, y, buttonWidth, buttonHeight).build())
                y += 28

                // Done button
                addRenderableWidget(Button.builder(Component.translatable("gui.done")) {
                    LinuxsshConfig.save()
                    showSaveToast()
                    this.minecraft?.setScreen(parentScreen)
                }.bounds(this.width / 2 - 100, this.height - 28, 200, 20).build())
            }

            private fun refreshWidgets() {
                // Rebuild the screen to reflect changes
                this.clearWidgets()
                this.init()
            }

            override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
                // Ensure a proper background is rendered (e.g., dirt/gradient) to avoid visual artifacts
                // Delegate to the default background rendering provided by Screen
                super.renderBackground(graphics, mouseX, mouseY, delta)
            }

            override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
                // Do not call renderBackground here; Screen.render will handle it once per frame
                super.render(graphics, mouseX, mouseY, delta)
                // Title
                graphics.drawCenteredString(font, this.title, this.width / 2, 15, 0xFFFFFF)
                // Optional message line
                showMessage?.let {
                    graphics.drawCenteredString(font, Component.literal(it), this.width / 2, 28, 0xA0FFA0)
                }

                // If showing public key, draw a preview text (first 80 chars)
                val client = Minecraft.getInstance()
                val playerUuid = client.player?.uuid?.toString() ?: "unknown"
                val publicKeyFile = File("config/linuxssh/keys/$playerUuid/id_rsa.pub")
                if (publicKeyFile.exists() && LinuxsshConfig.getInstance().showPublicKeyPassword) {
                    val preview = try { publicKeyFile.readText().take(80) } catch (_: Exception) { "" }
                    graphics.drawCenteredString(font,
                        Component.literal(preview), this.width / 2, 120, 0xCCCCCC)
                }
            }

            override fun removed() {
                LinuxsshConfig.save()
            }

            override fun onClose() {
                this.minecraft?.setScreen(parentScreen)
            }

            private fun showSaveToast() {
                val client = Minecraft.getInstance()
                client.toastManager.addToast(
                    SystemToast(
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("linuxssh.config.saved.title"),
                        Component.translatable("linuxssh.config.saved.message")
                    )
                )
            }
        }
    }
}