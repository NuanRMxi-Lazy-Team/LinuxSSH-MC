package cn.moerain.linuxssh.util

import java.util.regex.Pattern

/**
 * Utility class for ANSI to Minecraft color conversion
 */
object ColorUtil {
    private val ANSI_COLOR_MAP = mapOf(
        "0;30" to "§0", // Black
        "0;34" to "§1", // Dark Blue
        "0;32" to "§2", // Dark Green
        "0;36" to "§3", // Dark Aqua
        "0;31" to "§4", // Dark Red
        "0;35" to "§5", // Dark Purple
        "0;33" to "§6", // Gold
        "0;37" to "§7", // Gray
        "1;30" to "§8", // Dark Gray
        "1;34" to "§9", // Blue
        "1;32" to "§a", // Green
        "1;36" to "§b", // Aqua
        "1;31" to "§c", // Red
        "1;35" to "§d", // Light Purple
        "1;33" to "§e", // Yellow
        "1;37" to "§f", // White
        "30" to "§0",
        "34" to "§1",
        "32" to "§2",
        "36" to "§3",
        "31" to "§4",
        "35" to "§5",
        "33" to "§6",
        "37" to "§7",
        "0" to "§r",   // Reset
        "1" to "§l",   // Bold
        "4" to "§n",   // Underline
    )

    private val ANSI_PATTERN = Pattern.compile("\u001B\\[([0-9;]+)m")

    /**
     * Convert ANSI color codes in the input string to Minecraft color codes
     */
    fun formatAnsiToMinecraft(input: String): String {
        val matcher = ANSI_PATTERN.matcher(input)
        val sb = StringBuilder()
        var lastEnd = 0
        while (matcher.find()) {
            sb.append(input, lastEnd, matcher.start())
            val code = matcher.group(1)
            sb.append(ANSI_COLOR_MAP[code] ?: "")
            lastEnd = matcher.end()
        }
        sb.append(input, lastEnd, input.length)
        return sb.toString()
    }
}
