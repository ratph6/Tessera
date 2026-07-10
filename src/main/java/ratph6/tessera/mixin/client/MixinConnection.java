package ratph6.tessera.mixin.client;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ratph6.tessera.engine.TesseraHooks;

// packetReceived/packetSent (observe-only, hooks marshal to JS) and prePacketSend (cancellable for
// main-thread sends — cancelling drops the packet before it reaches netty). channelRead0 runs on the
// netty I/O thread; send is called from the game thread for player actions, netty for keepalives.
@Mixin(Connection.class)
public class MixinConnection {

    @Inject(
        method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD")
    )
    private void tessera$onInbound(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        TesseraHooks.onPacketReceived(packet);
    }

    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void tessera$onOutbound(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (TesseraHooks.onPrePacketSend(packet)) {
            ci.cancel(); // vetoed: packetSent must not fire either — the packet never goes out
            return;
        }
        TesseraHooks.onPacketSent(packet);
    }
}
