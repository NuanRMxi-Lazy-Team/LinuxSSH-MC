package cn.moerain.linuxssh

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.UserInfo
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.KeyPair
import cn.moerain.linuxssh.config.LinuxsshConfig
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.CompletableFuture

class Linuxssh : ModInitializer {
    // Map to store active SSH sessions for players
    private val activeSessions = mutableMapOf<UUID, Session>()

    // Map to store pending password inputs for players
    private val pendingPasswords = mutableMapOf<UUID, (String) -> Unit>()

    // Map to store pending fingerprint confirmations for players
    private val pendingFingerprints = mutableMapOf<UUID, (Boolean) -> Unit>()

    // Directory to store SSH keys
    private val sshKeysDir = File("config/linuxssh/keys")

    // Directory to store known hosts
    private val knownHostsFile = File("config/linuxssh/known_hosts")

    override fun onInitialize() {
        // Create SSH keys directory if it doesn't exist
        if (!sshKeysDir.exists()) {
            sshKeysDir.mkdirs()
        }

        // Create parent directory for known_hosts if it doesn't exist
        if (!knownHostsFile.parentFile.exists()) {
            knownHostsFile.parentFile.mkdirs()
        }

        // Create known_hosts file if it doesn't exist
        if (!knownHostsFile.exists()) {
            knownHostsFile.createNewFile()
        }

        // Initialize configuration
        LinuxsshConfig.getInstance()

        // Register the SSH command
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerSshCommand(dispatcher)
        }
    }

    /**
     * Delete a host fingerprint from the known_hosts file
     *
     * @param host The host to delete
     * @return True if the host was found and deleted, false otherwise
     */
    private fun deleteHostFingerprint(host: String): Boolean {
        try {
            val jsch = JSch()
            jsch.setKnownHosts(knownHostsFile.absolutePath)
            val hostKeyRepository = jsch.hostKeyRepository

            // Find the host key to delete
            val hostKeys = hostKeyRepository.getHostKey()
            var found = false

            for (hostKey in hostKeys) {
                if (hostKey.host == host) {
                    // Remove the host key
                    hostKeyRepository.remove(host, hostKey.type)
                    found = true
                    break
                }
            }

            return found
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun registerSshCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("ssh")
                .then(
                    CommandManager.literal("disconnect")
                        .executes { context -> handleDisconnect(context) }
                )
                .then(
                    CommandManager.literal("password")
                        .then(
                            CommandManager.argument("password", StringArgumentType.greedyString())
                                .executes { context -> handlePasswordInput(context) }
                        )
                )
                .then(
                    CommandManager.literal("confirm")
                        .then(
                            CommandManager.literal("yes")
                                .executes { context -> handleFingerprintConfirmation(context, true) }
                        )
                        .then(
                            CommandManager.literal("no")
                                .executes { context -> handleFingerprintConfirmation(context, false) }
                        )
                )
                .then(
                    CommandManager.literal("key")
                        .then(
                            CommandManager.literal("import")
                                .then(
                                    CommandManager.argument("keypath", StringArgumentType.greedyString())
                                        .executes { context -> handleKeyImport(context) }
                                )
                        )
                        .then(
                            CommandManager.literal("generate")
                                .executes { context -> handleKeyGenerate(context) }
                        )
                )
                .then(
                    CommandManager.literal("connect")
                        .then(
                            CommandManager.argument("connection", StringArgumentType.greedyString())
                                .executes { context -> handleSshConnect(context) }
                        )
                )
                .then(
                    CommandManager.literal("command")
                        .then(
                            CommandManager.argument("command", StringArgumentType.greedyString())
                                .executes { context -> handleSshCommand(context) }
                        )
                )
        )
    }

    private fun handleFingerprintConfirmation(context: CommandContext<ServerCommandSource>, confirmed: Boolean): Int {
        val source = context.source
        val player = source.player ?: return 0

        val fingerprintCallback = pendingFingerprints[player.uuid]
        if (fingerprintCallback != null) {
            fingerprintCallback(confirmed)
            pendingFingerprints.remove(player.uuid)
            if (confirmed) {
                source.sendFeedback({ Text.translatable("linuxssh.command.fingerprint_accepted") }, false)
            } else {
                source.sendFeedback({ Text.translatable("linuxssh.command.fingerprint_rejected") }, false)
            }
        } else {
            source.sendFeedback({ Text.translatable("linuxssh.command.not_connected") }, false)
        }

        return 1
    }

    private fun handleDisconnect(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: return 0

        val session = activeSessions[player.uuid]
        if (session != null && session.isConnected) {
            session.disconnect()
            activeSessions.remove(player.uuid)
            source.sendFeedback({ Text.translatable("linuxssh.command.disconnect") }, false)
        } else {
            source.sendFeedback({ Text.translatable("linuxssh.command.not_connected") }, false)
        }

        return 1
    }

    private fun handlePasswordInput(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: return 0
        val password = StringArgumentType.getString(context, "password")

        val passwordCallback = pendingPasswords[player.uuid]
        if (passwordCallback != null) {
            passwordCallback(password)
            pendingPasswords.remove(player.uuid)
            source.sendFeedback({ Text.translatable("linuxssh.command.password_prompt") }, false)
        } else {
            source.sendFeedback({ Text.translatable("linuxssh.command.not_connected") }, false)
        }

        return 1
    }

    private fun handleKeyImport(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: return 0
        val keyPath = StringArgumentType.getString(context, "keypath")

        CompletableFuture.runAsync {
            try {
                val keyFile = File(keyPath)
                if (!keyFile.exists() || !keyFile.isFile) {
                    source.sendFeedback({ Text.translatable("linuxssh.command.key_import_error", keyPath) }, false)
                    return@runAsync
                }

                // Create player-specific directory
                val playerKeyDir = File(sshKeysDir, player.uuid.toString())
                if (!playerKeyDir.exists()) {
                    playerKeyDir.mkdirs()
                }

                // Validate the key format by trying to add it to JSch
                val jsch = JSch()
                try {
                    // Try to add the key to JSch to validate its format
                    jsch.addIdentity(keyFile.absolutePath)
                } catch (e: JSchException) {
                    // If the key format is invalid, inform the user
                    val errorMsg = e.message?.let {
                        if (it.contains("[B@")) {
                            "invalid privatekey format"
                        } else {
                            it
                        }
                    } ?: "unknown error"
                    source.sendFeedback({ Text.translatable("linuxssh.command.key_import_error", errorMsg) }, false)
                    return@runAsync
                }

                // Copy the key file to the player's directory
                val targetFile = File(playerKeyDir, keyFile.name)
                keyFile.copyTo(targetFile, overwrite = true)

                source.sendFeedback({ Text.translatable("linuxssh.command.key_imported", keyFile.name) }, false)

                // Display the key content for copying and viewing
                val keyContent = keyFile.readText()
                source.sendFeedback({ Text.translatable("linuxssh.key.imported_content_header") }, false)
                source.sendFeedback({ Text.literal("§a$keyContent") }, false)
                source.sendFeedback({ Text.translatable("linuxssh.key.imported_content_footer") }, false)

                // If this is a private key, check if there's a corresponding public key
                if (!keyFile.name.endsWith(".pub")) {
                    val publicKeyFile = File(keyPath + ".pub")
                    if (publicKeyFile.exists() && publicKeyFile.isFile) {
                        // Copy the public key file to the player's directory
                        val targetPublicFile = File(playerKeyDir, publicKeyFile.name)
                        publicKeyFile.copyTo(targetPublicFile, overwrite = true)

                        // Display the public key content
                        val publicKeyContent = publicKeyFile.readText()
                        source.sendFeedback({ Text.translatable("linuxssh.key.imported_public_content_header") }, false)
                        source.sendFeedback({ Text.literal("§a$publicKeyContent") }, false)
                        source.sendFeedback({ Text.translatable("linuxssh.key.imported_public_content_footer") }, false)

                        source.sendFeedback({ Text.translatable("linuxssh.command.key_imported", publicKeyFile.name) }, false)
                    }
                }
            } catch (e: Exception) {
                source.sendFeedback({ Text.translatable("linuxssh.command.key_import_error", e.message) }, false)
                e.printStackTrace()
            }
        }

        return 1
    }

    private fun handleKeyGenerate(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: return 0

        // Check if key generation is enabled in config
        val config = LinuxsshConfig.getInstance()
        if (!config.enableKeyGeneration) {
            source.sendFeedback({ Text.translatable("linuxssh.command.key_generation_disabled") }, false)
            return 1
        }

        CompletableFuture.runAsync {
            try {
                // Create player-specific directory
                val playerKeyDir = File(sshKeysDir, player.uuid.toString())
                if (!playerKeyDir.exists()) {
                    playerKeyDir.mkdirs()
                }

                // Define key files
                val privateKeyFile = File(playerKeyDir, "id_rsa")
                val publicKeyFile = File(playerKeyDir, "id_rsa.pub")

                // Generate a new key pair
                val keyPair = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 2048)

                // Save the private key
                keyPair.writePrivateKey(FileOutputStream(privateKeyFile), ByteArray(0))

                // Save the public key
                keyPair.writePublicKey(FileOutputStream(publicKeyFile), "")

                // Dispose of the key pair
                keyPair.dispose()

                source.sendFeedback({ Text.translatable("linuxssh.command.key_generated") }, false)

                // Display the private key content for copying and viewing
                val privateKeyContent = privateKeyFile.readText()
                source.sendFeedback({ Text.translatable("linuxssh.key.generated_private_header") }, false)
                source.sendFeedback({ Text.literal("§a$privateKeyContent") }, false)
                source.sendFeedback({ Text.translatable("linuxssh.key.generated_private_footer") }, false)

                // Display the public key content for copying and viewing
                val publicKeyContent = publicKeyFile.readText()
                source.sendFeedback({ Text.translatable("linuxssh.key.generated_public_header") }, false)
                source.sendFeedback({ Text.literal("§a$publicKeyContent") }, false)
                source.sendFeedback({ Text.translatable("linuxssh.key.generated_public_footer") }, false)

                // Additional message for using the keys
                source.sendFeedback({ Text.translatable("linuxssh.command.key_usage_info") }, false)
            } catch (e: Exception) {
                source.sendFeedback({ Text.translatable("linuxssh.command.key_generation_error", e.message) }, false)
                e.printStackTrace()
            }
        }

        return 1
    }

    private fun handleSshConnect(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player
        val connectionString = StringArgumentType.getString(context, "connection")

        // Run SSH operations in a separate thread to avoid blocking the main game thread
        CompletableFuture.runAsync {
            try {
                // Check if the input is a connection string like "user@host"
                if (connectionString.contains("@")) {
                    val parts = connectionString.split("@")
                    if (parts.size != 2) {
                        source.sendFeedback({ Text.translatable("linuxssh.command.invalid_format") }, false)
                        return@runAsync
                    }

                    val username = parts[0]
                    val host = parts[1]

                    // Close existing session if any
                    player?.let {
                        activeSessions[it.uuid]?.disconnect()
                    }

                    // Create new SSH session
                    val jsch = JSch()

                    // Set known hosts file
                    jsch.setKnownHosts(knownHostsFile.absolutePath)

                    // Add identity (SSH key) if available
                    player?.let {
                        val playerKeyDir = File(sshKeysDir, it.uuid.toString())
                        if (playerKeyDir.exists() && playerKeyDir.isDirectory) {
                            playerKeyDir.listFiles()?.forEach { keyFile ->
                                if (keyFile.isFile) {
                                    try {
                                        jsch.addIdentity(keyFile.absolutePath)
                                        source.sendFeedback({ Text.translatable("linuxssh.command.key_imported", keyFile.name) }, false)
                                    } catch (e: JSchException) {
                                        // Clean up error message to avoid displaying byte arrays
                                        val errorMsg = e.message?.let {
                                            if (it.contains("[B@")) {
                                                "invalid privatekey format"
                                            } else {
                                                it
                                            }
                                        } ?: "unknown error"
                                        source.sendFeedback({ Text.translatable("linuxssh.command.key_import_error", errorMsg) }, false)
                                    }
                                }
                            }
                        }
                    }

                    // Check if we should delete the host fingerprint
                    val config = LinuxsshConfig.getInstance()
                    if (config.deleteHostFingerprint) {
                        if (deleteHostFingerprint(host)) {
                            source.sendFeedback({ Text.translatable("linuxssh.command.fingerprint_deleted", host) }, false)
                        }
                    }

                    val session = jsch.getSession(username, host, 22)

                    // Configure session to ask for fingerprint confirmation
                    session.setConfig("StrictHostKeyChecking", "ask")

                    // Create a custom UserInfo to handle fingerprint verification
                    if (player != null) {
                        session.setUserInfo(object : UserInfo {
                            override fun getPassword(): String? {
                                // This method is called by JSch to get the password
                                // We'll return null here as we're handling password input separately
                                return null
                            }
                            override fun promptYesNo(message: String): Boolean {
                                // Extract fingerprint from message
                                val fingerprintPromise = CompletableFuture<Boolean>()

                                pendingFingerprints[player.uuid] = { confirmed ->
                                    fingerprintPromise.complete(confirmed)
                                }

                                // Format and display the fingerprint message
                                val formattedMessage = message.replace("\n", " ")
                                source.sendFeedback({ Text.literal("§e$formattedMessage") }, false)
                                source.sendFeedback({ Text.translatable("linuxssh.command.fingerprint_prompt") }, false)

                                // Wait for user confirmation
                                return try {
                                    fingerprintPromise.get() // This will block until confirmation is provided
                                } catch (e: Exception) {
                                    source.sendFeedback({ Text.translatable("linuxssh.command.fingerprint_rejected") }, false)
                                    pendingFingerprints.remove(player.uuid)
                                    false
                                }
                            }
                            override fun promptPassword(message: String): Boolean {
                                // Return true to indicate we'll handle password input
                                // This allows JSch to proceed with password authentication
                                return true
                            }
                            override fun promptPassphrase(message: String): Boolean = false
                            override fun getPassphrase(): String? = null
                            override fun showMessage(message: String) {
                                source.sendFeedback({ Text.translatable("linuxssh.command.fingerprint_prompt") }, false)
                            }
                        })
                    }

                    source.sendFeedback({ Text.translatable("linuxssh.command.connect", "$username@$host") }, false)

                    // Check if we should try key authentication first
                    var authSuccess = false

                    if (config.preferKeyAuthentication) {
                        try {
                            // Try to connect with key authentication
                            session.connect(5000) // 5 second timeout for key auth
                            authSuccess = true
                            source.sendFeedback({ Text.translatable("linuxssh.command.connected", host) }, false)
                        } catch (e: Exception) {
                            // Key authentication failed, fall back to password
                            source.sendFeedback({ Text.translatable("linuxssh.command.key_auth_failed") }, false)
                        }
                    }

                    // If key authentication failed or is not preferred, use password
                    if (!authSuccess && player != null) {
                        val passwordPromise = CompletableFuture<String>()

                        pendingPasswords[player.uuid] = { password ->
                            passwordPromise.complete(password)
                        }

                        source.sendFeedback({ Text.translatable("linuxssh.command.password_prompt") }, false)

                        // Set password when provided
                        try {
                            val password = passwordPromise.get() // This will block until password is provided
                            session.setPassword(password)

                            // Connect with password
                            session.connect(30000) // 30 second timeout for password auth
                            authSuccess = true
                            source.sendFeedback({ Text.translatable("linuxssh.command.connected", host) }, false)
                        } catch (e: Exception) {
                            source.sendFeedback({ Text.translatable("linuxssh.command.connection_failed", e.message) }, false)
                            pendingPasswords.remove(player.uuid)
                            return@runAsync
                        }
                    }

                    player?.let {
                        activeSessions[it.uuid] = session
                    }
                } else {
                    source.sendFeedback({ Text.translatable("linuxssh.command.invalid_format") }, false)
                }
            } catch (e: Exception) {
                source.sendFeedback({ Text.translatable("linuxssh.command.error", e.message) }, false)
                e.printStackTrace()
            }
        }

        return 1
    }

    private fun handleSshCommand(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player
        val command = StringArgumentType.getString(context, "command")

        // Run SSH operations in a separate thread to avoid blocking the main game thread
        CompletableFuture.runAsync {
            try {
                val session = player?.let { activeSessions[it.uuid] }

                if (session == null || !session.isConnected) {
                    source.sendFeedback({ Text.translatable("linuxssh.command.not_connected") }, false)
                    return@runAsync
                }

                // Execute the command
                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(command)

                // Get command output
                val inputStream = channel.inputStream
                // Use UTF-8 encoding to properly handle Chinese and other non-Latin characters
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))

                channel.connect()

                // Read and display output
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    source.sendFeedback({ Text.translatable("linuxssh.command.command_output", line) }, false)
                }

                channel.disconnect()
            } catch (e: Exception) {
                source.sendFeedback({ Text.translatable("linuxssh.command.error", e.message) }, false)
                e.printStackTrace()
            }
        }

        return 1
    }
}