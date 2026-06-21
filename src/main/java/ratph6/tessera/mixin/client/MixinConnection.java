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

/**
 * Feeds Tessera's {@code packetReceived} / {@code packetSent} triggers. Every inbound packet passes
 * through {@code channelRead0}; every outbound packet funnels through the 3-arg {@code send}. Both
 * run on the netty I/O thread, so {@link TesseraHooks} marshals onto the JS thread — these triggers are
 * observe-only (the packet has already been handed off, so there is nothing to cancel).
 */
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
        at = @At("HEAD")
    )
    private void tessera$onOutbound(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        TesseraHooks.onPacketSent(packet);
    }
}
