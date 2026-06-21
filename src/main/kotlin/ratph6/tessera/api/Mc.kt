package ratph6.tessera.api

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component

/** Internal convenience accessors over the Minecraft client singleton. Not exposed to scripts. */
internal object Mc {
    val client: Minecraft get() = Minecraft.getInstance()
    val player: LocalPlayer? get() = client.player
    val level: ClientLevel? get() = client.level
    val connection: ClientPacketListener? get() = client.connection

    fun literal(text: String): Component = Component.literal(text)
}
