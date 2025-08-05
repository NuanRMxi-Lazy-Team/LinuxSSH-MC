package cn.moerain.linuxssh.client.config

import cn.moerain.linuxssh.config.LinuxsshConfig
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigCategory
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.client.MinecraftClient
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Configuration screen provider for LinuxSSH mod
 */
object LinuxsshConfigScreen {
    /**
     * Create a configuration screen
     *
     * @param parent The parent screen
     * @return The configuration screen
     */
    fun create(parent: Screen?): Screen {
        val config = LinuxsshConfig.getInstance()
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.translatable("linuxssh.config.title"))
            .setSavingRunnable { LinuxsshConfig.save() }

        val entryBuilder = builder.entryBuilder()

        // Create SSH category
        val sshCategory = builder.getOrCreateCategory(Text.translatable("linuxssh.config.category.ssh"))

        // Add delete host fingerprint option
        sshCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("linuxssh.config.option.delete_host_fingerprint"),
                config.deleteHostFingerprint
            )
            .setDefaultValue(false)
            .setTooltip(Text.translatable("linuxssh.config.option.delete_host_fingerprint.tooltip"))
            .setSaveConsumer { value -> config.deleteHostFingerprint = value }
            .build()
        )

        // Create SSH Key Management category
        val keyCategory = builder.getOrCreateCategory(Text.translatable("linuxssh.config.category.keys"))

        // Add prefer key authentication option
        keyCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("linuxssh.config.option.prefer_key_authentication"),
                config.preferKeyAuthentication
            )
            .setDefaultValue(true)
            .setTooltip(Text.translatable("linuxssh.config.option.prefer_key_authentication.tooltip"))
            .setSaveConsumer { value -> config.preferKeyAuthentication = value }
            .build()
        )

        // Add enable key generation option
        keyCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("linuxssh.config.option.enable_key_generation"),
                config.enableKeyGeneration
            )
            .setDefaultValue(true)
            .setTooltip(Text.translatable("linuxssh.config.option.enable_key_generation.tooltip"))
            .setSaveConsumer { value -> config.enableKeyGeneration = value }
            .build()
        )

        // Add show public key password option
        keyCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Text.translatable("linuxssh.config.option.show_public_key_password"),
                config.showPublicKeyPassword
            )
            .setDefaultValue(false)
            .setTooltip(Text.translatable("linuxssh.config.option.show_public_key_password.tooltip"))
            .setSaveConsumer { value -> config.showPublicKeyPassword = value }
            .build()
        )

        // Get the current user's UUID
        val client = MinecraftClient.getInstance()
        val playerUuid = client.player?.uuid?.toString() ?: "unknown"

        // Get the SSH keys directory for the player
        val playerKeyDir = File("config/linuxssh/keys/$playerUuid")
        if (!playerKeyDir.exists()) {
            playerKeyDir.mkdirs()
        }

        // Check for existing key pair
        val privateKeyFile = File(playerKeyDir, "id_rsa")
        val publicKeyFile = File(playerKeyDir, "id_rsa.pub")

        // Add generate keys button
        if (config.enableKeyGeneration) {
            keyCategory.addEntry(
                entryBuilder.startBooleanToggle(
                    Text.translatable("linuxssh.config.option.generate_keys"),
                    false
                )
                .setDefaultValue(false)
                .setTooltip(Text.translatable("linuxssh.config.option.generate_keys.tooltip"))
                .setSaveConsumer { value ->
                    if (value) {
                        try {
                            // Generate a new key pair
                            val keyPair = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 2048)

                            // Save the private key
                            keyPair.writePrivateKey(FileOutputStream(privateKeyFile))

                            // Save the public key
                            keyPair.writePublicKey(FileOutputStream(publicKeyFile), "")

                            // Dispose of the key pair
                            keyPair.dispose()

                            // Refresh the screen to show the updated keys
                            client.setScreen(create(client.currentScreen))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                .build()
            )
        }

        // Display public key if it exists and option is enabled
        if (publicKeyFile.exists() && config.showPublicKeyPassword) {
            // Read the public key content
            val publicKeyContent = publicKeyFile.readText()

            // Add public key display
            keyCategory.addEntry(
                entryBuilder.startTextDescription(Text.translatable("linuxssh.config.public_key_label"))
                    .build()
            )

            keyCategory.addEntry(
                entryBuilder.startTextDescription(Text.literal(publicKeyContent))
                    .build()
            )

            // Add copy button
            keyCategory.addEntry(
                entryBuilder.startBooleanToggle(
                    Text.translatable("linuxssh.config.option.copy_public_key"),
                    false
                )
                .setDefaultValue(false)
                .setTooltip(Text.translatable("linuxssh.config.option.copy_public_key.tooltip"))
                .setSaveConsumer { value ->
                    if (value) {
                        try {
                            // Copy to clipboard
                            val selection = StringSelection(publicKeyContent)
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(selection, selection)

                            // Refresh the screen
                            client.setScreen(create(client.currentScreen))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                .build()
            )
        }

        // Create Host Fingerprints category
        val fingerprintCategory = builder.getOrCreateCategory(Text.translatable("linuxssh.hostfingerprint.category"))

        // Get the known hosts file
        val knownHostsFile = File("config/linuxssh/known_hosts")
        if (!knownHostsFile.exists()) {
            knownHostsFile.parentFile.mkdirs()
            knownHostsFile.createNewFile()
        }

        // Get all host keys
        val jsch = JSch()
        jsch.setKnownHosts(knownHostsFile.absolutePath)
        val hostKeyRepository = jsch.hostKeyRepository
        val hostKeys = hostKeyRepository.hostKey

        if (hostKeys == null || hostKeys.isEmpty()) {
            // No host keys found
            fingerprintCategory.addEntry(
                entryBuilder.startTextDescription(Text.translatable("linuxssh.hostfingerprint.no_hosts"))
                    .build()
            )
        } else {
            // Add entries for each host
            for (hostKey in hostKeys) {
                val host = hostKey.host
                val type = hostKey.type
                val fingerprint = hostKey.getFingerPrint(jsch)

                // Create a subcategory for this host
                fingerprintCategory.addEntry(
                    entryBuilder.startSubCategory(Text.literal(host), listOf(
                        entryBuilder.startTextDescription(Text.translatable("linuxssh.hostfingerprint.type", type)).build(),
                        entryBuilder.startTextDescription(Text.translatable("linuxssh.hostfingerprint.fingerprint", fingerprint)).build(),
                        // Create a submenu for managing this fingerprint
                        entryBuilder.startSubCategory(
                            Text.translatable("linuxssh.hostfingerprint.manage"),
                            listOf(
                                entryBuilder.startBooleanToggle(
                                    Text.translatable("linuxssh.hostfingerprint.delete"),
                                    false
                                )
                                .setDefaultValue(false)
                                .setSaveConsumer { value ->
                                    if (value) {
                                        // Delete the host fingerprint
                                        hostKeyRepository.remove(host, type)
                                        // Refresh the screen to show the updated list
                                        val client = MinecraftClient.getInstance()
                                        client.setScreen(create(client.currentScreen))
                                    }
                                }
                                .build()
                            )
                        ).build()
                    )).build()
                )
            }
        }

        return builder.build()
    }
}