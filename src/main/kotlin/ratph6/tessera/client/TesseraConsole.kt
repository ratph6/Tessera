package ratph6.tessera.client

import ratph6.tessera.engine.TesseraEngine
import ratph6.tessera.engine.TesseraLogLine
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * A standalone log window in the spirit of ChatTriggers' console. It mirrors every Tessera console line
 * (chat output, `Tessera.log`, and errors) with level colours and full stack traces, and offers an input
 * box that evaluates a line of TypeScript via `/te eval`.
 *
 * Opened with `/te console`. Lives on the AWT/Swing thread, independent of the game window; lines
 * arrive from the render thread, so all UI mutation is marshalled onto the EDT.
 */
object TesseraConsole {

    private const val BG = 0x1E1E1E
    private const val FG = 0xE0E0E0

    private var frame: JFrame? = null
    private var pane: JTextPane? = null

    /** Open the console (creating it on first use) and replay recent history. */
    fun open() {
        // Minecraft's launch usually starts the JVM with -Djava.awt.headless=true, and AWT *caches*
        // that flag the first time it's read — so just setting the property now is too late. Flip the
        // property AND clear the cached value, otherwise JFrame throws a message-less HeadlessException.
        runCatching { System.setProperty("java.awt.headless", "false") }
        unlatchHeadless()
        SwingUtilities.invokeLater {
            runCatching {
                if (frame == null) build()
                frame?.isVisible = true
                frame?.toFront()
            }.onFailure {
                if (it is java.awt.HeadlessException) {
                    TesseraEngine.chat("§ccould not open console: this JVM is headless (no display/AWT).")
                    TesseraEngine.chat("§7Add §f--add-opens java.desktop/java.awt=ALL-UNNAMED§7 to the JVM args so Tessera can un-headless AWT.")
                } else {
                    TesseraEngine.chat("§ccould not open console: ${it::class.simpleName}: ${it.message ?: "(no message)"}")
                }
            }
        }
    }

    /**
     * Reset AWT's cached headless decision so a window can be created after Minecraft launched the JVM
     * headless. `GraphicsEnvironment.headless` is a private static cache; nulling it forces a re-read of
     * the (now `false`) property. Needs `--add-opens java.desktop/java.awt=ALL-UNNAMED` on JDK 25+ —
     * best-effort: if the module is locked down this no-ops and the HeadlessException message guides the
     * user to add the flag.
     */
    private fun unlatchHeadless() {
        runCatching {
            val ge = Class.forName("java.awt.GraphicsEnvironment")
            val field = ge.getDeclaredField("headless")
            field.isAccessible = true
            field.set(null, null)
        }
    }

    private fun build() {
        val textPane = JTextPane().apply {
            isEditable = false
            background = Color(BG)
            foreground = Color(FG)
            font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        }
        pane = textPane

        val input = JTextField().apply {
            background = Color(0x2B2B2B)
            foreground = Color(FG)
            caretColor = Color(FG)
            font = Font(Font.MONOSPACED, Font.PLAIN, 13)
            toolTipText = "Run a line of TypeScript (like /te eval)"
            addActionListener { e: ActionEvent ->
                val code = (e.source as JTextField).text.trim()
                if (code.isNotEmpty()) { TesseraEngine.evaluate(code); text = "" }
            }
        }

        val clear = JButton("Clear").apply {
            addActionListener { textPane.text = "" }
        }

        val bottom = JPanel(BorderLayout(4, 0)).apply {
            background = Color(BG)
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(input, BorderLayout.CENTER)
            add(clear, BorderLayout.EAST)
        }

        frame = JFrame("Tessera Console").apply {
            defaultCloseOperation = JFrame.HIDE_ON_CLOSE
            layout = BorderLayout()
            add(JScrollPane(textPane), BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
            preferredSize = Dimension(900, 520)
            pack()
            setLocationRelativeTo(null)
        }

        // Replay buffered history, then stream new lines.
        for (line in TesseraEngine.recentLog()) append(line)
        TesseraEngine.consoleSink = { line -> SwingUtilities.invokeLater { append(line) } }
    }

    private fun colorFor(level: String): Color = when (level) {
        "error" -> Color(0xFF6B68)
        "warn" -> Color(0xE6C07B)
        "debug" -> Color(0x808080)
        else -> Color(FG)
    }

    private fun append(line: TesseraLogLine) {
        val doc = pane?.styledDocument ?: return
        val style = SimpleAttributeSet().also { StyleConstants.setForeground(it, colorFor(line.level)) }
        val head = if (line.where == "chat" || line.where == "log") "" else "[${line.where}] "
        runCatching {
            doc.insertString(doc.length, "$head${line.message}\n", style)
            line.detail?.let {
                val dim = SimpleAttributeSet().also { s -> StyleConstants.setForeground(s, Color(0x9A9A9A)) }
                doc.insertString(doc.length, "${it.trimEnd()}\n", dim)
            }
        }
        pane?.caretPosition = doc.length
    }
}
