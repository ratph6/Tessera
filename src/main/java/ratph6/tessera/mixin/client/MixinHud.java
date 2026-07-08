package ratph6.tessera.mixin.client;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ratph6.tessera.engine.TesseraHooks;

// actionBar trigger; cancel hides the message. (setOverlayMessage moved Gui -> Hud in MC 26.2.)
@Mixin(Hud.class)
public class MixinHud {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void tessera$onOverlayMessage(Component message, boolean animateColor, CallbackInfo ci) {
        if (TesseraHooks.onActionBar(message.getString())) {
            ci.cancel();
        }
    }
}
