package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.view.PortalSurfaceRenderer;
import qouteall.imm_ptl.core.render.view.PortalViewRenderer;
import qouteall.imm_ptl.core.render.view.PortalViewTargets;

/**
 * Draws the portal surface, textured with the portal's view that was rendered earlier in the frame.
 */
@Environment(EnvType.CLIENT)
public class PortalEntityRenderer extends EntityRenderer<Portal, PortalEntityRenderer.State> {
    
    public static class State extends EntityRenderState {
        public @Nullable Portal portal;
        public Vec3 originPos = Vec3.ZERO;
    }
    
    public PortalEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
    
    @Override
    public State createRenderState() {
        return new State();
    }
    
    @Override
    public boolean shouldRender(Portal entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
        // Extraction happens before the portal views are rendered,
        // so the surface is always extracted and only drawn if its view got rendered.
        return entity.isPortalValid();
    }
    
    @Override
    public void extractRenderState(Portal portal, State state, float partialTicks) {
        super.extractRenderState(portal, state, partialTicks);
        state.portal = portal;
        state.originPos = portal.getOriginPos();
    }
    
    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Portal portal = state.portal;
        // submitting happens after the views inside the view being rendered are rendered
        PortalViewTargets.Slot slot = portal == null ? null : PortalViewRenderer.getRenderedViewOfPortal(portal);
        if (portal == null || slot == null) {
            return;
        }
        // the pose stack is translated to the (interpolated) entity position
        Vec3 meshOffset = state.originPos.subtract(state.x, state.y, state.z);
        PortalSurfaceRenderer.submitPortalSurface(
            portal, slot, portal instanceof Mirror, poseStack, collector, meshOffset
        );
    }
}
