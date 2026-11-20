package qouteall.imm_ptl.core.render.context_management;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.render.view.PortalCamera;
import qouteall.q_misc_util.Helper;

/**
 * Per-dimension rendering resources.
 * <p>
 * Each dimension seen through a portal needs its own lightmap, computed from that dimension's
 * environment attributes (sky light color/factor, block light tint) at the portal camera position.
 * Using the player dimension's lightmap for other dimensions makes, for example,
 * the nether look like it's lit by the overworld sky.
 */
@Environment(EnvType.CLIENT)
public class DimensionRenderHelper {
    private static final Minecraft client = Minecraft.getInstance();
    public final Level world;

    /**
     * The camera used for rendering portal views into this dimension.
     * It's kept across frames so that its environment attribute probe interpolates smoothly.
     */
    public final PortalCamera portalCamera = new PortalCamera();

    @Nullable
    private Lightmap lightmap;
    private final LightmapRenderState lightmapRenderState = new LightmapRenderState();
    private final LightmapRenderStateExtractor lightmapExtractor;
    private long lightmapUpdatedFrame = -1;

    public DimensionRenderHelper(Level world) {
        this.world = world;
        this.lightmapExtractor = new LightmapRenderStateExtractor(client.gameRenderer, client);
    }

    public void tick() {
        lightmapExtractor.tick();
        portalCamera.tickProbe();
    }

    /**
     * Must be called while the world is switched to this dimension
     * and the main camera is redirected to {@link #portalCamera}.
     */
    public GpuTextureView updateAndGetLightmap(long frameIndex, float partialTick) {
        if (lightmap == null) {
            lightmap = new Lightmap();
            Helper.log("Created lightmap for " + world.dimension().identifier());
        }
        if (lightmapUpdatedFrame != frameIndex) {
            lightmapUpdatedFrame = frameIndex;
            lightmapExtractor.extract(lightmapRenderState, partialTick);
            lightmap.render(lightmapRenderState);
        }
        return lightmap.getTextureView();
    }

    public @Nullable GpuTextureView getLightmapIfExists() {
        return lightmap == null ? null : lightmap.getTextureView();
    }

    public void cleanUp() {
        if (lightmap != null) {
            lightmap.close();
            lightmap = null;
        }
    }
}
