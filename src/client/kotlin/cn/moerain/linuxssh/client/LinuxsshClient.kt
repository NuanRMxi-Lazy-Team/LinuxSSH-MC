package cn.moerain.linuxssh.client

import cn.moerain.linuxssh.config.LinuxsshConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component

/**
 * LinuxSSH mod client entry point
 *
 * Let's dance!
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

        // Show reminder toast when joining a world
        ClientPlayConnectionEvents.JOIN.register { handler, sender, client ->
            client.execute {
                client.toastManager.addToast(
                    SystemToast(
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("linuxssh.toast.warning.title"),
                        Component.translatable("linuxssh.toast.warning.message")
                    )
                )
            }
        }
    }
}
