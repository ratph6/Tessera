package ratph6.tessera.agent;

import java.lang.reflect.Method;

/**
 * A tiny throwaway launcher run as a <em>separate</em> JVM process. It attaches to the Minecraft JVM by
 * pid and loads the Tessera agent into it.
 *
 * <p>Why a separate process: a JVM attaching to <em>itself</em> is refused unless it was started with
 * {@code -Djdk.attach.allowAttachSelf=true} (the guard reads the startup property snapshot, so it can't
 * be flipped at runtime). An external process attaching to the target has no such restriction — so this
 * lets TS mixins work without any launch flag. Uses reflection so it carries no compile-time dependency
 * on {@code jdk.attach}.
 *
 * <p>Args: {@code <targetPid> <agentJarPath>}.
 */
public final class AttachHelper {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: AttachHelper <pid> <agentJar>");
            System.exit(2);
        }
        String pid = args[0];
        String agentJar = args[1];

        Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        Method attach = vmClass.getMethod("attach", String.class);
        Method loadAgent = vmClass.getMethod("loadAgent", String.class);
        Method detach = vmClass.getMethod("detach");

        Object vm = attach.invoke(null, pid);
        try {
            loadAgent.invoke(vm, agentJar);
        } finally {
            detach.invoke(vm);
        }
    }

    private AttachHelper() {
    }
}
