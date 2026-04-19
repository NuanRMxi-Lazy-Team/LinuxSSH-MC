package cn.moerain.linuxssh.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader

/**
 * Configuration class for the LinuxSSH mod
 */
object LinuxsshConfig {
    // Configuration options
    var deleteHostFingerprint: Boolean = false
    
    // SSH Key Management options
    var preferKeyAuthentication: Boolean = true
    var enableKeyGeneration: Boolean = true
    var showPublicKeyPassword: Boolean = false

    private val configFile = FabricLoader.getInstance().configDir.resolve("linuxssh.cfg").toFile()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Data class to represent the configuration for serialization
     */
    private data class ConfigData(
        val deleteHostFingerprint: Boolean,
        val preferKeyAuthentication: Boolean,
        val enableKeyGeneration: Boolean,
        val showPublicKeyPassword: Boolean
    )

    /**
     * Load the configuration from file
     */
    fun load() {
        if (!configFile.exists()) return
        
        try {
            configFile.bufferedReader().use { reader ->
                val loaded = gson.fromJson(reader, ConfigData::class.java)
                if (loaded != null) {
                    deleteHostFingerprint = loaded.deleteHostFingerprint
                    preferKeyAuthentication = loaded.preferKeyAuthentication
                    enableKeyGeneration = loaded.enableKeyGeneration
                    showPublicKeyPassword = loaded.showPublicKeyPassword
                }
            }
        } catch (e: Exception) {
            println("Failed to load LinuxSSH config: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Save the configuration to file
     */
    fun save() {
        try {
            if (!configFile.parentFile.exists()) {
                configFile.parentFile.mkdirs()
            }
            
            val data = ConfigData(
                deleteHostFingerprint,
                preferKeyAuthentication,
                enableKeyGeneration,
                showPublicKeyPassword
            )
            
            configFile.bufferedWriter().use { writer ->
                gson.toJson(data, writer)
            }
        } catch (e: Exception) {
            println("Failed to save LinuxSSH config: ${e.message}")
            e.printStackTrace()
        }
    }
}