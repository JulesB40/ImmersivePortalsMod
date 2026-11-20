package qouteall.imm_ptl.core.render.view;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.mixin.client.render.IEGameRendererView;
import qouteall.imm_ptl.core.mixin.client.render.IELevelExtractor;
import qouteall.imm_ptl.core.mixin.client.render.IELevelRenderer;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.render.context_management.DimensionRenderHelper;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.q_misc_util.my_util.Plane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders the views seen through portals, for the Minecraft 26.x renderer (OpenGL and Vulkan backends).
 * <p>
 * How it works:
 * <ul>
 *     <li>Before vanilla renders the main view, every visible portal's view is rendered into its own off-screen
 *     target (recursively, so the views inside portal views are rendered first).</li>
 *     <li>A portal view is a normal vanilla "extract then render" of the destination world, with
 *     the camera transformed by the portal, an oblique near plane on the portal's destination surface,
 *     and its own render state and uniform buffers.</li>
 *     <li>When the containing view renders, the portal entity draws its surface textured with that view
 *     ({@link PortalSurfaceRenderer}).</li>
 * </ul>
 * No stencil buffer, no raw OpenGL calls and no shader patching are involved.
 */
@Environment(EnvType.CLIENT)
public final class PortalViewRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Minecraft client = Minecraft.getInstance();

    private PortalViewRenderer() {}

    /**
     * The view currently being processed. Null outside portal view rendering.
     */
    private static @Nullable ViewContext currentView = null;

    /**
     * The root view (the vanilla main view) of this frame.
     */
    private static @Nullable ViewContext rootView = null;

    private static long frameIndex = 0;
    
    /**
     * After the camera jumps (teleportation), the vanilla occlusion graph (built asynchronously)
     * is stale for a few frames. Use synchronous section discovery for the main view meanwhile,
     * to avoid flickering.
     */
    private static int mainViewSyncDiscoveryFrames = 0;
    
    public static void onCameraJumped() {
        mainViewSyncDiscoveryFrames = 3;
    }
    
    public static boolean shouldUseSyncDiscoveryForMainView() {
        return mainViewSyncDiscoveryFrames > 0;
    }

    private static final List<ViewContext> CONTEXT_POOL = new ArrayList<>();
    private static int usedContextNum = 0;

    public static final class ViewContext {
        public int layer;
        public @Nullable ViewContext parent;
        public @Nullable Portal portal;
        public ClientLevel world;
        public Vec3 cameraPos;
        public final Matrix4f viewRotation = new Matrix4f();
        /**
         * The projection without oblique clipping and without mirror flipping (shared by all views of a frame).
         */
        public final Matrix4f baseProjection = new Matrix4f();
        public final Matrix4f projection = new Matrix4f();
        public Frustum frustum;
        public boolean flipped;
        public float xRot;
        public float yRot;
        public int renderDistance;
        /**
         * Whether the renderer is shared with the player's main view.
         * If so, the shared camera-centered storages must not be repositioned.
         */
        public boolean sharedRenderer;
        public @Nullable LevelRenderer renderer;
        public @Nullable LevelExtractor extractor;
        public final Reference2ObjectOpenHashMap<Portal, PortalViewTargets.Slot> renderedChildViews =
            new Reference2ObjectOpenHashMap<>();
        public @Nullable PortalViewTargets.Slot slot;
        public @Nullable GpuTextureView lightmapOverride;
        public @Nullable Lighting lightingOverride;
        public @Nullable PortalCamera camera;
        public boolean isExtracting;
        public boolean isRendering;

        private void reset() {
            parent = null;
            portal = null;
            world = null;
            renderer = null;
            extractor = null;
            renderedChildViews.clear();
            slot = null;
            lightmapOverride = null;
            lightingOverride = null;
            camera = null;
            isExtracting = false;
            isRendering = false;
            sharedRenderer = false;
            flipped = false;
        }
    }

    private static ViewContext allocContext() {
        if (usedContextNum >= CONTEXT_POOL.size()) {
            CONTEXT_POOL.add(new ViewContext());
        }
        ViewContext c = CONTEXT_POOL.get(usedContextNum);
        usedContextNum++;
        c.reset();
        return c;
    }

    // ---- queries used by mixins ----

    public static @Nullable ViewContext getCurrentView() {
        return currentView;
    }

    public static boolean isProcessingPortalView() {
        return currentView != null;
    }

    public static boolean isExtractingPortalView() {
        return currentView != null && currentView.isExtracting;
    }

    public static boolean isRenderingPortalView() {
        return currentView != null && currentView.isRendering;
    }

    /**
     * Whether the current portal view uses the renderer of the player's own view.
     */
    public static boolean isUsingSharedRenderer() {
        return currentView != null && currentView.sharedRenderer;
    }

    public static @Nullable RenderTarget getMainRenderTargetOverride() {
        if (currentView != null && currentView.isRendering && currentView.slot != null) {
            return currentView.slot.getTarget();
        }
        return null;
    }

    public static @Nullable PortalCamera getCameraOverride() {
        if (currentView != null && currentView.camera != null) {
            return currentView.camera;
        }
        return null;
    }

    public static @Nullable GpuTextureView getLightmapOverride() {
        if (currentView != null) {
            return currentView.lightmapOverride;
        }
        return null;
    }

    public static @Nullable Lighting getLightingOverride() {
        if (currentView != null) {
            return currentView.lightingOverride;
        }
        return null;
    }

    /**
     * Used when drawing the portal surface while rendering a view.
     * The view being rendered is either a portal view or the root view (main view).
     */
    public static @Nullable PortalViewTargets.Slot getRenderedViewOfPortal(Portal portal) {
        ViewContext view = currentView != null ? currentView : rootView;
        if (view == null) {
            return null;
        }
        return view.renderedChildViews.get(portal);
    }

    // ---- main entry ----

    /**
     * Called right before vanilla renders the main view (after the main view is extracted).
     *
     * @param mainProjection The final main view projection matrix (including view bobbing).
     */
    public static void renderPortalViewsBeforeMainView(Matrix4f mainProjection) {
        frameIndex++;
        usedContextNum = 0;
        PortalViewTargets.onFrameBegin();
        rootView = null;

        if (IPGlobal.renderMode == IPGlobal.RenderMode.none || IPGlobal.maxPortalLayer == 0) {
            return;
        }

        ClientLevel mainWorld = client.level;
        if (mainWorld == null || client.player == null) {
            return;
        }

        GameRenderer gameRenderer = client.gameRenderer;
        CameraRenderState mainCamera = gameRenderer.gameRenderState().levelRenderState.cameraRenderState;

        ViewContext root = allocContext();
        root.layer = 0;
        root.world = mainWorld;
        root.cameraPos = mainCamera.pos;
        root.viewRotation.set(mainCamera.viewRotationMatrix);
        root.baseProjection.set(mainProjection);
        root.projection.set(mainProjection);
        root.frustum = mainCamera.cullFrustum;
        root.renderDistance = client.options.getEffectiveRenderDistance();
        root.renderer = client.levelRenderer;
        root.extractor = client.levelExtractor;
        rootView = root;

        ProfilerFiller profiler = Profiler.get();
        profiler.push("imm_ptl_portal_views");

        // save the global render states that portal views overwrite
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType savedProjectionType = RenderSystem.getProjectionType();
        GpuBufferSlice savedFog = RenderSystem.getShaderFog();
        GpuBuffer savedGlobals = RenderSystem.getGlobalSettingsUniform();

        try {
            renderChildViews(root);
        }
        catch (Throwable e) {
            LOGGER.error("Error rendering portal views", e);
            currentView = null;
        }
        finally {
            if (savedProjection != null) {
                RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
            }
            if (savedFog != null) {
                RenderSystem.setShaderFog(savedFog);
            }
            if (savedGlobals != null) {
                RenderSystem.setGlobalSettingsUniform(savedGlobals);
            }

            // the dispatchers were prepared with portal cameras during portal view extraction
            client.levelRenderer.entityRenderDispatcher().prepare(
                gameRenderer.mainCamera(), client.crosshairPickEntity
            );
            client.levelRenderer.blockEntityRenderDispatcher().prepare(mainCamera.pos);

            profiler.pop();
        }
    }

    /**
     * Called at the end of the frame.
     */
    public static void onFrameEnd() {
        if (mainViewSyncDiscoveryFrames > 0) {
            mainViewSyncDiscoveryFrames--;
        }
        ViewLayerResources.onFrameEnd();
        if (frameIndex % 600 == 0) {
            PortalViewTargets.trimUnused();
        }
        // the root view is still needed for drawing portal surfaces in the main view,
        // which happens before this
        rootView = null;
    }

    private static void renderChildViews(ViewContext parent) {
        if (parent.layer >= getMaxLayer()) {
            return;
        }

        List<Portal> portals = collectPortalsToRender(parent);
        for (Portal portal : portals) {
            if (RenderStates.getRenderedPortalNum() >= IPGlobal.portalRenderLimit) {
                break;
            }
            ViewContext child = createChildView(parent, portal);
            if (child == null) {
                continue;
            }
            boolean success = renderView(child);
            if (success) {
                parent.renderedChildViews.put(portal, child.slot);
            }
        }
    }

    private static int getMaxLayer() {
        return PortalRendering.getMaxPortalLayer();
    }

    private static List<Portal> collectPortalsToRender(ViewContext view) {
        ObjectArrayList<Portal> result = new ObjectArrayList<>();
        ClientLevel world = view.world;

        for (Portal globalPortal : GlobalPortalStorage.getGlobalPortals(world)) {
            if (!PortalRenderer.shouldSkipRenderingPortal(
                globalPortal, view.cameraPos, view.frustum, RenderStates.getRenderedPortalNum()
            )) {
                result.add(globalPortal);
            }
        }

        for (Entity e : world.entitiesForRendering()) {
            if (e instanceof Portal portal) {
                if (view.portal != null && portal == view.portal) {
                    continue;
                }
                if (!PortalRenderer.shouldSkipRenderingPortal(
                    portal, view.cameraPos, view.frustum, RenderStates.getRenderedPortalNum()
                )) {
                    result.add(portal);
                }
            }
        }

        Vec3 cameraPos = view.cameraPos;
        result.sort(Comparator.comparingDouble(p -> p.getDistanceToNearestPointInPortal(cameraPos)));
        return result;
    }

    private static @Nullable ViewContext createChildView(ViewContext parent, Portal portal) {
        ResourceKey<Level> destDim = portal.getDestDim();
        ClientLevel destWorld = ClientWorldLoader.getOptionalWorld(destDim);
        if (destWorld == null) {
            return null;
        }

        ViewContext child = allocContext();
        child.layer = parent.layer + 1;
        child.parent = parent;
        child.portal = portal;
        child.world = destWorld;
        child.cameraPos = portal.transformPoint(parent.cameraPos);
        child.renderDistance = PortalRenderer.getPortalRenderDistance(portal);
        child.flipped = parent.flipped ^ (portal instanceof Mirror);

        // view rotation: parent view * portal's camera transformation
        child.viewRotation.set(parent.viewRotation);
        Matrix4f cameraTransformation = portal.getAdditionalCameraTransformation();
        if (cameraTransformation != null) {
            child.viewRotation.mul(cameraTransformation);
        }

        child.baseProjection.set(parent.baseProjection);
        Matrix4f projection = new Matrix4f(child.baseProjection);

        // oblique near plane on the portal's destination surface
        Plane innerClipping = portal.getInnerClipping();
        if (innerClipping != null) {
            Vector4f planeEye = computeEyeSpacePlane(innerClipping, child.cameraPos, child.viewRotation);
            Matrix4f oblique = ObliqueProjection.makeOblique(
                projection, planeEye, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()
            );
            if (oblique != null) {
                projection = oblique;
            }
        }

        if (child.flipped) {
            // flip X in clip space. See PortalSurfaceRenderer.
            projection = new Matrix4f().scaling(-1, 1, 1).mul(projection);
        }
        child.projection.set(projection);

        Frustum frustum = new Frustum(child.viewRotation, child.projection);
        frustum.prepare(child.cameraPos.x, child.cameraPos.y, child.cameraPos.z);
        child.frustum = frustum;

        // yaw/pitch of the view direction (used for change detection and some effects)
        Matrix4f inv = new Matrix4f(child.viewRotation).invert();
        Vector3f forward = inv.transformDirection(new Vector3f(0, 0, -1)).normalize();
        child.yRot = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        child.xRot = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -forward.y))));

        return child;
    }

    /**
     * @return the plane in eye space. Points with plane . (x, y, z, 1) >= 0 are kept.
     */
    private static Vector4f computeEyeSpacePlane(Plane plane, Vec3 cameraPos, Matrix4f viewRotation) {
        Vec3 n = plane.normal().normalize();
        Vec3 relPos = plane.pos().subtract(cameraPos);
        // keep the geometry that is very slightly in front of the plane, to avoid cutting the blocks touching the portal
        double offset = 0.002;
        Vector4f planeRel = new Vector4f(
            (float) n.x, (float) n.y, (float) n.z,
            (float) (-n.dot(relPos) + offset)
        );
        // plane transformation: C_eye = (V^-1)^T * C_rel
        Matrix4f invView = new Matrix4f(viewRotation).invert();
        invView.transformTranspose(planeRel);
        return planeRel;
    }

    /**
     * Extract, render nested views, then render the view into a slot.
     */
    private static boolean renderView(ViewContext view) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("portal_view_layer_" + view.layer);

        Portal portal = view.portal;
        assert portal != null;
        ResourceKey<Level> dim = view.world.dimension();

        ResourceKey<Level> playerDim = RenderStates.originalPlayerDimension != null ?
            RenderStates.originalPlayerDimension : client.level.dimension();

        LevelRenderer renderer = ClientWorldLoader.getWorldRenderer(dim);
        LevelExtractor extractor = ClientWorldLoader.getLevelExtractor(dim);
        view.renderer = renderer;
        view.extractor = extractor;
        view.sharedRenderer = (dim == playerDim);

        ViewLayerResources res = ViewLayerResources.get(view.layer);
        DimensionRenderHelper dimHelper = ClientWorldLoader.getDimensionRenderHelper(dim);
        view.camera = dimHelper.portalCamera;
        view.lightingOverride = res.lighting;
        res.lighting.updateLevel(view.world.dimensionType().cardinalLightType());

        SwapState swap = new SwapState();
        swap.save(renderer, extractor);

        ViewContext outerView = currentView;
        currentView = view;
        PortalRendering.pushPortalLayer(portal);
        PortalRendering.onBeginPortalWorldRendering();

        boolean success = false;
        try {
            swap.apply(view, res);

            // ---- extract ----
            view.isExtracting = true;
            DeltaTracker deltaTracker = client.getDeltaTracker();
            float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);

            view.camera.setup(
                view.world, client.getCameraEntity(), view.cameraPos,
                view.viewRotation, view.frustum, view.xRot, view.yRot
            );

            if (dim != playerDim) {
                view.lightmapOverride = dimHelper.updateAndGetLightmap(frameIndex, partialTicks);
            }

            profiler.push("extract");
            extractor.extract(deltaTracker, view.camera, partialTicks);
            fillCameraState(view, res, deltaTracker, partialTicks);
            postProcessExtractedState(view, res.levelRenderState);
            profiler.pop();
            view.isExtracting = false;

            // ---- nested views ----
            // they must be rendered before this view, so that this view can draw their portal surfaces
            renderChildViews(view);
            // the nested views may have used the same renderer with different states
            swap.apply(view, res);

            // ---- render ----
            view.slot = PortalViewTargets.acquire();
            RenderTarget mainTarget = ((IEGameRendererView) client.gameRenderer).ip_getVanillaMainRenderTarget();
            view.slot.getOrCreateTarget(mainTarget.width, mainTarget.height);

            view.isRendering = true;
            profiler.push("render");
            renderExtractedView(view, res);
            profiler.pop();
            view.isRendering = false;

            success = true;
        }
        catch (Throwable e) {
            LOGGER.error("Error rendering portal view of {}", portal, e);
        }
        finally {
            view.isExtracting = false;
            view.isRendering = false;
            swap.restore(renderer, extractor);
            PortalRendering.onEndPortalWorldRendering();
            PortalRendering.popPortalLayer();
            currentView = outerView;
            profiler.pop();
        }

        return success;
    }

    private static void fillCameraState(
        ViewContext view, ViewLayerResources res, DeltaTracker deltaTracker, float partialTicks
    ) {
        CameraRenderState main = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        CameraRenderState cs = res.levelRenderState.cameraRenderState;
        PortalCamera camera = view.camera;
        assert camera != null;

        cs.cameraEntityPartialTicks = main.cameraEntityPartialTicks;
        cs.initialized = true;
        cs.isPanoramicMode = false;
        cs.isFrustumCaptured = false;
        cs.isFirstPerson = main.isFirstPerson;
        cs.smartCull = false;
        cs.pos = view.cameraPos;
        cs.xRot = view.xRot;
        cs.yRot = view.yRot;
        cs.blockPos = BlockPos.containing(view.cameraPos);
        cs.orientation.set(camera.rotation());
        cs.cullFrustum = view.frustum;
        cs.depthFar = main.depthFar;
        cs.projectionMatrix.set(view.projection);
        cs.viewRotationMatrix.set(view.viewRotation);
        cs.hudFov = main.hudFov;
        cs.entityRenderState = main.entityRenderState;
        cs.fogType = camera.getFluidInCamera();
        cs.fogData = res.fogRenderer.setupFog(
            camera, view.renderDistance, deltaTracker,
            client.gameRenderer.bossOverlayWorldDarkening(partialTicks), view.world
        );
    }

    private static void postProcessExtractedState(ViewContext view, LevelRenderState state) {
        // no entity outline post effect in portal views
        state.shouldShowEntityOutlines = false;
        state.playerCompiledSectionCallback = null;
        state.render3dCrosshair = false;
    }

    private static void renderExtractedView(ViewContext view, ViewLayerResources res) {
        LevelRenderState state = res.levelRenderState;
        CameraRenderState cs = state.cameraRenderState;
        GameRenderer gameRenderer = client.gameRenderer;
        GameRenderState gameRenderState = gameRenderer.gameRenderState();

        RenderSystem.setProjectionMatrix(res.projectionBuffer.getBuffer(view.projection), ProjectionType.PERSPECTIVE);
        res.fogRenderer.updateBuffer(cs.fogData);
        GpuBufferSlice terrainFog = res.fogRenderer.getBuffer(FogRenderer.FogMode.WORLD);

        res.globalSettings.update(
            gameRenderState.windowRenderState.width,
            gameRenderState.windowRenderState.height,
            gameRenderState.optionsRenderState.glintStrength,
            state.gameTime,
            state.worldPartialTicks,
            gameRenderState.optionsRenderState.menuBackgroundBlurriness,
            cs.pos,
            gameRenderState.optionsRenderState.textureFiltering == TextureFilteringMethod.RGSS
        );

        boolean renderSky = view.portal == null || !view.portal.isFuseView();

        assert view.renderer != null;
        view.renderer.render(
            ((IEGameRendererView) gameRenderer).ip_getResourcePool(),
            false,
            cs,
            terrainFog,
            cs.fogData.color,
            renderSky,
            false
        );
    }

    /**
     * Saves/applies/restores the vanilla state that a portal view changes.
     */
    private static final class SwapState {
        private ClientLevel level;
        private LevelRenderer levelRenderer;
        private LevelExtractor levelExtractor;
        private HitResult hitResult;
        private LevelRenderState rendererState;
        private LevelRenderState extractorState;
        private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
        private ObjectArrayList<SectionRenderDispatcher.RenderSection> nearbyVisibleSections;

        void save(LevelRenderer renderer, LevelExtractor extractor) {
            level = client.level;
            levelRenderer = client.levelRenderer;
            levelExtractor = client.levelExtractor;
            hitResult = client.hitResult;
            rendererState = ((IELevelRenderer) renderer).ip_getLevelRenderState();
            extractorState = ((IELevelExtractor) extractor).ip_getLevelRenderState();
            visibleSections = ((IELevelRenderer) renderer).ip_getVisibleSections();
            nearbyVisibleSections = ((IELevelRenderer) renderer).ip_getNearbyVisibleSections();
        }

        void apply(ViewContext view, ViewLayerResources res) {
            LevelRenderer renderer = view.renderer;
            LevelExtractor extractor = view.extractor;
            assert renderer != null && extractor != null;
            client.level = view.world;
            ((IEMinecraftClient) client).ip_setWorldRenderer(renderer);
            ((IEMinecraftClient) client).ip_setLevelExtractor(extractor);
            if (BlockManipulationClient.remotePointedDim == view.world.dimension()) {
                client.hitResult = BlockManipulationClient.remoteHitResult;
            }
            else {
                client.hitResult = null;
            }
            ((IELevelRenderer) renderer).ip_setLevelRenderState(res.levelRenderState);
            ((IELevelExtractor) extractor).ip_setLevelRenderState(res.levelRenderState);
            ((IELevelRenderer) renderer).ip_setVisibleSections(res.visibleSections);
            ((IELevelRenderer) renderer).ip_setNearbyVisibleSections(res.nearbyVisibleSections);
        }

        void restore(LevelRenderer renderer, LevelExtractor extractor) {
            ((IELevelRenderer) renderer).ip_setLevelRenderState(rendererState);
            ((IELevelExtractor) extractor).ip_setLevelRenderState(extractorState);
            ((IELevelRenderer) renderer).ip_setVisibleSections(visibleSections);
            ((IELevelRenderer) renderer).ip_setNearbyVisibleSections(nearbyVisibleSections);
            client.level = level;
            ((IEMinecraftClient) client).ip_setWorldRenderer(levelRenderer);
            ((IEMinecraftClient) client).ip_setLevelExtractor(levelExtractor);
            client.hitResult = hitResult;
        }
    }

    /**
     * @return whether the section is in the storage range of the renderer of the current view
     */
    public static boolean isSectionPosNearMainCamera(SectionPos sectionPos) {
        if (rootView == null) {
            return true;
        }
        SectionPos main = SectionPos.of(rootView.cameraPos);
        int r = client.options.getEffectiveRenderDistance();
        return Math.abs(main.x() - sectionPos.x()) <= r && Math.abs(main.z() - sectionPos.z()) <= r;
    }
}
