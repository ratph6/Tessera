package ratph6.tessera.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ratph6.tessera.engine.TesseraHooks;

// addMessage is the private sink all chat paths funnel into — hook it once for the chat trigger.
@Mixin(ChatComponent.class)
public class MixinChatComponent {

    @Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
    private void tessera$onAddMessage(Component message, MessageSignature signature, GuiMessageSource source,
                                  GuiMessageTag tag, CallbackInfo ci) {
        if (TesseraHooks.onChat(message.getString())) {
            ci.cancel();
        }
    }
}
