package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

@Mixin(GameRenderer.class)
public class MixinGameRenderer_Frame {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private boolean ip_isRenderingHand = false;

    /**
     * Before the camera is updated and the frame is extracted.
     * The client-side teleportation must happen before that, so that the camera is on the correct side.
     */
    @Inject(method = "update", at = @At("HEAD"))
    private void onBeforeFrameUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        Profiler.get().push("ip_pre_total_render");
        IPGlobal.PRE_TOTAL_RENDER_TASK_LIST.processTasks();
        Profiler.get().pop();

        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Profiler.get().push("ip_pre_render");
        // Note do not use delta tick. use partial tick.
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        RenderStates.updatePreRenderInfo(partialTick);
        StableClientTimer.update(minecraft.level.getGameTime(), partialTick);
        ClientPortalAnimationManagement.update(); // must update before teleportation
        ClientTeleportationManager.manageTeleportation(false);
        IPGlobal.PRE_GAME_RENDER_EVENT.invoker().run();
        Profiler.get().pop();

        RenderStates.frameIndex++;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onAfterFrame(CallbackInfo ci) {
        if (minecraft.level != null) {
            RenderStates.onTotalRenderEnd();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void onRenderHandBegin(CallbackInfo ci) {
        ip_isRenderingHand = true;
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void onRenderHandEnd(CallbackInfo ci) {
        ip_isRenderingHand = false;
    }

    // reduce view bobbing when near a portal (the view bobbing makes the portal seams visible)
    @ModifyArg(
        method = "bobView",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
        index = 0
    )
    private float modifyBobViewTranslateX(float f) {
        return ip_isRenderingHand ? f : (float) (f * RenderStates.getViewBobbingOffsetMultiplier());
    }

    @ModifyArg(
        method = "bobView",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
        index = 1
    )
    private float modifyBobViewTranslateY(float f) {
        return ip_isRenderingHand ? f : (float) (f * RenderStates.getViewBobbingOffsetMultiplier());
    }
}
