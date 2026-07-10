package ratph6.tessera.mixin.client;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ratph6.tessera.engine.TesseraHooks;

// Raw mouse-button source for MOUSE_LEFT/MOUSE_RIGHT (press) and MOUSE_LEFT_RELEASE/MOUSE_RIGHT_RELEASE
// (release). onButton is the single funnel every physical mouse press/release passes through, before the
// game routes it — cancelling here at HEAD vetoes the event entirely (nothing downstream sees it).
@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    // GLFW: button 0 = left, 1 = right; action 1 = press, 0 = release
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void tessera$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        boolean cancelled = false;
        if (action == 1) { // press
            if (info.button() == 0) cancelled = TesseraHooks.onMouseLeft();
            else if (info.button() == 1) cancelled = TesseraHooks.onMouseRight();
        } else if (action == 0) { // release
            if (info.button() == 0) cancelled = TesseraHooks.onMouseLeftRelease();
            else if (info.button() == 1) cancelled = TesseraHooks.onMouseRightRelease();
        }
        if (cancelled) ci.cancel();
    }
}
