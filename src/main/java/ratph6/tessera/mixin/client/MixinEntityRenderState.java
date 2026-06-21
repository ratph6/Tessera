package ratph6.tessera.mixin.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ratph6.tessera.engine.TesseraRenderTarget;

// transient back-reference to the source entity
@Mixin(EntityRenderState.class)
public class MixinEntityRenderState implements TesseraRenderTarget {

    @Unique
    private Entity tessera$sourceEntity;

    @Override
    public Entity tessera$entity() {
        return tessera$sourceEntity;
    }

    @Override
    public void tessera$setEntity(Entity entity) {
        this.tessera$sourceEntity = entity;
    }
}
