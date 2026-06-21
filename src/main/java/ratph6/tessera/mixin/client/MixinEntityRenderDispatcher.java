package ratph6.tessera.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ratph6.tessera.engine.TesseraRenderHooks;
import ratph6.tessera.engine.TesseraRenderTarget;

// renderEntity/postRenderEntity triggers + the Tessellator matrix API.
// extractEntity is where we stash the source entity. We wrap the inner EntityRenderer.submit call
// (not the outer submit) so script pose tweaks affect only the model, not its shadow/flame, and
// are applied at the entity's own origin.
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void tessera$stashEntity(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        if (state instanceof TesseraRenderTarget) {
            ((TesseraRenderTarget) state).tessera$setEntity(entity);
        }
    }

    @Inject(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            shift = At.Shift.BEFORE
        )
    )
    private void tessera$beforeEntityModel(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                       PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
        TesseraRenderHooks.onRenderEntity(state, pose);
    }

    @Inject(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            shift = At.Shift.AFTER
        )
    )
    private void tessera$afterEntityModel(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                      PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
        TesseraRenderHooks.onPostRenderEntity(state, pose);
    }
}
