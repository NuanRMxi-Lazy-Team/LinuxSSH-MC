package cn.moerain.linuxssh.client

import cn.moerain.linuxssh.config.LinuxsshConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents

/**
 * LinuxSSH mod client entry point
 * @author Celesita
 */

class LinuxsshClient : ClientModInitializer {

    override fun onInitializeClient() {
        // Initialize configuration
        LinuxsshConfig.getInstance()
        
        // Save configuration when client stops
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            LinuxsshConfig.save()
        }
    }
}
