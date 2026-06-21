package ratph6.tessera.engine;

import net.minecraft.world.entity.Entity;

/**
 * Duck-typing interface mixed into {@code EntityRenderState} so Tessera can recover the live
 * {@link Entity} behind a render state. Modern Minecraft renders from an extracted, entity-less
 * render state; we stash the source entity onto it during extraction (see
 * {@code MixinEntityRenderDispatcher}) and read it back at submit time to drive the
 * {@code renderEntity} / {@code postRenderEntity} triggers.
 *
 * <p>Lives in the engine package (not {@code ratph6.tessera.mixin.*}) because game code references it
 * directly, and Mixin forbids direct references to non-mixin classes inside a mixin package.
 */
public interface TesseraRenderTarget {
    Entity tessera$entity();

    void tessera$setEntity(Entity entity);
}
