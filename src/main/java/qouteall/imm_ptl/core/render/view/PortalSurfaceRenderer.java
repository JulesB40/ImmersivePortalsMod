package qouteall.imm_ptl.core.render.view;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a portal's surface, textured with its already-rendered view (screen-space lookup).
 * <p>
 * The surface is an ordinary opaque, depth-tested and depth-writing draw inside the vanilla render passes.
 * So it's correctly occluded by blocks/entities in front of it, and the parts of entities
 * that went behind the portal plane are hidden by it.
 * This replaces the old OpenGL stencil-buffer approach and works on both the OpenGL and Vulkan backends.
 */
@Environment(EnvType.CLIENT)
public final class PortalSurfaceRenderer {
    private PortalSurfaceRenderer() {}

    private static final RenderPipeline.Snippet PORTAL_VIEW_SNIPPET = RenderPipeline.builder()
        .withVertexShader(Identifier.fromNamespaceAndPath("immersive_portals", "core/portal_view"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("immersive_portals", "core/portal_view"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        // mirrors flip the winding order
        .withCull(false)
        .buildSnippet();

    public static final RenderPipeline PORTAL_VIEW_PIPELINE = RenderPipeline.builder(PORTAL_VIEW_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("immersive_portals", "pipeline/portal_view"))
        .build();

    public static final RenderPipeline PORTAL_VIEW_FLIPPED_PIPELINE = RenderPipeline.builder(PORTAL_VIEW_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("immersive_portals", "pipeline/portal_view_flipped"))
        .withShaderDefine("IMM_PTL_FLIP_X")
        .build();

    private static final List<RenderType> RENDER_TYPES = new ArrayList<>();
    private static final List<RenderType> FLIPPED_RENDER_TYPES = new ArrayList<>();

    public static void init() {
        RenderPipelines.register(PORTAL_VIEW_PIPELINE);
        RenderPipelines.register(PORTAL_VIEW_FLIPPED_PIPELINE);
    }

    public static RenderType getRenderType(PortalViewTargets.Slot slot, boolean flipX) {
        List<RenderType> list = flipX ? FLIPPED_RENDER_TYPES : RENDER_TYPES;
        while (list.size() <= slot.index) {
            int i = list.size();
            PortalViewTargets.Slot s = PortalViewTargets.get(i);
            list.add(RenderType.create(
                (flipX ? "imm_ptl_portal_view_flipped_" : "imm_ptl_portal_view_") + i,
                RenderSetup.builder(flipX ? PORTAL_VIEW_FLIPPED_PIPELINE : PORTAL_VIEW_PIPELINE)
                    .withTexture("Sampler0", s.textureId)
                    .createRenderSetup()
            ));
        }
        return list.get(slot.index);
    }

    /**
     * @param meshOffset the portal origin position relative to the pose stack origin
     */
    public static void submitPortalSurface(
        Portal portal,
        PortalViewTargets.Slot slot,
        boolean flipX,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        Vec3 meshOffset
    ) {
        RenderType renderType = getRenderType(slot, flipX);
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            emitMesh(portal, pose, buffer, meshOffset);
        });
    }

    private static void emitMesh(Portal portal, PoseStack.Pose pose, VertexConsumer buffer, Vec3 meshOffset) {
        portal.renderViewAreaMesh(
            meshOffset,
            (p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z) -> {
                buffer.addVertex(pose, (float) p0x, (float) p0y, (float) p0z);
                buffer.addVertex(pose, (float) p1x, (float) p1y, (float) p1z);
                buffer.addVertex(pose, (float) p2x, (float) p2y, (float) p2z);
            }
        );
    }
}
