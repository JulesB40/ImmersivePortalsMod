package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The sky renderer keeps the render target that was the main target when it was created.
 * Make it render into the current target, so that portal views get their sky
 * (and so that it never keeps a portal view target that may be released later).
 */
@Mixin(SkyRenderer.class)
public class MixinSkyRenderer_PortalView {
    @Redirect(
        method = "render",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/SkyRenderer;renderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"
        )
    )
    private RenderTarget redirectRenderTarget(SkyRenderer instance) {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }
}
