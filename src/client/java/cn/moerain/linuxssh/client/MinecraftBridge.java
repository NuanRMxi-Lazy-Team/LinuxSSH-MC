package cn.moerain.linuxssh.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Java bridge using pure reflection to access renamed Minecraft APIs in 26.2-snapshot.
 * No direct references to setScreen or toastManager.
 */
public final class MinecraftBridge {

    private MinecraftBridge() {}

    // Cache the resolved setScreen method
    private static volatile Method cachedSetScreenMethod;
    private static volatile Object cachedToastManager;
    private static volatile Method cachedAddToastMethod;

    /**
     * Opens a screen on the given Minecraft client instance.
     * Uses reflection to find the correct method name.
     */
    public static void setScreen(Minecraft client, Screen screen) {
        try {
            if (cachedSetScreenMethod != null) {
                cachedSetScreenMethod.invoke(client, screen);
                return;
            }

            // Try all possible method names
            String[] candidates = {
                    "setScreen", "openScreen", "displayScreen", "showScreen",
                    "setCurrentScreen", "m_91152_", "m_91291_"
            };

            for (String name : candidates) {
                try {
                    Method m = Minecraft.class.getMethod(name, Screen.class);
                    m.setAccessible(true);
                    cachedSetScreenMethod = m;
                    m.invoke(client, screen);
                    return;
                } catch (NoSuchMethodException ignored) {
                }
            }

            // Last resort: scan all methods that take a single Screen parameter
            for (Method m : Minecraft.class.getMethods()) {
                if (m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(Screen.class)
                        && m.getReturnType() == void.class) {
                    m.setAccessible(true);
                    cachedSetScreenMethod = m;
                    System.out.println("[LinuxSSH] Found screen method via scan: " + m.getName());
                    m.invoke(client, screen);
                    return;
                }
            }

            System.err.println("[LinuxSSH] Could not find any setScreen method on Minecraft class");
        } catch (Exception e) {
            System.err.println("[LinuxSSH] Failed to set screen via reflection");
            e.printStackTrace();
        }
    }

    /**
     * Adds a SystemToast to the Minecraft client instance.
     * Uses reflection to find the correct toast manager field and method.
     */
    public static void addSystemToast(Minecraft client,
                                      SystemToast.SystemToastId id,
                                      Component title,
                                      Component message) {
        try {
            Object toastMgr = getToastManager(client);
            if (toastMgr == null) {
                System.err.println("[LinuxSSH] Could not find toast manager");
                return;
            }

            if (cachedAddToastMethod == null) {
                // Try common method names on the toast manager
                String[] addToastCandidates = {"addToast", "add", "showToast", "m_94924_"};
                for (String name : addToastCandidates) {
                    try {
                        for (Method m : toastMgr.getClass().getMethods()) {
                            if (m.getName().equals(name) && m.getParameterCount() == 1) {
                                m.setAccessible(true);
                                cachedAddToastMethod = m;
                                break;
                            }
                        }
                        if (cachedAddToastMethod != null) break;
                    } catch (Exception ignored) {
                    }
                }

                // Scan all methods that accept a Toast-like parameter
                if (cachedAddToastMethod == null) {
                    for (Method m : toastMgr.getClass().getMethods()) {
                        if (m.getParameterCount() == 1
                                && m.getReturnType() == void.class) {
                            Class<?> paramType = m.getParameterTypes()[0];
                            if (paramType.getSimpleName().contains("Toast")
                                    || paramType.isAssignableFrom(SystemToast.class)) {
                                m.setAccessible(true);
                                cachedAddToastMethod = m;
                                System.out.println("[LinuxSSH] Found addToast method via scan: " + m.getName());
                                break;
                            }
                        }
                    }
                }
            }

            if (cachedAddToastMethod != null) {
                cachedAddToastMethod.invoke(toastMgr, new SystemToast(id, title, message));
            } else {
                System.err.println("[LinuxSSH] Could not find addToast method on toast manager");
            }
        } catch (Exception e) {
            System.err.println("[LinuxSSH] Failed to add toast via reflection");
            e.printStackTrace();
        }
    }

    private static Object getToastManager(Minecraft client) throws Exception {
        if (cachedToastManager != null) return cachedToastManager;

        // Try known field names
        String[] fieldCandidates = {
                "toastManager", "toast_manager", "toasts", "toastManagerInstance",
                "f_91013_", "f_90997_"
        };

        for (String name : fieldCandidates) {
            try {
                Field f = Minecraft.class.getDeclaredField(name);
                f.setAccessible(true);
                cachedToastManager = f.get(client);
                return cachedToastManager;
            } catch (NoSuchFieldException ignored) {
            }
        }

        // Scan all fields for ToastManager type
        for (Field f : Minecraft.class.getDeclaredFields()) {
            Class<?> type = f.getType();
            if (type.getSimpleName().contains("ToastManager") || type.getSimpleName().contains("ToastQueue")) {
                f.setAccessible(true);
                cachedToastManager = f.get(client);
                System.out.println("[LinuxSSH] Found toast manager field via scan: " + f.getName());
                return cachedToastManager;
            }
        }

        // Try getter methods
        String[] getterCandidates = {"getToastManager", "toastManager", "getToasts"};
        for (String name : getterCandidates) {
            try {
                Method m = Minecraft.class.getMethod(name);
                m.setAccessible(true);
                cachedToastManager = m.invoke(client);
                return cachedToastManager;
            } catch (NoSuchMethodException ignored) {
            }
        }

        // Scan all no-arg methods returning a Toast-related type
        for (Method m : Minecraft.class.getMethods()) {
            if (m.getParameterCount() == 0) {
                Class<?> returnType = m.getReturnType();
                if (returnType.getSimpleName().contains("ToastManager") || returnType.getSimpleName().contains("ToastQueue")) {
                    m.setAccessible(true);
                    cachedToastManager = m.invoke(client);
                    System.out.println("[LinuxSSH] Found toast manager via method scan: " + m.getName());
                    return cachedToastManager;
                }
            }
        }

        return null;
    }
}