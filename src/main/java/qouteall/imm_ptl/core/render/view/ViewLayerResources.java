package qouteall.imm_ptl.core.render.view;

import com.mojang.blaze3d.platform.Lighting;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import java.util.ArrayList;
import java.util.List;

/**
 * GPU buffers and render states owned by one portal-view nesting layer.
 * <p>
 * Several uniform buffers (fog, projection, global settings, lighting) are written once per frame by vanilla.
 * Some of them are persistently-mapped ring buffers, so writing them again within the same frame would also
 * change what the already-recorded draws read. Each layer therefore owns its own copies.
 */
@Environment(EnvType.CLIENT)
public final class ViewLayerResources implements AutoCloseable {
    public final int layer;
    public final LevelRenderState levelRenderState = new LevelRenderState();
    public final ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections = new ObjectArrayList<>(4096);
    public final ObjectArrayList<SectionRenderDispatcher.RenderSection> nearbyVisibleSections = new ObjectArrayList<>(64);
    public final FogRenderer fogRenderer = new FogRenderer();
    public final ProjectionMatrixBuffer projectionBuffer;
    public final GlobalSettingsUniform globalSettings = new GlobalSettingsUniform();
    public final Lighting lighting = new Lighting();

    private ViewLayerResources(int layer) {
        this.layer = layer;
        this.projectionBuffer = new ProjectionMatrixBuffer("imm_ptl portal view layer " + layer);
    }

    @Override
    public void close() {
        fogRenderer.close();
        projectionBuffer.close();
        globalSettings.close();
        lighting.close();
    }

    private static final List<ViewLayerResources> LAYERS = new ArrayList<>();

    /**
     * @param layer 1 for the views directly seen from the main view, 2 for views inside them, etc.
     */
    public static ViewLayerResources get(int layer) {
        while (LAYERS.size() < layer) {
            LAYERS.add(new ViewLayerResources(LAYERS.size() + 1));
        }
        return LAYERS.get(layer - 1);
    }

    public static void onFrameEnd() {
        for (ViewLayerResources r : LAYERS) {
            r.fogRenderer.endFrame();
        }
    }

    public static void closeAll() {
        for (ViewLayerResources r : LAYERS) {
            r.close();
        }
        LAYERS.clear();
    }
}
