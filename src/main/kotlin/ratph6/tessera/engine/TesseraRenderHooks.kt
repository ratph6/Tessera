package ratph6.tessera.engine

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.entity.state.EntityRenderState
import ratph6.tessera.api.EntityWrapper
import ratph6.tessera.api.Tessellator
import ratph6.tessera.triggers.TriggerType

/**
 * Bridge between the entity-render mixin and the engine. Called on the render thread (which is the
 * JS thread), once before and once after each entity model is submitted, with the live [PoseStack].
 *
 * The `renderEntity` callback runs with [Tessellator] bound to [pose], so a script may push a
 * scale/rotation that transforms the model. Anything left on the stack is auto-popped after
 * `postRenderEntity` so a buggy or conditional script can never unbalance Minecraft's pose stack.
 */
object TesseraRenderHooks {

    @JvmStatic
    fun onRenderEntity(state: EntityRenderState, pose: PoseStack) {
        if (!TesseraEngine.booted) return
        val entity = (state as? TesseraRenderTarget)?.`tessera$entity`() ?: return
        Tessellator.begin(pose)
        TesseraEngine.dispatch(TriggerType.RENDER_ENTITY, EntityWrapper(entity))
    }

    @JvmStatic
    fun onPostRenderEntity(state: EntityRenderState, pose: PoseStack) {
        if (!TesseraEngine.booted) return
        try {
            val entity = (state as? TesseraRenderTarget)?.`tessera$entity`()
            if (entity != null) TesseraEngine.dispatch(TriggerType.POST_RENDER_ENTITY, EntityWrapper(entity))
        } finally {
            Tessellator.end()
        }
    }
}
