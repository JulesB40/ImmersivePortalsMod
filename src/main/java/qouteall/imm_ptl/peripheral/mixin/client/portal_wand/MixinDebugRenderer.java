package qouteall.imm_ptl.peripheral.mixin.client.portal_wand;

import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The portal wand rendering moved to {@link qouteall.imm_ptl.peripheral.wand.PortalWandItem#initClient()}
 * (Fabric level render event). Kept empty so the mixin config stays stable.
 */
@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {
}
