package ratph6.tessera.engine

import org.graalvm.polyglot.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// stand-in for a Minecraft object with float setters
@Suppress("unused")
class FloatTargetForTest {
    @JvmField var yaw: Float = 0f
    fun setYaw(v: Float) { yaw = v }
}

// regression for GraalJS "lossy primitive coercion" on double→float; host-access mappings fix it
class FloatCoercionTest {

    @Test fun `JS number passes into a float parameter`() {
        Context.newBuilder("js")
            .allowHostAccess(GraalRuntime.hostAccess)
            .build().use { ctx ->
                val target = FloatTargetForTest()
                ctx.getBindings("js").putMember("t", target)
                ctx.eval("js", "t.setYaw(25.96923139185228);")
                assertEquals(25.96923f, target.yaw, 1e-3f, "method call should coerce double→float")
            }
    }
}
