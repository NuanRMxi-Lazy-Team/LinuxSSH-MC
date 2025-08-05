package cn.moerain.linuxssh.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Configuration class for the LinuxSSH mod
 */
class LinuxsshConfig {
    // Configuration options
    var deleteHostFingerprint: Boolean = false
    
    // SSH Key Management options
    var preferKeyAuthentication: Boolean = true
    var enableKeyGeneration: Boolean = true
    var showPublicKeyPassword: Boolean = false

    companion object {
        private val configFile = FabricLoader.getInstance().configDir.resolve("linuxssh/config.json").toFile()
        private val gson = GsonBuilder().setPrettyPrinting().create()
        private var instance: LinuxsshConfig? = null

        /**
         * Get the singleton instance of the config
         */
        fun getInstance(): LinuxsshConfig {
            if (instance == null) {
                instance = load()
            }
            return instance!!
        }

        /**
         * Load the configuration from file
         */
        private fun load(): LinuxsshConfig {
            if (configFile.exists()) {
                try {
                    FileReader(configFile).use { reader ->
                        return gson.fromJson(reader, LinuxsshConfig::class.java) ?: LinuxsshConfig()
                    }
                } catch (e: Exception) {
                    println("Failed to load LinuxSSH config: ${e.message}")
                    e.printStackTrace()
                }
            }
            return LinuxsshConfig()
        }

        /**
         * Save the configuration to file
         */
        fun save() {
            try {
                if (!configFile.parentFile.exists()) {
                    configFile.parentFile.mkdirs()
                }
                
                FileWriter(configFile).use { writer ->
                    gson.toJson(getInstance(), writer)
                }
            } catch (e: Exception) {
                println("Failed to save LinuxSSH config: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}