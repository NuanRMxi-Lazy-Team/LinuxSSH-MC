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
import java.io.BufferedReader
import java.io.File
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

        // Register the SSH command
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerSshCommand(dispatcher)
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

                // Copy the key file to the player's directory
                val targetFile = File(playerKeyDir, keyFile.name)
                keyFile.copyTo(targetFile, overwrite = true)

                source.sendFeedback({ Text.translatable("linuxssh.command.key_imported", keyFile.name) }, false)
            } catch (e: Exception) {
                source.sendFeedback({ Text.translatable("linuxssh.command.key_import_error", e.message) }, false)
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
                                        source.sendFeedback({ Text.translatable("linuxssh.command.key_import_error", e.message) }, false)
                                    }
                                }
                            }
                        }
                    }

                    val session = jsch.getSession(username, host, 22)

                    // Configure session to ask for fingerprint confirmation
                    session.setConfig("StrictHostKeyChecking", "ask")

                    // Create a custom UserInfo to handle fingerprint verification
                    if (player != null) {
                        session.setUserInfo(object : UserInfo {
                            override fun getPassword(): String? = null
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
                            override fun promptPassword(message: String): Boolean = false
                            override fun promptPassphrase(message: String): Boolean = false
                            override fun getPassphrase(): String? = null
                            override fun showMessage(message: String) {
                                source.sendFeedback({ Text.translatable("linuxssh.command.fingerprint_prompt") }, false)
                            }
                        })
                    }

                    source.sendFeedback({ Text.translatable("linuxssh.command.connect", "$username@$host") }, false)

                    // Handle password authentication
                    if (player != null) {
                        val passwordPromise = CompletableFuture<String>()

                        pendingPasswords[player.uuid] = { password ->
                            passwordPromise.complete(password)
                        }

                        source.sendFeedback({ Text.translatable("linuxssh.command.password_prompt") }, false)

                        // Set password when provided
                        try {
                            val password = passwordPromise.get() // This will block until password is provided
                            session.setPassword(password)
                        } catch (e: Exception) {
                            source.sendFeedback({ Text.translatable("linuxssh.command.password_prompt") }, false)
                            pendingPasswords.remove(player.uuid)
                            return@runAsync
                        }
                    }

                    // Connect to the SSH server
                    session.connect(30000) // 30 second timeout

                    player?.let {
                        activeSessions[it.uuid] = session
                    }

                    source.sendFeedback({ Text.translatable("linuxssh.command.connected", host) }, false)
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
                val reader = BufferedReader(InputStreamReader(inputStream))

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