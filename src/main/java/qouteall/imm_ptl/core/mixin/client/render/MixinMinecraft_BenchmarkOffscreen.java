package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.device.GpuSurface;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Dev-only, for the automated benchmark ({@code -Dimm_ptl.autotest.offscreen=true}):
 * render frames without presenting them, so the measured throughput
 * does not depend on the state of the display or on the desktop compositor.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft_BenchmarkOffscreen {
    @Unique
    private static final boolean IP_OFFSCREEN = Boolean.getBoolean("imm_ptl.autotest.offscreen");

    @WrapOperation(
        method = "renderFrame",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuSurface;acquireNextTexture()V"
        )
    )
    private void wrapAcquireNextTexture(GpuSurface surface, Operation<Void> original) {
        if (IP_OFFSCREEN) {
            return;
        }
        original.call(surface);
    }
}
