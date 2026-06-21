package ratph6.tessera.api

import net.minecraft.util.Mth

/** The local player. All getters return safe defaults when no player exists yet. */
object Player {
    @JvmStatic fun getX(): Double = Mc.player?.x ?: 0.0
    @JvmStatic fun getY(): Double = Mc.player?.y ?: 0.0
    @JvmStatic fun getZ(): Double = Mc.player?.z ?: 0.0

    @JvmStatic fun getMotionX(): Double = Mc.player?.deltaMovement?.x ?: 0.0
    @JvmStatic fun getMotionY(): Double = Mc.player?.deltaMovement?.y ?: 0.0
    @JvmStatic fun getMotionZ(): Double = Mc.player?.deltaMovement?.z ?: 0.0

    /** Yaw normalized to [-180, 180) — Minecraft's raw yRot accumulates unbounded as you keep turning. */
    @JvmStatic fun getYaw(): Double = Mth.wrapDegrees((Mc.player?.yRot ?: 0f).toDouble())
    @JvmStatic fun getPitch(): Double = (Mc.player?.xRot ?: 0f).toDouble()

    @JvmStatic fun getName(): String = Mc.player?.name?.string ?: ""
    @JvmStatic fun getUUID(): String = Mc.player?.stringUUID ?: ""

    @JvmStatic fun getHealth(): Double = (Mc.player?.health ?: 0f).toDouble()
    @JvmStatic fun getMaxHealth(): Double = (Mc.player?.maxHealth ?: 0f).toDouble()
    @JvmStatic fun getHunger(): Int = Mc.player?.foodData?.foodLevel ?: 0
    @JvmStatic fun getSaturation(): Double = (Mc.player?.foodData?.saturationLevel ?: 0f).toDouble()
    @JvmStatic fun getXPLevel(): Int = Mc.player?.experienceLevel ?: 0
    @JvmStatic fun getXPProgress(): Double = (Mc.player?.experienceProgress ?: 0f).toDouble()
    @JvmStatic fun getArmor(): Int = Mc.player?.armorValue ?: 0

    @JvmStatic fun isOnGround(): Boolean = Mc.player?.onGround() ?: false
    @JvmStatic fun isSprinting(): Boolean = Mc.player?.isSprinting ?: false
    @JvmStatic fun isSneaking(): Boolean = Mc.player?.isShiftKeyDown ?: false
    @JvmStatic fun isFlying(): Boolean = Mc.player?.abilities?.flying ?: false

    @JvmStatic fun getHeldItem(): ItemWrapper? = Mc.player?.mainHandItem?.let { ItemWrapper(it) }

    /** Distance from the player to an entity. */
    @JvmStatic fun distanceTo(entity: EntityWrapper): Double =
        (Mc.player?.distanceTo(entity.handle) ?: 0f).toDouble()
}
