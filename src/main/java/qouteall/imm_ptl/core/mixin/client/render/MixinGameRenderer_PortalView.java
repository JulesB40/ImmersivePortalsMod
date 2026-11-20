package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.view.PortalCamera;
import qouteall.imm_ptl.core.render.view.PortalViewRenderer;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer_PortalView {
    @Shadow
    private boolean useUiLightmap;

    /**
     * Render the portal views right before the main view is rendered
     * (after the main view is extracted and its projection is determined).
     */
    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"
        )
    )
    private void onBeforeRenderingMainLevel(
        CallbackInfo ci,
        @Local(ordinal = 0) Matrix4f projectionMatrix
    ) {
        PortalViewRenderer.renderPortalViewsBeforeMainView(projectionMatrix);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(CallbackInfo ci) {
        PortalViewRenderer.onFrameEnd();
    }

    /**
     * While rendering a portal view, vanilla level rendering renders into the portal view target.
     */
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void onGetMainRenderTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget override = PortalViewRenderer.getMainRenderTargetOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    /**
     * While extracting a portal view, the code that uses the "main camera"
     * (lightmap environment attributes, etc.) uses the portal view camera.
     */
    @Inject(method = "mainCamera", at = @At("HEAD"), cancellable = true)
    private void onGetMainCamera(CallbackInfoReturnable<Camera> cir) {
        if (PortalViewRenderer.isExtractingPortalView()) {
            PortalCamera camera = PortalViewRenderer.getCameraOverride();
            if (camera != null) {
                cir.setReturnValue(camera);
            }
        }
    }

    /**
     * Each dimension uses its own lightmap.
     */
    @Inject(method = "lightmap", at = @At("HEAD"), cancellable = true)
    private void onGetLightmap(CallbackInfoReturnable<GpuTextureView> cir) {
        if (!useUiLightmap) {
            GpuTextureView override = PortalViewRenderer.getLightmapOverride();
            if (override != null) {
                cir.setReturnValue(override);
            }
        }
    }

    @Inject(method = "levelLightmap", at = @At("HEAD"), cancellable = true)
    private void onGetLevelLightmap(CallbackInfoReturnable<GpuTextureView> cir) {
        GpuTextureView override = PortalViewRenderer.getLightmapOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    /**
     * Each portal view layer uses its own lighting uniform buffer
     * (the directional entity lighting differs between dimensions, e.g. the nether).
     */
    @Inject(method = "lighting", at = @At("HEAD"), cancellable = true)
    private void onGetLighting(CallbackInfoReturnable<Lighting> cir) {
        Lighting override = PortalViewRenderer.getLightingOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
