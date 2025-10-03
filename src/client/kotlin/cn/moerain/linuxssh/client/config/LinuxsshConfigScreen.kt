package cn.moerain.linuxssh.client.config

import cn.moerain.linuxssh.config.LinuxsshConfig
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.CyclingButtonWidget
import net.minecraft.text.Text
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FileOutputStream

/**
 * Simple custom configuration screen for LinuxSSH without Cloth Config
 */
object LinuxsshConfigScreen {
    fun create(parent: Screen?): Screen {
        return object : Screen(Text.translatable("linuxssh.config.title")) {
            private val config = LinuxsshConfig.getInstance()
            private var parentScreen: Screen? = parent
            private var showMessage: String? = null

            override fun init() {
                super.init()
                this.clearChildren()

                var y = 40
                val x = this.width / 2 - 150
                val buttonWidth = 300
                val buttonHeight = 20

                // Toggle: prefer key authentication
                addDrawableChild(
                    CyclingButtonWidget.onOffBuilder(config.preferKeyAuthentication)
                        .build(x, y, buttonWidth, buttonHeight,
                            Text.translatable("linuxssh.config.option.prefer_key_authentication")
                        ) { _, value: Boolean ->
                            config.preferKeyAuthentication = value
                            LinuxsshConfig.save()
                        }
                )
                y += 24

                // Toggle: enable key generation
                addDrawableChild(
                    CyclingButtonWidget.onOffBuilder(config.enableKeyGeneration)
                        .build(x, y, buttonWidth, buttonHeight,
                            Text.translatable("linuxssh.config.option.enable_key_generation")
                        ) { _, value: Boolean ->
                            config.enableKeyGeneration = value
                            LinuxsshConfig.save()
                            this.rebuildWidgets()
                        }
                )
                y += 24

                // Toggle: show public key
                addDrawableChild(
                    CyclingButtonWidget.onOffBuilder(config.showPublicKeyPassword)
                        .build(x, y, buttonWidth, buttonHeight,
                            Text.translatable("linuxssh.config.option.show_public_key_password")
                        ) { _, value: Boolean ->
                            config.showPublicKeyPassword = value
                            LinuxsshConfig.save()
                            this.rebuildWidgets()
                        }
                )
                y += 24

                // Toggle: delete host fingerprint flag
                addDrawableChild(
                    CyclingButtonWidget.onOffBuilder(config.deleteHostFingerprint)
                        .build(x, y, buttonWidth, buttonHeight,
                            Text.translatable("linuxssh.config.option.delete_host_fingerprint")
                        ) { _, value: Boolean ->
                            config.deleteHostFingerprint = value
                            LinuxsshConfig.save()
                        }
                )
                y += 28

                val client = MinecraftClient.getInstance()
                val playerUuid = client.player?.uuid?.toString() ?: "unknown"
                val playerKeyDir = File("config/linuxssh/keys/$playerUuid").apply { if (!exists()) mkdirs() }
                val privateKeyFile = File(playerKeyDir, "id_rsa")
                val publicKeyFile = File(playerKeyDir, "id_rsa.pub")

                // Generate keys button
                if (config.enableKeyGeneration) {
                    addDrawableChild(ButtonWidget.builder(
                        Text.translatable("linuxssh.config.option.generate_keys")
                    ) {
                        try {
                            val keyPair = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 2048)
                            keyPair.writePrivateKey(FileOutputStream(privateKeyFile))
                            keyPair.writePublicKey(FileOutputStream(publicKeyFile), "")
                            keyPair.dispose()
                            showMessage = "Keys generated"
                            this.rebuildWidgets()
                        } catch (e: Exception) {
                            showMessage = "Failed to generate keys: ${'$'}{e.message}"
                            e.printStackTrace()
                        }
                    }.dimensions(x, y, buttonWidth, buttonHeight).build())
                    y += 24
                }

                // Show/copy public key if requested
                if (publicKeyFile.exists() && config.showPublicKeyPassword) {
                    val content = try { publicKeyFile.readText() } catch (e: Exception) { "" }
                    addDrawableChild(ButtonWidget.builder(
                        Text.translatable("linuxssh.config.option.copy_public_key")
                    ) {
                        try {
                            val selection = StringSelection(content)
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(selection, selection)
                            showMessage = "Public key copied"
                        } catch (e: Exception) {
                            showMessage = "Failed to copy: ${'$'}{e.message}"
                        }
                    }.dimensions(x, y, buttonWidth, buttonHeight).build())
                    y += 24
                }

                // Manage known_hosts: simple clear button
                addDrawableChild(ButtonWidget.builder(
                    Text.translatable("linuxssh.hostfingerprint.clear_all")
                ) {
                    try {
                        val knownHostsFile = File("config/linuxssh/known_hosts")
                        if (knownHostsFile.exists()) {
                            knownHostsFile.writeText("")
                        }
                        showMessage = "Known hosts cleared"
                    } catch (e: Exception) {
                        showMessage = "Failed to clear: ${'$'}{e.message}"
                    }
                }.dimensions(x, y, buttonWidth, buttonHeight).build())
                y += 28

                // Done button
                addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done")) {
                    LinuxsshConfig.save()
                    this.client?.setScreen(parentScreen)
                }.dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build())
            }

            private fun rebuildWidgets() {
                // Rebuild the screen to reflect changes
                this.clearChildren()
                this.init()
            }

            override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
                // Ensure a proper background is rendered (e.g., dirt/gradient) to avoid visual artifacts
                // Delegate to the default background rendering provided by Screen
                super.renderBackground(context, mouseX, mouseY, delta)
            }

            override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
                // Do not call renderBackground here; Screen.render will handle it once per frame
                super.render(context, mouseX, mouseY, delta)
                // Title
                context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 15, 0xFFFFFF)
                // Optional message line
                showMessage?.let {
                    context.drawCenteredTextWithShadow(textRenderer, Text.literal(it), this.width / 2, 28, 0xA0FFA0)
                }

                // If showing public key, draw a preview text (first 80 chars)
                val client = MinecraftClient.getInstance()
                val playerUuid = client.player?.uuid?.toString() ?: "unknown"
                val publicKeyFile = File("config/linuxssh/keys/$playerUuid/id_rsa.pub")
                if (publicKeyFile.exists() && LinuxsshConfig.getInstance().showPublicKeyPassword) {
                    val preview = try { publicKeyFile.readText().take(80) } catch (_: Exception) { "" }
                    context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(preview), this.width / 2, 120, 0xCCCCCC)
                }
            }

            override fun close() {
                LinuxsshConfig.save()
                this.client?.setScreen(parentScreen)
            }
        }
    }
}