package ratph6.tessera.api

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Quaternionf

/**
 * Matrix transforms for world rendering, in the spirit of ChatTriggers' `Tessellator`. Only valid
 * inside a `renderEntity` / `postRenderEntity` trigger, where Tessera binds [pose] to the entity's live
 * pose stack (already positioned at the entity's origin).
 *
 * Push, transform, draw — Tessera auto-pops anything you leave on the stack when the entity finishes
 * rendering, so an unmatched [pushMatrix] can never corrupt Minecraft's matrices.
 *
 * ```ts
 * Tessera.register(Event.RENDER_ENTITY, (entity) => {
 *   if (entity.isLocalPlayer()) { Tessellator.pushMatrix(); Tessellator.scale(2, 2, 2); }
 * });
 * ```
 */
object Tessellator {

    /** The pose stack for the entity currently rendering, or null outside a render trigger. */
    @JvmField var pose: PoseStack? = null

    /** Pushes made since [begin]; used to auto-pop leftovers in [end]. */
    private var depth = 0

    internal fun begin(stack: PoseStack) {
        pose = stack
        depth = 0
    }

    internal fun end() {
        val p = pose
        if (p != null) while (depth > 0) { p.popPose(); depth-- }
        pose = null
        depth = 0
    }

    @JvmStatic fun pushMatrix() {
        val p = pose ?: return
        p.pushPose(); depth++
    }

    @JvmStatic fun popMatrix() {
        val p = pose ?: return
        if (depth > 0) { p.popPose(); depth-- }
    }

    @JvmStatic fun scale(x: Double, y: Double, z: Double) {
        pose?.scale(x.toFloat(), y.toFloat(), z.toFloat())
    }

    @JvmStatic fun translate(x: Double, y: Double, z: Double) {
        pose?.translate(x, y, z)
    }

    /** Rotate [angle] degrees about the axis (x, y, z). The axis is normalised for you. */
    @JvmStatic fun rotate(angle: Double, x: Double, y: Double, z: Double) {
        val p = pose ?: return
        if (x == 0.0 && y == 0.0 && z == 0.0) return
        p.mulPose(Quaternionf().rotateAxis(Math.toRadians(angle).toFloat(), x.toFloat(), y.toFloat(), z.toFloat()))
    }
}
