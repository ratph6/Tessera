package ratph6.tessera.api

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

/** Lightweight wrapper around a Minecraft entity, returned to scripts. */
class EntityWrapper(@JvmField val handle: Entity) {
    fun getX(): Double = handle.x
    fun getY(): Double = handle.y
    fun getZ(): Double = handle.z
    fun getName(): String = runCatching { handle.name.string }.getOrDefault("")
    fun getUUID(): String = handle.stringUUID
    fun getType(): String = runCatching { handle.type.toString() }.getOrDefault("unknown")
    fun distanceTo(other: EntityWrapper): Double = handle.distanceTo(other.handle).toDouble()
    fun isPlayer(): Boolean = handle is net.minecraft.world.entity.player.Player

    /** Body yaw in degrees, normalized to [-180, 180) (raw yRot accumulates unbounded). */
    fun getYaw(): Double = Mth.wrapDegrees(handle.yRot.toDouble())

    /** True if this entity is the local player (matched by UUID, not name). */
    fun isLocalPlayer(): Boolean = Mc.player?.let { handle.uuid == it.uuid } ?: false
}

/** Lightweight wrapper around an item stack. */
class ItemWrapper(@JvmField val stack: ItemStack) {
    fun getName(): String = runCatching { stack.hoverName.string }.getOrDefault("")
    fun getStackSize(): Int = stack.count
    fun isEmpty(): Boolean = stack.isEmpty
    fun getRawName(): String = runCatching { stack.item.toString() }.getOrDefault("")
}

/** Lightweight wrapper around a block at a position. */
class BlockWrapper(@JvmField val state: BlockState, @JvmField val pos: BlockPos) {
    fun getX(): Int = pos.x
    fun getY(): Int = pos.y
    fun getZ(): Int = pos.z
    fun getType(): String = runCatching { state.block.name.string }.getOrElse { state.toString() }
    fun isAir(): Boolean = state.isAir
}
