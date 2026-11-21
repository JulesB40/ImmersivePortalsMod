package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The old position-snapping logic for entities crossing portals was replaced by
 * {@link qouteall.imm_ptl.core.teleportation.PortalCrossingSmoother}.
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntity_C {
}
