package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewArea.class)
public interface IEViewArea {
    @Invoker("getRenderSection")
    @Nullable SectionRenderDispatcher.RenderSection ip_getRenderSection(long sectionNode);
}
