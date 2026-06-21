package ratph6.tessera.agent;

import java.lang.instrument.Instrumentation;

/**
 * Tiny java.lang.instrument agent holder. Tessera self-attaches to its own JVM at runtime (see
 * {@code InstrumentationLoader}) so TypeScript-defined mixins can retransform already-loaded Minecraft
 * classes — something a static {@code .mixins.json} cannot do once the game is up.
 *
 * <p>This class is loaded twice with two distinct identities: once by Knot (as part of the mod) and
 * once by the <em>system</em> class loader when the attach API loads the generated agent jar. Only the
 * system-loader copy's {@link #inst} is populated by {@link #agentmain}; the loader reads it back via
 * {@code ClassLoader.getSystemClassLoader()}. Keep this class dependency-free so it loads cleanly from
 * the bare agent jar.
 */
public final class TesseraAgent {

    /** Set by {@link #agentmain}/{@link #premain} on the system-loader copy of this class. */
    public static volatile Instrumentation inst;

    public static void agentmain(String args, Instrumentation instrumentation) {
        inst = instrumentation;
    }

    public static void premain(String args, Instrumentation instrumentation) {
        inst = instrumentation;
    }

    private TesseraAgent() {
    }
}
