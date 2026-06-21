package ratph6.tessera.agent;

import java.lang.instrument.Instrumentation;

// java.lang.instrument agent holder. Self-attach lets TS mixins retransform already-loaded classes.
// Loaded twice with distinct identities: by Knot (the mod) and by the system loader (the agent jar).
// Only the system-loader copy's inst is populated; loader reads it back via getSystemClassLoader().
// Keep dependency-free so it loads from the bare agent jar.
public final class TesseraAgent {

    // set on the system-loader copy by agentmain/premain
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
