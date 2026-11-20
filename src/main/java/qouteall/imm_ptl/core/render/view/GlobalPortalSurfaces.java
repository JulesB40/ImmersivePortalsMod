package qouteall.imm_ptl.core.render.view;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;

/**
 * Global portals (world wrapping portals, vertical connecting portals) are not in the world's entity list.
 * Their surfaces are submitted here.
 */
@Environment(EnvType.CLIENT)
public final class GlobalPortalSurfaces {
    private GlobalPortalSurfaces() {}
    
    public static void submit(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        Vec3 cameraPos = state.cameraRenderState.pos;
        for (Portal portal : GlobalPortalStorage.getGlobalPortals(world)) {
            PortalViewTargets.Slot slot = PortalViewRenderer.getRenderedViewOfPortal(portal);
            if (slot == null) {
                continue;
            }
            // the pose stack is at the camera position
            Vec3 meshOffset = portal.getOriginPos().subtract(cameraPos);
            PortalSurfaceRenderer.submitPortalSurface(
                portal, slot, portal instanceof Mirror, poseStack, collector, meshOffset
            );
        }
    }
}
