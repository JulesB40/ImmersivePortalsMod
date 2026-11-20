package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher_CrossPortal {
    /**
     * Rotate/scale the entities that are projected through rotating/scaling portals.
     */
    @Inject(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
            shift = At.Shift.AFTER,
            ordinal = 0
        )
    )
    private void onAfterTranslate(
        CallbackInfo ci,
        @Local(argsOnly = true) EntityRenderState renderState,
        @Local(argsOnly = true) PoseStack poseStack
    ) {
        Matrix4f transform = renderState.getData(CrossPortalEntityRenderer.PROJECTION_TRANSFORM);
        if (transform != null) {
            poseStack.mulPose(transform);
        }
    }
}
