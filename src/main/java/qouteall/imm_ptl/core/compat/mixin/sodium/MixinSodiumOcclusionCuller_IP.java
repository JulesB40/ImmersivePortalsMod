package qouteall.imm_ptl.core.compat.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import qouteall.imm_ptl.core.compat.sodium_compatibility.IESodiumViewport_IP;

/**
 * The camera of a portal view is behind the portal's destination surface: it's outside the frustum
 * (because of the oblique near plane) and it may be inside solid blocks.
 * So the graph search of a portal view starts from the destination surface, without cave culling.
 * This runs on Sodium's culling thread, so the information comes from the viewport object.
 */
@Mixin(value = OcclusionCuller.class, remap = false)
public class MixinSodiumOcclusionCuller_IP {
    @ModifyVariable(method = "findVisible", at = @At("HEAD"), argsOnly = true)
    private boolean modifyUseOcclusionCulling(boolean original, @Local(argsOnly = true) Viewport viewport) {
        if (((IESodiumViewport_IP) (Object) viewport).ip_getCullOrigin() != null) {
            return false;
        }
        return original;
    }

    @WrapOperation(
        method = "findVisible",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;getChunkCoord()Lnet/minecraft/core/SectionPos;"
        )
    )
    private SectionPos wrapGetOrigin(Viewport viewport, Operation<SectionPos> original) {
        SectionPos origin = ((IESodiumViewport_IP) (Object) viewport).ip_getCullOrigin();
        return origin != null ? origin : original.call(viewport);
    }
}
