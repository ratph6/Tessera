package ratph6.tessera.api

import net.minecraft.core.BlockPos

/** World / level queries. `import { World } from 'ratph6.tessera.api'`. */
object World {
    @JvmStatic fun isLoaded(): Boolean = Mc.level != null

    @JvmStatic fun getDimension(): String =
        runCatching { Mc.level?.dimension()?.identifier()?.toString() ?: "" }.getOrDefault("")

    @JvmStatic fun getTime(): Long = Mc.level?.gameTime ?: 0L
    @JvmStatic fun getDayTime(): Long = runCatching { Mc.level?.overworldClockTime ?: 0L }.getOrDefault(0L)
    @JvmStatic fun isRaining(): Boolean = Mc.level?.isRaining ?: false

    @JvmStatic fun getBlock(x: Int, y: Int, z: Int): BlockWrapper? {
        val level = Mc.level ?: return null
        val pos = BlockPos(x, y, z)
        return BlockWrapper(level.getBlockState(pos), pos)
    }

    /** All entities currently being rendered (the loaded ones around the player). */
    @JvmStatic fun getAllEntities(): Array<EntityWrapper> {
        val level = Mc.level ?: return emptyArray()
        return runCatching {
            level.entitiesForRendering().map { EntityWrapper(it) }.toTypedArray()
        }.getOrDefault(emptyArray())
    }

    @JvmStatic fun getEntitiesOfType(type: String): Array<EntityWrapper> =
        getAllEntities().filter { it.getType().contains(type, ignoreCase = true) }.toTypedArray()

    @JvmStatic fun getNearestEntity(type: String, radius: Double): EntityWrapper? =
        getEntitiesOfType(type)
            .filter { Player.distanceTo(it) <= radius }
            .minByOrNull { Player.distanceTo(it) }
}
