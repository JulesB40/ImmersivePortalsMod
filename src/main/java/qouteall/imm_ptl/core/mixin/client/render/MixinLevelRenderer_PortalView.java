package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.view.GlobalPortalSurfaces;
import qouteall.imm_ptl.core.render.view.PortalViewRenderer;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer_PortalView {
    /**
     * Don't move the camera-centered section storage of the player's renderer for portal views,
     * otherwise the sections would be reassigned and recompiled every frame.
     */
    @Inject(method = "repositionCamera", at = @At("HEAD"), cancellable = true)
    private void onRepositionCamera(CameraRenderState camera, CallbackInfo ci) {
        if (PortalViewRenderer.isUsingSharedRenderer()) {
            ci.cancel();
        }
    }

    /**
     * Portal views use their own visible section discovery, not the occlusion graph.
     * But keep the graph's chunk/empty-section bookkeeping up to date for the renderers
     * that are only seen through portals (the player may enter that dimension later).
     */
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"
        )
    )
    private void wrapOcclusionGraphUpdate(
        SectionOcclusionGraph graph, CameraRenderState camera, int fov, ChunkLoadingRenderState chunkLoading,
        Operation<Void> original
    ) {
        if (PortalViewRenderer.isRenderingPortalView()) {
            // the chunk tracking sets of the player's world are consumed by the player's renderer
            if (!PortalViewRenderer.isViewingPlayerWorld()) {
                graph.updateLoadedChunks(chunkLoading.addedLoadedChunks, chunkLoading.removedLoadedChunks);
                graph.updateEmptySections(chunkLoading.addedEmptySections, chunkLoading.removedEmptySections);
            }
            return;
        }
        original.call(graph, camera, fov, chunkLoading);
    }

    /**
     * The shared renderer's translucent geometry is sorted for the player's camera.
     * Re-sorting it for a portal view camera (and back for the player camera in the next frame)
     * would re-sort thousands of sections every frame.
     */
    @WrapOperation(
        method = "compileSections",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;scheduleTranslucentSectionResort(Lnet/minecraft/world/phys/Vec3;)V"
        )
    )
    private void wrapTranslucentResort(LevelRenderer renderer, net.minecraft.world.phys.Vec3 cameraPos, Operation<Void> original) {
        if (PortalViewRenderer.isRenderingPortalView() && PortalViewRenderer.isUsingSharedRenderer()) {
            return;
        }
        original.call(renderer, cameraPos);
    }
    
    /**
     * Global portals are not entities in the world entity list, submit their surfaces here.
     */
    @Inject(method = "submitEntities", at = @At("RETURN"))
    private void onSubmitEntities(
        PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, CallbackInfo ci
    ) {
        GlobalPortalSurfaces.submit(poseStack, levelRenderState, output);
    }
}
