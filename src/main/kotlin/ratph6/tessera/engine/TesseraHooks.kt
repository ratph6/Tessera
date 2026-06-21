package ratph6.tessera.engine

import ratph6.tessera.triggers.TriggerType

/**
 * Static entry points that mixins (Java) call into. Kept dependency-light so the mixin classes
 * don't need to know about Kotlin object instances.
 */
object TesseraHooks {
    private val FORMATTING = Regex("§[0-9a-fk-orA-FK-OR]")

    /** Incoming chat. Returns true if a script cancelled it (so the mixin can hide the message). */
    @JvmStatic
    fun onChat(formatted: String): Boolean =
        TesseraEngine.fireChat(TriggerType.CHAT, formatted, FORMATTING.replace(formatted, ""))

    /** An inbound packet (called on the netty thread by MixinConnection). Observe-only. */
    @JvmStatic
    fun onPacketReceived(packet: Any) =
        TesseraEngine.dispatchAsync(TriggerType.PACKET_RECEIVED, packet, packet.javaClass.simpleName)

    /** An outbound packet (called on the netty thread by MixinConnection). Observe-only. */
    @JvmStatic
    fun onPacketSent(packet: Any) =
        TesseraEngine.dispatchAsync(TriggerType.PACKET_SENT, packet, packet.javaClass.simpleName)

    /** A sound about to play (SoundEngine). Observe-only. */
    @JvmStatic
    fun onSoundPlay(name: String) = TesseraEngine.dispatchAsync(TriggerType.SOUND_PLAY, name)

    /** A particle being spawned (ClientLevel). Observe-only. */
    @JvmStatic
    fun onParticle(name: String, x: Double, y: Double, z: Double) =
        TesseraEngine.dispatchAsync(TriggerType.SPAWN_PARTICLE, name, x, y, z)

    /** A living entity died (LivingEntity.die). Observe-only. */
    @JvmStatic
    fun onEntityDeath(entity: net.minecraft.world.entity.Entity) =
        TesseraEngine.dispatchAsync(TriggerType.ENTITY_DEATH, ratph6.tessera.api.EntityWrapper(entity))

    /** The player is sending a chat message (ClientPacketListener.sendChat). Cancellable. */
    @JvmStatic
    fun onMessageSent(message: String): Boolean = TesseraEngine.dispatch(TriggerType.MESSAGE_SENT, message)

    /** An action-bar (overlay) message is being shown (Gui.setOverlayMessage). Cancellable. */
    @JvmStatic
    fun onActionBar(text: String): Boolean = TesseraEngine.dispatch(TriggerType.ACTION_BAR, text)
}
