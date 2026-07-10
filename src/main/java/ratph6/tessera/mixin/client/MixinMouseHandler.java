package ratph6.tessera.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
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

    // cursor-move source for MOUSE_MOVE (always) and MOUSE_DRAG (while a button is held). At HEAD
    // xpos()/ypos() still hold the previous position, so deltas come free. Coordinates are gui-scaled
    // to match the Renderer APIs. Observe-only.
    @Inject(method = "onMove", at = @At("HEAD"))
    private void tessera$onMove(long window, double x, double y, CallbackInfo ci) {
        MouseHandler self = (MouseHandler) (Object) this;
        Window win = Minecraft.getInstance().getWindow();
        double sx = MouseHandler.getScaledXPos(win, x);
        double sy = MouseHandler.getScaledYPos(win, y);
        double dx = sx - MouseHandler.getScaledXPos(win, self.xpos());
        double dy = sy - MouseHandler.getScaledYPos(win, self.ypos());
        int button = self.isLeftPressed() ? 0 : self.isRightPressed() ? 1 : self.isMiddlePressed() ? 2 : -1;
        TesseraHooks.onMouseMove(sx, sy, dx, dy, button);
    }
}
