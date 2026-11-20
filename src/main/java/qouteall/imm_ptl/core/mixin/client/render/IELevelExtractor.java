package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelExtractor.class)
public interface IELevelExtractor {
    @Accessor("levelRenderState")
    LevelRenderState ip_getLevelRenderState();
    
    @Mutable
    @Accessor("levelRenderState")
    void ip_setLevelRenderState(LevelRenderState state);
    
    @Accessor("level")
    @Nullable ClientLevel ip_getLevel();
}
