package cn.moerain.linuxssh.client.debug;

import cn.moerain.linuxssh.Linuxssh;
import com.jcraft.jsch.Session;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Display SSH Host Status in Debug Screen
 * eg. CPU Mem and Disk etc.
 *
 * <p>
 * This entry shows the status of the SSH host only when the local player has an active SSH session.
 * Data is read from a lightweight cache if available; otherwise a short placeholder is displayed.
 * </p>
 */
public class DebugEntrySshHostStatus {

    private static final long STATUS_CACHE_TTL_MS = 5000L;

    private static volatile CachedStatus cachedStatus = CachedStatus.empty();
    private static volatile long lastRefreshAttemptMs = 0L;

    public void display(Object displayer, @Nullable Level serverOrClientLevel, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
        Minecraft minecraft = getMinecraft();
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        UUID uuid = minecraft.player.getUUID();
        Session session = Linuxssh.activeSessions.get(uuid);

        if (session == null || !session.isConnected()) {
            return;
        }

        // Try using Reflection to find the add method in DebugScreenDisplayer if it's not simply 'add'
        safeAddLine(displayer, Component.literal("SSH Host Status: connected"));

        CachedStatus status = cachedStatus;
        if (status.isExpired()) {
            maybeRefreshAsync(session);
        }

        if (status.host != null && !status.host.isBlank()) {
            safeAddLine(displayer, Component.literal("  Host: " + status.host));
        }

        if (status.username != null && !status.username.isBlank()) {
            safeAddLine(displayer, Component.literal("  User: " + status.username));
        }

        if (status.cpu != null && !status.cpu.isBlank()) {
            safeAddLine(displayer, Component.literal("  CPU: " + status.cpu));
        } else {
            safeAddLine(displayer, Component.literal("  CPU: collecting..."));
        }

        if (status.memory != null && !status.memory.isBlank()) {
            safeAddLine(displayer, Component.literal("  Mem: " + status.memory));
        } else {
            safeAddLine(displayer, Component.literal("  Mem: collecting..."));
        }

        if (status.disk != null && !status.disk.isBlank()) {
            safeAddLine(displayer, Component.literal("  Disk: " + status.disk));
        } else {
            safeAddLine(displayer, Component.literal("  Disk: collecting..."));
        }

        if (status.load != null && !status.load.isBlank()) {
            safeAddLine(displayer, Component.literal("  Load: " + status.load));
        }

        if (status.updatedAtMs > 0L) {
            long ageSec = Math.max(0L, (System.currentTimeMillis() - status.updatedAtMs) / 1000L);
            safeAddLine(displayer, Component.literal("  Updated: " + ageSec + "s ago"));
        }
    }

    private void safeAddLine(Object displayer, Component line) {
        // Probe for common method names in DebugScreenDisplayer
        String[] methodNames = {"add", "addLine", "addText"};
        for (String name : methodNames) {
            try {
                Method m = displayer.getClass().getMethod(name, Component.class);
                m.invoke(displayer, line);
                return;
            } catch (Throwable ignored) {}
        }
    }

    private void maybeRefreshAsync(Session session) {
        long now = System.currentTimeMillis();
        if (now - lastRefreshAttemptMs < STATUS_CACHE_TTL_MS) {
            return;
        }
        lastRefreshAttemptMs = now;

        new Thread(() -> {
            try {
                CachedStatus fresh = queryStatus(session);
                cachedStatus = fresh;
            } catch (Exception e) {
                cachedStatus = CachedStatus.error("unavailable");
            }
        }).start();
    }

    private CachedStatus queryStatus(Session session) {
        String host = safeGetSessionString(session, "getHost", "host");
        String username = safeGetSessionString(session, "getUserName", "username", "getUsername");

        String cpu = null;
        String memory = null;
        String disk = null;
        String load = null;

        String[] commands = new String[] {
            "bash -lc 'top -bn1 | grep \"Cpu(s)\" | head -n 1'",
            "bash -lc 'free -h | sed -n \"2p\"'",
            "bash -lc 'df -h / | tail -n 1'",
            "bash -lc 'cat /proc/loadavg'"
        };

        List<String> outputs = new ArrayList<>();
        for (String command : commands) {
            String out = executeRemoteCommand(session, command);
            outputs.add(out == null ? "" : out.trim());
        }

        cpu = parseCpu(outputs.get(0));
        memory = parseMemory(outputs.get(1));
        disk = parseDisk(outputs.get(2));
        load = parseLoad(outputs.get(3));

        return new CachedStatus(host, username, cpu, memory, disk, load, System.currentTimeMillis());
    }

    @Nullable
    private String executeRemoteCommand(Session session, String command) {
        try {
            Object channel = session.openChannel("exec");
            if (channel == null) {
                return null;
            }

            Method setCommand = channel.getClass().getMethod("setCommand", String.class);
            setCommand.invoke(channel, command);

            Method connect = findMethod(channel.getClass(), "connect", int.class);
            if (connect != null) {
                connect.invoke(channel, 5000);
            } else {
                Method connectNoArg = channel.getClass().getMethod("connect");
                connectNoArg.invoke(channel);
            }

            Method getInputStream = channel.getClass().getMethod("getInputStream");
            java.io.InputStream inputStream = (java.io.InputStream) getInputStream.invoke(channel);

            byte[] buffer = inputStream.readAllBytes();
            String result = new String(buffer, java.nio.charset.StandardCharsets.UTF_8);

            try {
                Method disconnect = channel.getClass().getMethod("disconnect");
                disconnect.invoke(channel);
            } catch (Throwable ignored) {
            }

            return result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private String parseCpu(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    @Nullable
    private String parseMemory(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    @Nullable
    private String parseDisk(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    @Nullable
    private String parseLoad(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length >= 3) {
            return parts[0] + " " + parts[1] + " " + parts[2];
        }
        return text.trim();
    }

    @Nullable
    private String safeGetSessionString(Session session, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method m = session.getClass().getMethod(methodName);
                Object value = m.invoke(session);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Nullable
    private Method findMethod(Class<?> type, String name, Class<?>... paramTypes) {
        try {
            Method method = type.getMethod(name, paramTypes);
            if (Modifier.isPublic(method.getModifiers())) {
                return method;
            }
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Nullable
    private Minecraft getMinecraft() {
        try {
            return Minecraft.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class CachedStatus {
        final @Nullable String host;
        final @Nullable String username;
        final @Nullable String cpu;
        final @Nullable String memory;
        final @Nullable String disk;
        final @Nullable String load;
        final long updatedAtMs;

        private CachedStatus(
                @Nullable String host,
                @Nullable String username,
                @Nullable String cpu,
                @Nullable String memory,
                @Nullable String disk,
                @Nullable String load,
                long updatedAtMs
        ) {
            this.host = host;
            this.username = username;
            this.cpu = cpu;
            this.memory = memory;
            this.disk = disk;
            this.load = load;
            this.updatedAtMs = updatedAtMs;
        }

        static CachedStatus empty() {
            return new CachedStatus(null, null, null, null, null, null, 0L);
        }

        static CachedStatus error(String message) {
            return new CachedStatus(null, null, message, message, message, message, System.currentTimeMillis());
        }

        boolean isExpired() {
            return updatedAtMs <= 0L || System.currentTimeMillis() - updatedAtMs > STATUS_CACHE_TTL_MS;
        }
    }
}
