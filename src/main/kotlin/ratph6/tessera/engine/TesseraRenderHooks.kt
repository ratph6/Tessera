package ratph6.tessera.engine

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.entity.state.EntityRenderState
import ratph6.tessera.api.EntityWrapper
import ratph6.tessera.api.Tessellator
import ratph6.tessera.triggers.TriggerType

// Bridge between the entity-render mixin and the engine, called on the render (= JS) thread before and
// after each entity model with the live PoseStack. Tessellator is bound to pose so a script can push a
// transform; anything left is auto-popped after post so a buggy script can't unbalance the pose stack.
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
