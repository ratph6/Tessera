package ratph6.tessera.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ratph6.tessera.engine.TesseraHooks;

/** Feeds the {@code actionBar} trigger; cancelling hides the overlay (action-bar) message. */
@Mixin(Gui.class)
public class MixinGui {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void tessera$onOverlayMessage(Component message, boolean animateColor, CallbackInfo ci) {
        if (TesseraHooks.onActionBar(message.getString())) {
            ci.cancel();
        }
    }
}
