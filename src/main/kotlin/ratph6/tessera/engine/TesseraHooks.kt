package ratph6.tessera.engine

import ratph6.tessera.triggers.TriggerRegistry
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

    // client-side damage pulse (driven by ClientboundDamageEventPacket). Observe-only. Wrapper first so
    // setFilteredClass matches the entity; damageType reaches multi-param (bytecode) callbacks
    @JvmStatic
    fun onEntityDamage(entity: net.minecraft.world.entity.Entity, damageType: String) =
        TesseraEngine.dispatchAsync(TriggerType.ENTITY_DAMAGE, ratph6.tessera.api.EntityWrapper(entity), damageType)

    // chat-like: must go through fireChat so .setCriteria()/match modes are honoured
    @JvmStatic
    fun onMessageSent(message: String): Boolean =
        TesseraEngine.fireChat(TriggerType.MESSAGE_SENT, message, FORMATTING.replace(message, ""))

    @JvmStatic
    fun onActionBar(text: String): Boolean =
        TesseraEngine.fireChat(TriggerType.ACTION_BAR, text, FORMATTING.replace(text, ""))

    // raw in-world clicks; true if a script cancelled it (mixin vetoes the physical click)
    @JvmStatic
    fun onMouseLeft(): Boolean = TesseraEngine.dispatch(TriggerType.MOUSE_LEFT)

    @JvmStatic
    fun onMouseRight(): Boolean = TesseraEngine.dispatch(TriggerType.MOUSE_RIGHT)

    @JvmStatic
    fun onMouseLeftRelease(): Boolean = TesseraEngine.dispatch(TriggerType.MOUSE_LEFT_RELEASE)

    @JvmStatic
    fun onMouseRightRelease(): Boolean = TesseraEngine.dispatch(TriggerType.MOUSE_RIGHT_RELEASE)

    // cursor move on the render thread, gui-scaled coords. MOUSE_MOVE gets [x, y]; MOUSE_DRAG fires
    // only while a button is held (button 0/1/2; -1 = none) and gets [dx, dy, x, y, button].
    // hasType-guarded — this runs for every pixel of cursor travel
    @JvmStatic
    fun onMouseMove(x: Double, y: Double, dx: Double, dy: Double, button: Int) {
        if (TriggerRegistry.hasType(TriggerType.MOUSE_MOVE))
            TesseraEngine.dispatch(TriggerType.MOUSE_MOVE, arrayOf(x, y))
        if (button >= 0 && TriggerRegistry.hasType(TriggerType.MOUSE_DRAG))
            TesseraEngine.dispatch(TriggerType.MOUSE_DRAG, arrayOf<Any>(dx, dy, x, y, button))
    }

    // before a packet goes out. On the JS thread this is synchronous and cancellable (true = veto the
    // send); sends from other threads (netty keepalives etc.) can't be vetoed — observe-only there
    @JvmStatic
    fun onPrePacketSend(packet: Any): Boolean {
        if (!TriggerRegistry.hasType(TriggerType.PRE_PACKET_SEND)) return false
        if (TesseraEngine.isOnJsThread())
            return TesseraEngine.dispatch(TriggerType.PRE_PACKET_SEND, packet, packet.javaClass.simpleName)
        TesseraEngine.dispatchAsync(TriggerType.PRE_PACKET_SEND, packet, packet.javaClass.simpleName)
        return false
    }
}
