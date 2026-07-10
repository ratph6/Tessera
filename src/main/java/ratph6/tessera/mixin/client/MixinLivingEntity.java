package ratph6.tessera.mixin.client;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ratph6.tessera.engine.TesseraHooks;

// entityDeath + entityDamage triggers. Observe-only.
@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @Inject(method = "die", at = @At("HEAD"))
    private void tessera$onDie(DamageSource source, CallbackInfo ci) {
        TesseraHooks.onEntityDeath((LivingEntity) (Object) this);
    }

    // client-side damage notification: the server sends ClientboundDamageEventPacket, whose handler
    // calls handleDamageEvent on the hurt entity — fires for every damaged entity the client can see
    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void tessera$onDamage(DamageSource source, CallbackInfo ci) {
        TesseraHooks.onEntityDamage((LivingEntity) (Object) this, source.getMsgId());
    }
}
