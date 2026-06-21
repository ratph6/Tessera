package ratph6.tessera.engine

import ratph6.tessera.triggers.TriggerType

// Static entry points the Java mixins call into; kept dependency-light.
object TesseraHooks {
    private val FORMATTING = Regex("§[0-9a-fk-orA-FK-OR]")

    // incoming chat; true if a script cancelled it (so the mixin can hide the message)
    @JvmStatic
    fun onChat(formatted: String): Boolean =
        TesseraEngine.fireChat(TriggerType.CHAT, formatted, FORMATTING.replace(formatted, ""))

    @JvmStatic
    fun onPacketReceived(packet: Any) =
        TesseraEngine.dispatchAsync(TriggerType.PACKET_RECEIVED, packet, packet.javaClass.simpleName)

    @JvmStatic
    fun onPacketSent(packet: Any) =
        TesseraEngine.dispatchAsync(TriggerType.PACKET_SENT, packet, packet.javaClass.simpleName)

    @JvmStatic
    fun onSoundPlay(name: String) = TesseraEngine.dispatchAsync(TriggerType.SOUND_PLAY, name)

    @JvmStatic
    fun onParticle(name: String, x: Double, y: Double, z: Double) =
        TesseraEngine.dispatchAsync(TriggerType.SPAWN_PARTICLE, name, x, y, z)

    @JvmStatic
    fun onEntityDeath(entity: net.minecraft.world.entity.Entity) =
        TesseraEngine.dispatchAsync(TriggerType.ENTITY_DEATH, ratph6.tessera.api.EntityWrapper(entity))

    @JvmStatic
    fun onMessageSent(message: String): Boolean = TesseraEngine.dispatch(TriggerType.MESSAGE_SENT, message)

    @JvmStatic
    fun onActionBar(text: String): Boolean = TesseraEngine.dispatch(TriggerType.ACTION_BAR, text)
}
