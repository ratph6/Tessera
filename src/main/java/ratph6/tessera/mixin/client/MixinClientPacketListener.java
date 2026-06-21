package ratph6.tessera.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ratph6.tessera.engine.TesseraHooks;

// messageSent trigger; cancel blocks the outgoing chat.
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void tessera$onSendChat(String message, CallbackInfo ci) {
        if (TesseraHooks.onMessageSent(message)) {
            ci.cancel();
        }
    }
}
