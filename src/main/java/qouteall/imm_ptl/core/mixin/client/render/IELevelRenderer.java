package qouteall.imm_ptl.core.mixin.client.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface IELevelRenderer {
    @Accessor("levelRenderState")
    LevelRenderState ip_getLevelRenderState();
    
    @Mutable
    @Accessor("levelRenderState")
    void ip_setLevelRenderState(LevelRenderState state);
    
    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> ip_getVisibleSections();
    
    @Mutable
    @Accessor("visibleSections")
    void ip_setVisibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> list);
    
    @Accessor("nearbyVisibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> ip_getNearbyVisibleSections();
    
    @Mutable
    @Accessor("nearbyVisibleSections")
    void ip_setNearbyVisibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> list);
    
    @Accessor("viewArea")
    @Nullable ViewArea ip_getViewArea();
}
