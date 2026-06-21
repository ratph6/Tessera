package ratph6.tessera.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ratph6.tessera.engine.TesseraHooks;

/** Feeds the {@code spawnParticle} trigger from the client level. Observe-only. */
@Mixin(ClientLevel.class)
public class MixinClientLevel {

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"))
    private void tessera$onAddParticle(ParticleOptions options, double x, double y, double z,
                                   double dx, double dy, double dz, CallbackInfo ci) {
        TesseraHooks.onParticle(String.valueOf(options.getType()), x, y, z);
    }
}
