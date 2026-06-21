package ratph6.tessera.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ratph6.tessera.engine.TesseraHooks;

/** Feeds the {@code soundPlay} trigger — every sound that reaches the engine. Observe-only. */
@Mixin(SoundEngine.class)
public class MixinSoundEngine {
    @Inject(method = "play", at = @At("HEAD"))
    private void tessera$onPlay(SoundInstance instance, CallbackInfoReturnable<?> cir) {
        TesseraHooks.onSoundPlay(String.valueOf(instance.getIdentifier()));
    }
}
