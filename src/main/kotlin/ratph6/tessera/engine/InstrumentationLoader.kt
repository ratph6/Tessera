package ratph6.tessera.engine

import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

// Obtains a live Instrumentation by loading a tiny java agent, so TS mixins can rewrite Minecraft
// classes that are already loaded. Tries self-attach (needs -Djdk.attach.allowAttachSelf=true, dev
// only) then external-process attach (no launch flag — robust default). Both need the jdk.attach
// module. Failure is sticky: once attach fails we remember why and fail fast instead of retrying.
object InstrumentationLoader {

    private val log = org.slf4j.LoggerFactory.getLogger("Tessera")
    private const val AGENT_CLASS = "ratph6.tessera.agent.TesseraAgent"
    private const val HELPER_CLASS = "ratph6.tessera.agent.AttachHelper"
    private const val AGENT_RESOURCE = "/ratph6/tessera/agent/TesseraAgent.class"
    private const val HELPER_RESOURCE = "/ratph6/tessera/agent/AttachHelper.class"

    @Volatile private var inst: Instrumentation? = null
    @Volatile private var failure: String? = null

    fun instrumentationOrNull(): Instrumentation? = inst

    // clear a remembered attach failure so the next instrumentation() retries (used by /te reload)
    fun resetFailureForRetry() {
        if (inst == null) failure = null
    }

    // attaches on first use; throws with a user-facing message on failure
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
        // both strategies reflect into com.sun.tools.attach — bail early if jdk.attach is absent
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

        // fast path: self-attach (needs -Djdk.attach.allowAttachSelf=true)
        try {
            selfAttach(agentJar, pid)
            readInstrumentation()?.let { log.info("instrumentation attached for TS mixins (self-attach)"); return it }
        } catch (e: Throwable) {
            log.info("self-attach unavailable ({}); falling back to external attach", rootCause(e).message)
        }

        // robust path: a separate JVM attaches to us — no launch flag needed
        externalAttach(agentJar, pid)
        return readInstrumentation()
            ?: throw IllegalStateException("agent attached but Instrumentation was not delivered")
    }

    // The agent jar is appended to the system loader's search, so its separate copy of TesseraAgent —
    // the one agentmain populated with the Instrumentation — is reachable there.
    private fun readInstrumentation(): Instrumentation? {
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

    // spawn a helper JVM that attaches to our pid (sidesteps the self-attach rule)
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

    // both helper classes + a manifest that serves as agent, premain and launcher
    private fun buildAgentJar(): String {
        val agentBytes = resourceBytes(AGENT_RESOURCE)
        val helperBytes = resourceBytes(HELPER_RESOURCE)

        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name("Agent-Class")] = AGENT_CLASS
            mainAttributes[Attributes.Name("Premain-Class")] = AGENT_CLASS
            mainAttributes[Attributes.Name("Can-Retransform-Classes")] = "true"
            mainAttributes[Attributes.Name("Can-Redefine-Classes")] = "true"
            // so the same jar can be launched directly by the external helper
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
