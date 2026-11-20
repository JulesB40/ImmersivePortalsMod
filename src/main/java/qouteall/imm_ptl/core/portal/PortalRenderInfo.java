package qouteall.imm_ptl.core.portal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;

/**
 * A portal's rendering related things.
 * <p>
 * The old OpenGL occlusion-query based visibility prediction was removed:
 * the 26.x rendering API has no occlusion queries.
 * Portal visibility now uses the vanilla section occlusion culling result instead
 * (see {@link qouteall.imm_ptl.core.render.view.PortalViewRenderer}).
 */
@Environment(EnvType.CLIENT)
public class PortalRenderInfo {
    
    public static void init() {
        Portal.PORTAL_DISPOSE_SIGNAL.register(portal -> {
            if (portal.level().isClientSide()) {
                portal.portalRenderInfo = null;
            }
        });
    }
    
    @Nullable
    public static PortalRenderInfo getOptional(Portal portal) {
        Validate.isTrue(portal.level().isClientSide());
        return portal.portalRenderInfo;
    }
    
    public static PortalRenderInfo get(Portal portal) {
        Validate.isTrue(portal.level().isClientSide());
        if (portal.portalRenderInfo == null) {
            portal.portalRenderInfo = new PortalRenderInfo();
        }
        return portal.portalRenderInfo;
    }
}
