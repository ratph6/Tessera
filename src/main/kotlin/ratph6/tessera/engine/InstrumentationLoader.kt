package ratph6.tessera.engine

import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

/**
 * Obtains a live [Instrumentation] for the running JVM by loading a tiny java agent (see
 * [ratph6.tessera.agent.TesseraAgent]). This is what lets TypeScript mixins rewrite Minecraft classes
 * that are *already loaded* by the time a script runs — a static `.mixins.json` only gets a shot during
 * early class loading.
 *
 * Two attach strategies, tried in order:
 *  1. **Self-attach** — fast, in-process. Only works when the JVM was launched with
 *     `-Djdk.attach.allowAttachSelf=true` (the guard reads the *startup* property snapshot, so it can't
 *     be enabled at runtime). Set automatically for the dev run in build.gradle.kts.
 *  2. **External-process attach** — spawns a throwaway JVM ([ratph6.tessera.agent.AttachHelper]) that
 *     attaches to *us* by pid. A separate process attaching has no self-attach restriction, so this
 *     path needs **no launch flag** — the robust default for production.
 *
 * Both paths require the Java runtime to include the `jdk.attach` module (any full JDK does; some
 * stripped Minecraft runtime images do not). If neither works, mixins are disabled with a clear error
 * and the rest of Tessera keeps running.
 *
 * Failure is sticky: once attach fails we remember why and fail fast on subsequent calls instead of
 * retrying the (slow) attach every time.
 */
object InstrumentationLoader {

    private val log = org.slf4j.LoggerFactory.getLogger("Tessera")
    private const val AGENT_CLASS = "ratph6.tessera.agent.TesseraAgent"
    private const val HELPER_CLASS = "ratph6.tessera.agent.AttachHelper"
    private const val AGENT_RESOURCE = "/ratph6/tessera/agent/TesseraAgent.class"
    private const val HELPER_RESOURCE = "/ratph6/tessera/agent/AttachHelper.class"

    @Volatile private var inst: Instrumentation? = null
    @Volatile private var failure: String? = null

    /** The instrumentation if already attached, else null (does not attempt an attach). */
    fun instrumentationOrNull(): Instrumentation? = inst

    /** Clear a remembered attach failure so the next [instrumentation] call retries (used by `/te reload`). */
    fun resetFailureForRetry() {
        if (inst == null) failure = null
    }

    /** The instrumentation, attaching on first use. Throws (with a user-facing message) on failure. */
    fun instrumentation(): Instrumentation {
        inst?.let { return it }
        synchronized(this) {
            inst?.let { return it }
            failure?.let { throw IllegalStateException("Tessera mixins unavailable: $it") }
            return try {
                attach().also { inst = it }
            } catch (t: Throwable) {
                val root = rootCause(t)
                val msg = "${root::class.java.simpleName}: ${root.message ?: ""}"
                failure = msg
                log.warn("attach failed — TS mixins disabled ({})", msg, root)
                throw IllegalStateException("Tessera mixins unavailable: $msg", root)
            }
        }
    }

    private fun attach(): Instrumentation {
        // Ensure jdk.attach is present before either strategy (both reflect into com.sun.tools.attach).
        try {
            Class.forName("com.sun.tools.attach.VirtualMachine")
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException(
                "this Java runtime lacks the jdk.attach module — run Minecraft on a full JDK to use TS mixins",
                e,
            )
        }

        val agentJar = buildAgentJar()
        val pid = ProcessHandle.current().pid().toString()

        // 1) Fast path: self-attach (works only with -Djdk.attach.allowAttachSelf=true).
        try {
            selfAttach(agentJar, pid)
            readInstrumentation()?.let { log.info("instrumentation attached for TS mixins (self-attach)"); return it }
        } catch (e: Throwable) {
            log.info("self-attach unavailable ({}); falling back to external attach", rootCause(e).message)
        }

        // 2) Robust path: a separate JVM attaches to us — no launch flag needed.
        externalAttach(agentJar, pid)
        return readInstrumentation()
            ?: throw IllegalStateException("agent attached but Instrumentation was not delivered")
    }

    /** Read the [Instrumentation] the agent stashed on the system-loader copy of [AGENT_CLASS]. */
    private fun readInstrumentation(): Instrumentation? {
        // The attach appends the agent jar to the system class loader's search, so its (separate) copy
        // of TesseraAgent — the one agentmain populated — is reachable there.
        val holder = runCatching { Class.forName(AGENT_CLASS, true, ClassLoader.getSystemClassLoader()) }.getOrNull()
            ?: return null
        return holder.getField("inst").get(null) as? Instrumentation
    }

    private fun selfAttach(agentJar: String, pid: String) {
        val vmClass = Class.forName("com.sun.tools.attach.VirtualMachine")
        val vm = vmClass.getMethod("attach", String::class.java).invoke(null, pid)
        try {
            vmClass.getMethod("loadAgent", String::class.java).invoke(vm, agentJar)
        } finally {
            runCatching { vmClass.getMethod("detach").invoke(vm) }
        }
    }

    /** Spawn a helper JVM that attaches to our pid and loads the agent (sidesteps the self-attach rule). */
    private fun externalAttach(agentJar: String, pid: String) {
        val javaBin = javaExecutable()
        val cmd = listOf(
            javaBin, "--add-modules", "jdk.attach",
            "-cp", agentJar, HELPER_CLASS, pid, agentJar,
        )
        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.readBytes().decodeToString().trim()
        if (!proc.waitFor(30, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            throw IllegalStateException("attach helper timed out")
        }
        if (proc.exitValue() != 0) {
            throw IllegalStateException("attach helper failed (exit ${proc.exitValue()})" + if (output.isNotEmpty()) ": $output" else "")
        }
    }

    private fun javaExecutable(): String {
        val home = System.getProperty("java.home")
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val name = if (isWindows) "java.exe" else "java"
        return Path.of(home, "bin", name).toAbsolutePath().toString()
    }

    /** Write the agent jar: both helper classes + a manifest that serves as agent, premain and launcher. */
    private fun buildAgentJar(): String {
        val agentBytes = resourceBytes(AGENT_RESOURCE)
        val helperBytes = resourceBytes(HELPER_RESOURCE)

        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name("Agent-Class")] = AGENT_CLASS
            mainAttributes[Attributes.Name("Premain-Class")] = AGENT_CLASS
            mainAttributes[Attributes.Name("Can-Retransform-Classes")] = "true"
            mainAttributes[Attributes.Name("Can-Redefine-Classes")] = "true"
            // So the same jar can be launched directly by the external helper process.
            mainAttributes[Attributes.Name.MAIN_CLASS] = HELPER_CLASS
        }

        val jar = Files.createTempFile("tessera-agent", ".jar")
        jar.toFile().deleteOnExit()
        JarOutputStream(Files.newOutputStream(jar), manifest).use { out ->
            out.putNextEntry(ZipEntry(AGENT_CLASS.replace('.', '/') + ".class"))
            out.write(agentBytes)
            out.closeEntry()
            out.putNextEntry(ZipEntry(HELPER_CLASS.replace('.', '/') + ".class"))
            out.write(helperBytes)
            out.closeEntry()
        }
        return jar.toAbsolutePath().toString()
    }

    private fun resourceBytes(path: String): ByteArray =
        InstrumentationLoader::class.java.getResourceAsStream(path)?.use { it.readBytes() }
            ?: throw IllegalStateException("agent class resource missing from the mod jar ($path)")

    /** Unwrap reflection/wrapper layers to the real failure for a useful message. */
    private fun rootCause(t: Throwable): Throwable {
        var cur: Throwable = t
        while (true) {
            val next = when (cur) {
                is java.lang.reflect.InvocationTargetException -> cur.targetException
                else -> cur.cause
            }
            if (next == null || next === cur) return cur
            cur = next
        }
    }
}
