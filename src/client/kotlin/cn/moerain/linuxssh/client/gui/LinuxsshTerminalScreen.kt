package cn.moerain.linuxssh.client.gui

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Modifier
import java.util.concurrent.Executors

/**
 * Terminal screen for interactive SSH session
 */
class LinuxsshTerminalScreen(private val session: Session) : Screen(Component.translatable("linuxssh.terminal.title")) {
    private val columns = 80
    private val rows = 24 // VT100 standard is 24 rows
    private val terminalLines = Array(rows) { StringBuilder(" ".repeat(columns)) }
    private val terminalColors = Array(rows) { IntArray(columns) { 0xAAAAAA } } // Default Light Grey
    
    private val terminalBackgrounds = Array(rows) { IntArray(columns) { 0x000000 } }
    
    private var cursorX = 0
    private var cursorY = 0
    private var currentColor = 0xAAAAAA
    private var currentBackground = 0x000000
    private var isBold = false

    private var channel: ChannelShell? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val readExecutor = Executors.newSingleThreadExecutor()

    override fun init() {
        super.init()
        if (channel == null) {
            try {
                channel = session.openChannel("shell") as ChannelShell
                channel?.setPtyType("vt100", columns, rows, 640, 480)
                inputStream = channel?.inputStream
                outputStream = channel?.outputStream
                channel?.connect()

                startReading()
                
                // 延迟发送，确保 Channel 已经准备好接收
                readExecutor.execute {
                    try {
                        Thread.sleep(500)
                        sendToSsh("\r\n")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                this.minecraft.setScreen(null)
            }
        }
    }

    private fun startReading() {
        readExecutor.execute {
            val buffer = ByteArray(4096)
            try {
                while (channel?.isConnected == true) {
                    val read = inputStream?.read(buffer) ?: -1
                    if (read == -1) {
                        break
                    }
                    if (read > 0) {
                        processInput(String(buffer, 0, read))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processInput(data: String) {
        var i = 0
        while (i < data.length) {
            val char = data[i]
            if (char == '\u001B') { // Escape sequence
                if (i + 1 < data.length && data[i + 1] == '[') {
                    // Try to handle SGR (m)
                    val mIdx = data.indexOf('m', i + 2)
                    // Try to handle Cursor commands (A, B, C, D, H, f, J, K, etc.)
                    val cursorEndIdx = findCursorEnd(data, i + 2)
                    
                    if (mIdx != -1 && (cursorEndIdx == -1 || mIdx < cursorEndIdx)) {
                        val codes = data.substring(i + 2, mIdx).split(';')
                        handleSgr(codes)
                        i = mIdx + 1
                        continue
                    }
                    
                    if (cursorEndIdx != -1) {
                        handleCursor(data.substring(i + 2, cursorEndIdx), data[cursorEndIdx])
                        i = cursorEndIdx + 1
                        continue
                    }
                }
            }
            
            when (char) {
                '\r' -> cursorX = 0
                '\n' -> {
                    cursorY++
                    if (cursorY >= rows) {
                        scrollUp()
                        cursorY = rows - 1
                    }
                }
                '\b' -> {
                    cursorX = (cursorX - 1).coerceAtLeast(0)
                    // ANSI backspace doesn't usually clear the character, but we should handle it consistently
                    // with how we render. Many terminals just move the cursor.
                    // If we want to support "destructive backspace", we'd clear it.
                }
                '\u0007' -> { /* Bell - ignore */ }
                '\t' -> {
                    val spaces = 8 - (cursorX % 8)
                    repeat(spaces) {
                        if (cursorX < columns) {
                            terminalLines[cursorY].setCharAt(cursorX, ' ')
                            terminalColors[cursorY][cursorX] = currentColor
                            terminalBackgrounds[cursorY][cursorX] = currentBackground
                            cursorX++
                        }
                    }
                }
                else -> {
                    if (char.code in 32..126 || char.code >= 160) { // Printable characters
                        if (cursorX < columns && cursorY < rows) {
                            terminalLines[cursorY].setCharAt(cursorX, char)
                            terminalColors[cursorY][cursorX] = if (isBold) brighten(currentColor) else currentColor
                            terminalBackgrounds[cursorY][cursorX] = currentBackground
                            cursorX++
                            if (cursorX >= columns) {
                                cursorX = 0
                                cursorY++
                                if (cursorY >= rows) {
                                    scrollUp()
                                    cursorY = rows - 1
                                }
                            }
                        }
                    }
                }
            }
            i++
        }
    }

    private fun findCursorEnd(data: String, start: Int): Int {
        for (idx in start until data.length) {
            val c = data[idx]
            if (c in 'A'..'Z' || c in 'a'..'z') return idx
        }
        return -1
    }

    private fun handleSgr(codes: List<String>) {
        for (code in codes) {
            when (code) {
                "0", "" -> {
                    currentColor = 0xAAAAAA
                    currentBackground = 0x000000
                    isBold = false
                }
                "1" -> isBold = true
                "30" -> currentColor = 0x000000
                "31" -> currentColor = 0xAA0000
                "32" -> currentColor = 0x00AA00
                "33" -> currentColor = 0xAA5500
                "34" -> currentColor = 0x0000AA
                "35" -> currentColor = 0xAA00AA
                "36" -> currentColor = 0x00AAAA
                "37" -> currentColor = 0xAAAAAA
                "90" -> currentColor = 0x555555
                "91" -> currentColor = 0xFF5555
                "92" -> currentColor = 0x55FF55
                "93" -> currentColor = 0xFFFF55
                "94" -> currentColor = 0x5555FF
                "95" -> currentColor = 0xFF55FF
                "96" -> currentColor = 0x55FFFF
                "97" -> currentColor = 0xFFFFFF
                // Background colors
                "40" -> currentBackground = 0x000000
                "41" -> currentBackground = 0xAA0000
                "42" -> currentBackground = 0x00AA00
                "43" -> currentBackground = 0xAA5500
                "44" -> currentBackground = 0x0000AA
                "45" -> currentBackground = 0xAA00AA
                "46" -> currentBackground = 0x00AAAA
                "47" -> currentBackground = 0xAAAAAA
                "100" -> currentBackground = 0x555555
                "101" -> currentBackground = 0xFF5555
                "102" -> currentBackground = 0x55FF55
                "103" -> currentBackground = 0xFFFF55
                "104" -> currentBackground = 0x5555FF
                "105" -> currentBackground = 0xFF55FF
                "106" -> currentBackground = 0x55FFFF
                "107" -> currentBackground = 0xFFFFFF
            }
        }
    }

    private fun brighten(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (Math.min(r + 0x55, 0xFF) shl 16) or (Math.min(g + 0x55, 0xFF) shl 8) or Math.min(b + 0x55, 0xFF)
    }

    private fun handleCursor(params: String, command: Char) {
        // Very basic implementation of cursor commands
        when (command) {
            'H', 'f' -> { // Cursor Position
                val parts = params.split(';')
                cursorY = (parts.getOrNull(0)?.toIntOrNull()?.minus(1) ?: 0).coerceIn(0, rows - 1)
                cursorX = (parts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: 0).coerceIn(0, columns - 1)
            }
            'A' -> cursorY = (cursorY - (params.toIntOrNull() ?: 1)).coerceAtLeast(0)
            'B' -> cursorY = (cursorY + (params.toIntOrNull() ?: 1)).coerceAtMost(rows - 1)
            'C' -> cursorX = (cursorX + (params.toIntOrNull() ?: 1)).coerceAtMost(columns - 1)
            'D' -> cursorX = (cursorX - (params.toIntOrNull() ?: 1)).coerceAtLeast(0)
            'K' -> { // Erase in Line
                 val mode = params.toIntOrNull() ?: 0
                 when (mode) {
                     0 -> { // Erase from cursor to end of line
                         for (x in cursorX until columns) {
                             terminalLines[cursorY].setCharAt(x, ' ')
                             terminalColors[cursorY][x] = 0xAAAAAA
                             terminalBackgrounds[cursorY][x] = 0x000000
                         }
                     }
                     1 -> { // Erase from beginning of line to cursor
                         for (x in 0..cursorX.coerceAtMost(columns - 1)) {
                             terminalLines[cursorY].setCharAt(x, ' ')
                             terminalColors[cursorY][x] = 0xAAAAAA
                             terminalBackgrounds[cursorY][x] = 0x000000
                         }
                     }
                     2 -> { // Erase entire line
                         terminalLines[cursorY].setLength(0)
                         terminalLines[cursorY].append(" ".repeat(columns))
                         for (x in 0 until columns) {
                             terminalColors[cursorY][x] = 0xAAAAAA
                             terminalBackgrounds[cursorY][x] = 0x000000
                         }
                     }
                 }
            }
            'J' -> { // Erase in Display
                val mode = params.toIntOrNull() ?: 0
                when (mode) {
                    0 -> { // Erase from cursor to end of display
                        // Current line
                        for (x in cursorX until columns) {
                            terminalLines[cursorY].setCharAt(x, ' ')
                            terminalColors[cursorY][x] = 0xAAAAAA
                            terminalBackgrounds[cursorY][x] = 0x000000
                        }
                        // Following lines
                        for (y in cursorY + 1 until rows) {
                            terminalLines[y].setLength(0)
                            terminalLines[y].append(" ".repeat(columns))
                            for (x in 0 until columns) {
                                terminalColors[y][x] = 0xAAAAAA
                                terminalBackgrounds[y][x] = 0x000000
                            }
                        }
                    }
                    1 -> { // Erase from beginning of display to cursor
                        // Previous lines
                        for (y in 0 until cursorY) {
                            terminalLines[y].setLength(0)
                            terminalLines[y].append(" ".repeat(columns))
                            for (x in 0 until columns) {
                                terminalColors[y][x] = 0xAAAAAA
                                terminalBackgrounds[y][x] = 0x000000
                            }
                        }
                        // Current line
                        for (x in 0..cursorX.coerceAtMost(columns - 1)) {
                            terminalLines[cursorY].setCharAt(x, ' ')
                            terminalColors[cursorY][x] = 0xAAAAAA
                            terminalBackgrounds[cursorY][x] = 0x000000
                        }
                    }
                    2, 3 -> { // Erase entire display
                        for (y in 0 until rows) {
                            terminalLines[y].setLength(0)
                            terminalLines[y].append(" ".repeat(columns))
                            for (x in 0 until columns) {
                                terminalColors[y][x] = 0xAAAAAA
                                terminalBackgrounds[y][x] = 0x000000
                            }
                        }
                        cursorX = 0
                        cursorY = 0
                    }
                }
            }
        }
    }

    private fun scrollUp() {
        for (y in 0 until rows - 1) {
            terminalLines[y].setLength(0)
            terminalLines[y].append(terminalLines[y + 1])
            System.arraycopy(terminalColors[y + 1], 0, terminalColors[y], 0, columns)
            System.arraycopy(terminalBackgrounds[y + 1], 0, terminalBackgrounds[y], 0, columns)
        }
        terminalLines[rows - 1].setLength(0)
        terminalLines[rows - 1].append(" ".repeat(columns))
        for (x in 0 until columns) {
            terminalColors[rows - 1][x] = 0xAAAAAA
            terminalBackgrounds[rows - 1][x] = 0x000000
        }
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        handleTerminalRender(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun shouldCloseOnEsc(): Boolean {
        return true
    }

    override fun onClose() {
        try {
            channel?.disconnect()
            readExecutor.shutdownNow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onClose()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        this.minecraft.framerateLimitTracker.onInputReceived()
        val keyCode = resolveReflectively(event, "keyCode", "getKeyCode", "a") as? Int ?: -1
        val modifiers = resolveReflectively(event, "modifiers", "getModifiers", "c") as? Int ?: 0

        println("[LinuxSSH-DEBUG] keyPressed event: ${event::class.java.simpleName}, keyCode: $keyCode, modifiers: $modifiers")

        // Handle Ctrl+V (Paste)
        if (modifiers and GLFW.GLFW_MOD_CONTROL != 0 && keyCode == GLFW.GLFW_KEY_V) {
            val content = this.minecraft.keyboardHandler.clipboard
            if (content.isNotEmpty()) {
                sendToSsh(content)
            }
            return true
        }

        // Handle Ctrl+C, Ctrl+D etc.
        if (modifiers and GLFW.GLFW_MOD_CONTROL != 0 && keyCode != -1) {
            when (keyCode) {
                GLFW.GLFW_KEY_C -> {
                    sendToSsh("\u0003")
                    return true
                }
                GLFW.GLFW_KEY_D -> {
                    sendToSsh("\u0004")
                    return true
                }
                GLFW.GLFW_KEY_Z -> {
                    sendToSsh("\u001A")
                    return true
                }
            }
        }

        if (keyCode != -1 && handleTerminalKeyPressed(keyCode)) return true

        // Prevent F3 debug keys from triggering while typing in terminal
        if (keyCode == GLFW.GLFW_KEY_F3) return true

        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        this.minecraft.framerateLimitTracker.onInputReceived()
        val character = resolveReflectively(event, "character", "getCharacter", "a") as? Char ?: '\u0000'

        if (character != '\u0000' && character.code != 0) {
            handleTerminalCharTyped(character)
            return true
        }
        return super.charTyped(event)
    }

    override fun preeditUpdated(event: PreeditEvent?): Boolean {
        // IME Support: PreeditEvent means the user is composing text (e.g., Chinese input)
        // For simple terminal, we might just want to show something or let it pass
        // But the requirement is to implement it to avoid UI issues
        return super.preeditUpdated(event)
    }

    private fun resolveReflectively(obj: Any, vararg names: String): Any? {
        val cls = obj.javaClass
        val simpleName = cls.simpleName

        if (simpleName == "KeyEvent") {
            for (name in names) {
                if (name == "keyCode" || name == "getKeyCode" || name == "a") {
                    val v = resolveSingle(obj, "key", "keyCode", "getKeyCode", "a")
                    if (v != null) return v
                }
                if (name == "modifiers" || name == "getModifiers" || name == "c") {
                    val v = resolveSingle(obj, "modifiers", "getModifiers", "c")
                    if (v != null) return v
                }
            }
        } else if (simpleName == "CharacterEvent") {
            // 1.21.4 uses 'codepoint' (Int) instead of 'character' (Char)
            val v = resolveSingle(obj, "codepoint", "character", "getCharacter", "a")
            if (v is Int) return v.toChar()
            if (v != null) return v
        }

        return resolveSingle(obj, *names)
    }

    private fun resolveSingle(obj: Any, vararg names: String): Any? {
        val cls = obj.javaClass

        for (name in names) {
            try {
                val method = try { cls.getMethod(name) } catch (e: Exception) { cls.getDeclaredMethod(name) }
                method.isAccessible = true
                val result = method.invoke(obj)
                if (result != null) return result
            } catch (e: Exception) {}
        }

        for (name in names) {
            try {
                val field = cls.getDeclaredField(name)
                field.isAccessible = true
                val result = field.get(obj)
                if (result != null) return result
            } catch (e: Exception) {}
        }

        return null
    }

    private fun handleTerminalRender(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val charWidth = 9
        val charHeight = 12
        val titleBarHeight = 15
        val padding = 10

        val windowWidth = columns * charWidth + padding * 2
        val windowHeight = rows * charHeight + padding + titleBarHeight + 5

        val x1 = (this.width - windowWidth) / 2
        val x2 = x1 + windowWidth
        val y1 = (this.height - windowHeight) / 2
        val y2 = y1 + windowHeight

        try {
            val graphicsClass = guiGraphics.javaClass

            try {
                graphicsClass.getMethod("nextStratum").invoke(guiGraphics)
            } catch (e: Exception) {}

            val fillMethod = graphicsClass.getMethod("fill", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            fillMethod.invoke(guiGraphics, 0, 0, this.width, this.height, 0x80000000.toInt())

            try {
                graphicsClass.getMethod("nextStratum").invoke(guiGraphics)
            } catch (e: Exception) {}

            fillMethod.invoke(guiGraphics, x1, y1, x2, y2, 0xFF121212.toInt())

            fillMethod.invoke(guiGraphics, x1 - 1, y1 - 1, x2 + 1, y1, 0xFF707070.toInt()) // Top
            fillMethod.invoke(guiGraphics, x1 - 1, y2, x2 + 1, y2 + 1, 0xFF707070.toInt()) // Bottom
            fillMethod.invoke(guiGraphics, x1 - 1, y1, x1, y2, 0xFF707070.toInt()) // Left
            fillMethod.invoke(guiGraphics, x2, y1, x2 + 1, y2, 0xFF707070.toInt()) // Right

            fillMethod.invoke(guiGraphics, x1, y1, x2, y1 + titleBarHeight, 0xFF353535.toInt())

            val font = this.minecraft.font
            val fontClass = font.javaClass

            var textMethod: java.lang.reflect.Method? = null
            try {
                textMethod = graphicsClass.getMethod("text", fontClass, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            } catch (e: Exception) {
                try {
                    textMethod = graphicsClass.getMethod("drawString", fontClass, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                } catch (e2: Exception) {}
            }

            val startX = x1 + padding
            val startY = y1 + titleBarHeight + 5

            if (textMethod != null) {
                val title = "SSH Terminal: ${session.host}"
                textMethod.invoke(guiGraphics, font, title, x1 + 5, y1 + 3, 0xFFFFFFFF.toInt(), false)
                textMethod.invoke(guiGraphics, font, "X", x2 - 12, y1 + 3, 0xFFFF5555.toInt(), false)

                for (y in 0 until rows) {
                    val line = terminalLines[y].toString()
                    val backgrounds = terminalBackgrounds[y]
                    val colors = terminalColors[y]

                    // Optimization: Batch backgrounds
                    var startX_bg = 0
                    while (startX_bg < columns) {
                        val currentBg = backgrounds[startX_bg]
                        var endX_bg = startX_bg + 1
                        while (endX_bg < columns && backgrounds[endX_bg] == currentBg) {
                            endX_bg++
                        }

                        val px1 = startX + startX_bg * charWidth
                        val py1 = startY + y * charHeight
                        val px2 = startX + endX_bg * charWidth
                        val py2 = py1 + charHeight

                        fillMethod.invoke(guiGraphics, px1, py1, px2, py2, 0xFF000000.toInt() or currentBg)
                        startX_bg = endX_bg
                    }

                    // Optimization: Batch text
                    var startX_text = 0
                    while (startX_text < columns) {
                        val currentChar = line[startX_text]
                        if (currentChar == ' ') {
                            startX_text++
                            continue
                        }

                        val currentColor = colors[startX_text]
                        var endX_text = startX_text + 1
                        while (endX_text < columns && line[endX_text] != ' ' && colors[endX_text] == currentColor) {
                            endX_text++
                        }

                        val textToDraw = line.substring(startX_text, endX_text)
                        val px = startX + startX_text * charWidth
                        val py = startY + y * charHeight

                        textMethod.invoke(guiGraphics, font, textToDraw, px, py, 0xFF000000.toInt() or currentColor, false)
                        startX_text = endX_text
                    }
                }
            }

            if ((System.currentTimeMillis() / 500) % 2 == 0L) {
                fillMethod.invoke(guiGraphics, startX + cursorX * charWidth, startY + cursorY * charHeight, startX + (cursorX + 1) * charWidth, startY + (cursorY + 1) * charHeight, -2004318209)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val eventClass = event.javaClass
        val mouseX = try {
            val m = eventClass.getMethod("mouseX")
            m.invoke(event) as Double
        } catch (e: Exception) {
            try {
                val m = eventClass.getMethod("getMouseX")
                m.invoke(event) as Double
            } catch (e2: Exception) {
                0.0
            }
        }
        val mouseY = try {
            val m = eventClass.getMethod("mouseY")
            m.invoke(event) as Double
        } catch (e: Exception) {
            try {
                val m = eventClass.getMethod("getMouseY")
                m.invoke(event) as Double
            } catch (e2: Exception) {
                0.0
            }
        }

        val charWidth = 9
        val charHeight = 12
        val titleBarHeight = 15
        val padding = 10

        val windowWidth = columns * charWidth + padding * 2
        val windowHeight = rows * charHeight + padding + titleBarHeight + 5

        val x1 = (this.width - windowWidth) / 2
        val x2 = x1 + windowWidth
        val y1 = (this.height - windowHeight) / 2
        val y2 = y1 + windowHeight

        if (mouseX < x1 || mouseX > x2 || mouseY < y1 || mouseY > y2) {
            this.onClose()
            return true
        }

        // Also check for click on "X"
        if (mouseX >= x2 - 15 && mouseX <= x2 && mouseY >= y1 && mouseY <= y1 + 15) {
            this.onClose()
            return true
        }

        return super.mouseClicked(event, doubleClick)
    }

    private fun handleTerminalKeyPressed(keyCode: Int): Boolean {
        when (keyCode) {
            GLFW.GLFW_KEY_ENTER -> sendToSsh("\r")
            GLFW.GLFW_KEY_BACKSPACE -> sendToSsh("\b")
            GLFW.GLFW_KEY_TAB -> sendToSsh("\t")
            GLFW.GLFW_KEY_ESCAPE -> {
                this.onClose()
                return true
            }
            GLFW.GLFW_KEY_UP -> sendToSsh("\u001B[A")
            GLFW.GLFW_KEY_DOWN -> sendToSsh("\u001B[B")
            GLFW.GLFW_KEY_RIGHT -> sendToSsh("\u001B[C")
            GLFW.GLFW_KEY_LEFT -> sendToSsh("\u001B[D")
            else -> return false
        }
        return true
    }

    private fun handleTerminalCharTyped(codePoint: Char) {
        sendToSsh(codePoint.toString())
    }

    private fun sendToSsh(data: String) {
        readExecutor.execute {
            try {
                outputStream?.write(data.toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun isPauseScreen(): Boolean = false
}
