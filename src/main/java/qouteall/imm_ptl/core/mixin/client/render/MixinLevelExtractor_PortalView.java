package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.VisibleSectionDiscovery;
import qouteall.imm_ptl.core.render.view.PortalViewRenderer;

@Mixin(LevelExtractor.class)
public class MixinLevelExtractor_PortalView {
    @Shadow
    @Final
    private LevelRenderer levelRenderer;

    @Shadow
    private ClientLevel level;

    /**
     * Portal views always re-discover their visible sections (their cameras move a lot).
     */
    @ModifyExpressionValue(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"
        )
    )
    private boolean modifyConsumeFrustumUpdate(boolean original) {
        if (PortalViewRenderer.isExtractingPortalView()) {
            return true;
        }
        if (PortalViewRenderer.shouldUseSyncDiscoveryForMainView()) {
            return true;
        }
        return original;
    }

    /**
     * Portal views use a breadth-first section discovery from the portal,
     * instead of the vanilla occlusion graph that's built for the main camera.
     */
    @Inject(method = "applyFrustum", at = @At("HEAD"), cancellable = true)
    private void onApplyFrustum(Frustum frustum, CallbackInfo ci) {
        if (PortalViewRenderer.isExtractingPortalView()) {
            PortalViewRenderer.ViewContext view = PortalViewRenderer.getCurrentView();
            if (view != null && level != null) {
                VisibleSectionDiscovery.discoverVisibleSections(
                    level, levelRenderer, view.cameraPos, view.frustum, view.renderDistance,
                    view.portal, view.cullStart,
                    ((IELevelRenderer) levelRenderer).ip_getVisibleSections()
                );
                ((IELevelRenderer) levelRenderer).ip_getNearbyVisibleSections().clear();
            }
            ci.cancel();
        }
        else if (PortalViewRenderer.shouldUseSyncDiscoveryForMainView() && level != null) {
            net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();
            VisibleSectionDiscovery.discoverVisibleSections(
                level, levelRenderer, camera.position(), frustum,
                net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance(),
                null, null,
                ((IELevelRenderer) levelRenderer).ip_getVisibleSections()
            );
            ((IELevelRenderer) levelRenderer).ip_getNearbyVisibleSections().clear();
            ci.cancel();
        }
    }

    @WrapOperation(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/SectionUpdateTracker;repositionCamera(Lnet/minecraft/core/SectionPos;)V"
        )
    )
    private void wrapRepositionTracker(
        SectionUpdateTracker tracker, SectionPos pos, Operation<Void> original
    ) {
        if (PortalViewRenderer.isUsingSharedRenderer()) {
            return;
        }
        original.call(tracker, pos);
    }

    /**
     * Don't consume the chunk update tracking of the player's renderer in portal views.
     * The player's occlusion graph needs them.
     */
    @WrapOperation(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;flipUpdateTrackingSets()V"
        )
    )
    private void wrapFlipUpdateTrackingSets(ClientChunkCache chunkCache, Operation<Void> original) {
        if (PortalViewRenderer.isExtractingPortalView() && PortalViewRenderer.isViewingPlayerWorld()) {
            return;
        }
        original.call(chunkCache);
    }

    /**
     * The level only notifies its own extractor about block/light changes.
     * Forward them to the far view extractor of the same level.
     */
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void onSetSectionDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged, CallbackInfo ci) {
        if (level == null) {
            return;
        }
        LevelExtractor far = qouteall.imm_ptl.core.ClientWorldLoader.getFarViewExtractorIfPresent(level.dimension());
        if (far != null && far != (Object) this) {
            ((IELevelExtractor) far).ip_setSectionDirty(sectionX, sectionY, sectionZ, playerChanged);
        }
    }
    
    /**
     * The particle engine only holds the particles of the player's dimension.
     */
    @WrapOperation(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;extract(Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)V"
        )
    )
    private void wrapExtractParticles(
        ParticleEngine engine, ParticlesRenderState state, Frustum frustum, Camera camera, float partialTicks,
        Operation<Void> original
    ) {
        if (PortalViewRenderer.isExtractingPortalView() && !PortalViewRenderer.isViewingPlayerWorld()) {
            return;
        }
        original.call(engine, state, frustum, camera, partialTicks);
    }

    /**
     * Add the entities that are going through portals into this world (seen on this side of the portal).
     */
    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void onExtractVisibleEntities(
        Camera camera, Frustum frustum, net.minecraft.client.DeltaTracker deltaTracker,
        net.minecraft.client.renderer.state.level.LevelRenderState output, CallbackInfo ci
    ) {
        if (level == null) {
            return;
        }
        qouteall.imm_ptl.core.render.CrossPortalEntityRenderer.addProjectedEntities(
            level, levelRenderer.entityRenderDispatcher(), camera.position(),
            deltaTracker.getGameTimeDeltaPartialTick(true), output
        );
    }
    
    /**
     * The portal surfaces are always extracted (even if their sections are not considered visible),
     * and only drawn if their views got rendered.
     */
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void onIsEntityVisible(
        Entity entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks,
        long chunkFadeDuration, CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity instanceof Portal portal) {
            cir.setReturnValue(portal.isPortalValid());
        }
    }
}
