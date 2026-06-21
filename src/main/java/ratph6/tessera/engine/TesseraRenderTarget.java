package ratph6.tessera.engine;

import net.minecraft.world.entity.Entity;

// Mixed into EntityRenderState to recover the source entity from an (entity-less) render state.
// Lives in engine, not mixin.*, because game code references it directly and Mixin forbids that
// inside a mixin package.
public interface TesseraRenderTarget {
    Entity tessera$entity();

    void tessera$setEntity(Entity entity);
}
