package cn.moerain.linuxssh.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Java bridge to access Minecraft methods that Kotlin compiler
 * cannot resolve directly in 26.2-snapshot (Loom/Kotlin interop issue).
 */
public class MinecraftBridge {

    public static void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    public static void addSystemToast(Minecraft client,
                                      SystemToast.SystemToastId id,
                                      Component title,
                                      Component message) {
        client.toastManager.addToast(new SystemToast(id, title, message));
    }
}