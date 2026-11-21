package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface IEGameRendererView {
    @Accessor("resourcePool")
    CrossFrameResourcePool ip_getResourcePool();
    
    @Accessor("globalSettingsUniform")
    GlobalSettingsUniform ip_getGlobalSettingsUniform();
    
    @Accessor("fogRenderer")
    FogRenderer ip_getFogRenderer();
    
    @org.spongepowered.asm.mixin.Mutable
    @Accessor("fogRenderer")
    void ip_setFogRenderer(FogRenderer fogRenderer);
    
    @Accessor("mainRenderTarget")
    com.mojang.blaze3d.pipeline.RenderTarget ip_getVanillaMainRenderTarget();
    
    @Accessor("lightmapRenderStateExtractor")
    LightmapRenderStateExtractor ip_getLightmapRenderStateExtractor();
}
