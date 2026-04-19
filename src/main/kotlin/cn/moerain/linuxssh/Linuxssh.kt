package cn.moerain.linuxssh

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.commands.ServerPackCommand
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.UserInfo
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.KeyPair
import cn.moerain.linuxssh.config.LinuxsshConfig
import cn.moerain.linuxssh.util.ColorUtil
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.CompletableFuture

class Linuxssh : ModInitializer {
    // Map to store active SSH sessions for players
    companion object {
        @JvmField
        val activeSessions = mutableMapOf<UUID, Session>()
    }
    
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
        LinuxsshConfig.load()

        /*
        // Register the SSH command
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerSshCommand(dispatcher)
        }
        */
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

    fun registerSshCommand(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("ssh").apply {
                then(Commands.literal("disconnect").executes { handleDisconnect(it.source) })
                then(Commands.literal("password").then(
                    Commands.argument("password", StringArgumentType.greedyString()).executes { handlePasswordInput(it.source, StringArgumentType.getString(it, "password")) }
                ))
                then(Commands.literal("confirm").apply {
                    then(Commands.literal("yes").executes { handleFingerprintConfirmation(it.source, true) })
                    then(Commands.literal("no").executes { handleFingerprintConfirmation(it.source, false) })
                })
                then(Commands.literal("key").apply {
                    then(Commands.literal("import").then(
                        Commands.argument("keypath", StringArgumentType.greedyString()).executes { handleKeyImport(it.source, StringArgumentType.getString(it, "keypath")) }
                    ))
                    then(Commands.literal("generate").executes { handleKeyGenerate(it.source) })
                })
                then(Commands.literal("connect").then(
                    Commands.argument("connection", StringArgumentType.greedyString()).executes { handleSshConnect(it.source, StringArgumentType.getString(it, "connection")) }
                ))
                then(Commands.literal("command").then(
                    Commands.argument("command", StringArgumentType.greedyString()).executes { handleSshCommand(it.source, StringArgumentType.getString(it, "command")) }
                ))
            }
        )
    }

    fun handleFingerprintConfirmation(source: Any, confirmed: Boolean): Int {
        val player = try {
            val getPlayerMethod = source.javaClass.getMethod("getPlayer")
            getPlayerMethod.invoke(source) as? net.minecraft.world.entity.player.Player
        } catch (e: Exception) {
            null
        } ?: return 0

        val fingerprintCallback = pendingFingerprints[player.uuid]
        if (fingerprintCallback != null) {
            fingerprintCallback(confirmed)
            pendingFingerprints.remove(player.uuid)
            if (confirmed) {
                sendFeedback(source, Component.translatable("linuxssh.command.fingerprint_accepted"))
            } else {
                sendFeedback(source, Component.translatable("linuxssh.command.fingerprint_rejected"))
            }
        } else {
            sendFeedback(source, Component.translatable("linuxssh.command.not_connected"))
        }

        return 1
    }

    fun handleDisconnect(source: Any): Int {
        val player = try {
            val getPlayerMethod = source.javaClass.getMethod("getPlayer")
            getPlayerMethod.invoke(source) as? net.minecraft.world.entity.player.Player
        } catch (e: Exception) {
            null
        } ?: return 0

        val session = activeSessions[player.uuid]
        if (session != null && session.isConnected) {
            session.disconnect()
            activeSessions.remove(player.uuid)
            sendFeedback(source, Component.translatable("linuxssh.command.disconnect"))
        } else {
            sendFeedback(source, Component.translatable("linuxssh.command.not_connected"))
        }

        return 1
    }

    fun handlePasswordInput(source: Any, password: String): Int {
        val player = try {
            val getPlayerMethod = source.javaClass.getMethod("getPlayer")
            getPlayerMethod.invoke(source) as? net.minecraft.world.entity.player.Player
        } catch (e: Exception) {
            null
        } ?: return 0

        val passwordCallback = pendingPasswords[player.uuid]
        if (passwordCallback != null) {
            passwordCallback(password)
            pendingPasswords.remove(player.uuid)
            sendFeedback(source, Component.translatable("linuxssh.command.password_received"))
        } else {
            sendFeedback(source, Component.translatable("linuxssh.command.not_connected"))
        }

        return 1
    }

    fun handleKeyImport(source: Any, keyPath: String): Int {
        val player = try {
            val getPlayerMethod = source.javaClass.getMethod("getPlayer")
            getPlayerMethod.invoke(source) as? net.minecraft.world.entity.player.Player
        } catch (e: Exception) {
            null
        } ?: return 0

        CompletableFuture.runAsync {
            try {
                val keyFile = File(keyPath)
                if (!keyFile.exists() || !keyFile.isFile) {
                    sendFeedback(source, Component.translatable("linuxssh.command.key_import_error", keyPath))
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
                    sendFeedback(source, Component.translatable("linuxssh.command.key_import_error", errorMsg))
                    return@runAsync
                }

                // Copy the key file to the player's directory
                val targetFile = File(playerKeyDir, keyFile.name)
                keyFile.copyTo(targetFile, overwrite = true)

                sendFeedback(source, Component.translatable("linuxssh.command.key_imported", keyFile.name))
                
                // Display the key content for copying and viewing
                val keyContent = keyFile.readText()
                sendFeedback(source, Component.translatable("linuxssh.key.imported_content_header"))
                sendFeedback(source, Component.literal("§a$keyContent"))
                sendFeedback(source, Component.translatable("linuxssh.key.imported_content_footer"))
                
                // If this is a private key, check if there's a corresponding public key
                if (!keyFile.name.endsWith(".pub")) {
                    val publicKeyFile = File(keyPath + ".pub")
                    if (publicKeyFile.exists() && publicKeyFile.isFile) {
                        // Copy the public key file to the player's directory
                        val targetPublicFile = File(playerKeyDir, publicKeyFile.name)
                        publicKeyFile.copyTo(targetPublicFile, overwrite = true)
                        
                        // Display the public key content
                        val publicKeyContent = publicKeyFile.readText()
                        sendFeedback(source, Component.translatable("linuxssh.key.imported_public_content_header"))
                        sendFeedback(source, Component.literal("§a$publicKeyContent"))
                        sendFeedback(source, Component.translatable("linuxssh.key.imported_public_content_footer"))
                        
                        sendFeedback(source, Component.translatable("linuxssh.command.key_imported", publicKeyFile.name))
                    }
                }
            } catch (e: Exception) {
                sendFeedback(source, Component.translatable("linuxssh.command.key_import_error", e.message ?: ""))
                e.printStackTrace()
            }
        }

        return 1
    }

    private fun sendFeedback(source: Any, component: Component) {
        // Run on main thread if possible to avoid IllegalStateException: Rendersystem called from wrong thread
        try {
            val minecraftClass = Class.forName("net.minecraft.client.Minecraft")
            val getInstanceMethod = minecraftClass.getMethod("getInstance")
            val minecraftInstance = getInstanceMethod.invoke(null)
            if (minecraftInstance != null) {
                val executeMethod = minecraftClass.getMethod("execute", Runnable::class.java)
                executeMethod.invoke(minecraftInstance, Runnable { performSendFeedback(source, component) })
                return
            }
        } catch (e: Exception) {
            // Not on client side or Minecraft class not found, proceed with direct call
        }

        performSendFeedback(source, component)
    }

    private fun performSendFeedback(source: Any, component: Component) {
        try {
            val sourceClass = source.javaClass
            // Try to find a method that takes a Component and sends it
            // This covers FabricClientCommandSource and ClientSuggestionProvider (which uses sendFeedback in recent versions or similar)
            
            // 1. Try sendFeedback(Component)
            try {
                val method = sourceClass.getMethod("sendFeedback", Component::class.java)
                method.invoke(source, component)
                return
            } catch (e: NoSuchMethodException) {}

            // 2. Try sendSuccess(Supplier, boolean) - CommandSourceStack
            try {
                val method = sourceClass.getMethod("sendSuccess", java.util.function.Supplier::class.java, Boolean::class.javaPrimitiveType)
                method.invoke(source, java.util.function.Supplier { component }, false)
                return
            } catch (e: NoSuchMethodException) {}

            // 3. Fallback: Try sendSystemMessage(Component) - Many Minecraft classes have this
            try {
                val method = sourceClass.getMethod("sendSystemMessage", Component::class.java)
                method.invoke(source, component)
                return
            } catch (e: NoSuchMethodException) {}

            // 4. If everything fails, try reflection on the player if it's a command source that has a player
            try {
                val getPlayerMethod = sourceClass.getMethod("getPlayer")
                val player = getPlayerMethod.invoke(source) as? net.minecraft.world.entity.player.Player
                player?.sendSystemMessage(component)
                if (player != null) return
            } catch (e: Exception) {}

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun handleKeyGenerate(source: Any): Int {
        val player = try {
            val getPlayerMethod = source.javaClass.getMethod("getPlayer")
            getPlayerMethod.invoke(source) as? net.minecraft.world.entity.player.Player
        } catch (e: Exception) {
            null
        } ?: return 0
        
        // Check if key generation is enabled in config
        val config = LinuxsshConfig
        if (!config.enableKeyGeneration) {
            sendFeedback(source, Component.translatable("linuxssh.command.key_generation_disabled"))
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
                
                sendFeedback(source, Component.translatable("linuxssh.command.key_generated"))
                
                // Display the private key content for copying and viewing
                val privateKeyContent = privateKeyFile.readText()
                sendFeedback(source, Component.translatable("linuxssh.key.generated_private_header"))
                sendFeedback(source, Component.literal("§a$privateKeyContent"))
                sendFeedback(source, Component.translatable("linuxssh.key.generated_private_footer"))
                
                // Display the public key content for copying and viewing
                val publicKeyContent = publicKeyFile.readText()
                sendFeedback(source, Component.translatable("linuxssh.key.generated_public_header"))
                sendFeedback(source, Component.literal("§a$publicKeyContent"))
                sendFeedback(source, Component.translatable("linuxssh.key.generated_public_footer"))
                
                // Additional message for using the keys
                sendFeedback(source, Component.translatable("linuxssh.command.key_usage_info"))
            } catch (e: Exception) {
                sendFeedback(source, Component.translatable("linuxssh.command.key_generation_error", e.message ?: ""))
                e.printStackTrace()
            }
        }

        return 1
    }

    fun handleSshConnect(source: Any, connectionString: String): Int {
        val player = try {
            val getPlayerMethod = source.javaClass.getMethod("getPlayer")
            getPlayerMethod.invoke(source) as? net.minecraft.world.entity.player.Player
        } catch (e: Exception) {
            null
        }

        // Run SSH operations in a separate thread to avoid blocking the main game thread
        CompletableFuture.runAsync {
            try {
                // Check if the input is a connection string like "user@host"
                if (connectionString.contains("@")) {
                    val parts = connectionString.split("@")
                    if (parts.size != 2) {
                        sendFeedback(source, Component.translatable("linuxssh.command.invalid_format"))
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
                                        sendFeedback(source, Component.translatable("linuxssh.command.key_imported", keyFile.name))
                                    } catch (e: JSchException) {
                                        // Clean up error message to avoid displaying byte arrays
                                        val errorMsg = e.message?.let {
                                            if (it.contains("[B@")) {
                                                "invalid privatekey format"
                                            } else {
                                                it
                                            }
                                        } ?: "unknown error"
                                        sendFeedback(source, Component.translatable("linuxssh.command.key_import_error", errorMsg))
                                    }
                                }
                            }
                        }
                    }

                    // Check if we should delete the host fingerprint
                    val config = LinuxsshConfig
                    if (config.deleteHostFingerprint) {
                        if (deleteHostFingerprint(host)) {
                            sendFeedback(source, Component.translatable("linuxssh.command.fingerprint_deleted", host))
                        }
                    }
                    
                    val session = jsch.getSession(username, host, 22)

                    // Configure session to ask for fingerprint confirmation
                    session.setConfig("StrictHostKeyChecking", "ask")

                    // Create a custom UserInfo to handle fingerprint verification and password prompt
                    val customUserInfo = if (player != null) {
                        object : UserInfo {
                            private var passwordStr: String? = null

                            fun setPassword(password: String) {
                                passwordStr = password
                            }

                            override fun getPassword(): String? = passwordStr
                            
                            override fun promptYesNo(message: String): Boolean {
                                // Extract fingerprint from message
                                val fingerprintPromise = CompletableFuture<Boolean>()

                                pendingFingerprints[player.uuid] = { confirmed ->
                                    fingerprintPromise.complete(confirmed)
                                }

                                // Format and display the fingerprint message
                                val formattedMessage = message.replace("\n", " ")
                                sendFeedback(source, Component.literal("§e$formattedMessage"))
                                sendFeedback(source, Component.translatable("linuxssh.command.fingerprint_prompt"))

                                // Wait for user confirmation
                                return try {
                                    fingerprintPromise.get() // This will block until confirmation is provided
                                } catch (e: Exception) {
                                    sendFeedback(source, Component.translatable("linuxssh.command.fingerprint_rejected"))
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
                                sendFeedback(source, Component.translatable("linuxssh.command.fingerprint_prompt"))
                            }
                        }
                    } else null

                    if (customUserInfo != null) {
                        session.setUserInfo(customUserInfo)
                    }

                    sendFeedback(source, Component.translatable("linuxssh.command.connect", "$username@$host"))

                    // Check if we should try key authentication first
                    var authSuccess = false
                    
                    if (config.preferKeyAuthentication) {
                        try {
                            // Try to connect with key authentication
                            session.connect(5000) // 5 second timeout for key auth
                            authSuccess = true
                            sendFeedback(source, Component.translatable("linuxssh.command.connected", host))
                        } catch (e: Exception) {
                            // Key authentication failed, fall back to password
                            sendFeedback(source, Component.translatable("linuxssh.command.key_auth_failed"))
                        }
                    }
                    
                    // If key authentication failed or is not preferred, use password
                    if (!authSuccess && player != null) {
                        val passwordPromise = CompletableFuture<String>()

                        pendingPasswords[player.uuid] = { password ->
                            passwordPromise.complete(password)
                        }

                        sendFeedback(source, Component.translatable("linuxssh.command.password_prompt"))

                        // Set password when provided
                        try {
                            val password = passwordPromise.get() // This will block until password is provided
                            session.setPassword(password)
                            
                            // Also set the password in UserInfo to satisfy some authentication requirements
                            try {
                                val setPasswordMethod = customUserInfo?.javaClass?.getMethod("setPassword", String::class.java)
                                setPasswordMethod?.invoke(customUserInfo, password)
                            } catch (e: Exception) {
                                // Fallback or ignore if method not found
                            }
                            
                            // Connect with password
                            session.connect(30000) // 30 second timeout for password auth
                            authSuccess = true
                            sendFeedback(source, Component.translatable("linuxssh.command.connected", host))
                        } catch (e: Exception) {
                            sendFeedback(source, Component.translatable("linuxssh.command.connection_failed", e.message ?: ""))
                            pendingPasswords.remove(player.uuid)
                            return@runAsync
                        }
                    }

                    player?.let {
                        activeSessions[it.uuid] = session
                    }
                } else {
                    sendFeedback(source, Component.translatable("linuxssh.command.invalid_format"))
                }
            } catch (e: Exception) {
                sendFeedback(source, Component.translatable("linuxssh.command.error", e.message ?: ""))
                e.printStackTrace()
            }
        }

        return 1
    }

    fun handleSshCommand(source: Any, command: String): Int {
        val player = try {
            val getPlayerMethod = source.javaClass.getMethod("getPlayer")
            getPlayerMethod.invoke(source) as? net.minecraft.world.entity.player.Player
        } catch (e: Exception) {
            null
        }

        // Run SSH operations in a separate thread to avoid blocking the main game thread
        CompletableFuture.runAsync {
            try {
                val session = player?.let { activeSessions[it.uuid] }

                if (session == null || !session.isConnected) {
                    sendFeedback(source, Component.translatable("linuxssh.command.not_connected"))
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
                    val currentLine = line ?: ""
                    val formattedLine = ColorUtil.formatAnsiToMinecraft(currentLine)
                    sendFeedback(source, Component.literal(formattedLine))
                }

                channel.disconnect()
            } catch (e: Exception) {
                sendFeedback(source, Component.translatable("linuxssh.command.error", e.message ?: ""))
                e.printStackTrace()
            }
        }

        return 1
    }
}