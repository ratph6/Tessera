package ratph6.tessera.agent;

import java.lang.reflect.Method;

// Separate-process launcher: attaches to the MC JVM by pid and loads the agent.
// Must be a separate process — self-attach is refused without -Djdk.attach.allowAttachSelf=true
// (read once at startup, can't be flipped at runtime); an external process has no such restriction.
// Reflection avoids a compile-time dep on jdk.attach. Args: <targetPid> <agentJarPath>.
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
