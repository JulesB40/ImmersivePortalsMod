package qouteall.imm_ptl.core.compat.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.sodium_compatibility.IESodiumViewport_IP;
import qouteall.imm_ptl.core.render.view.PortalViewRenderer;

@Mixin(value = Viewport.class, remap = false)
public class MixinSodiumViewport_IP implements IESodiumViewport_IP {
    @Unique
    private @Nullable SectionPos ip_cullOrigin;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        ip_cullOrigin = PortalViewRenderer.getCullOriginOfExtractingView();
    }

    @Override
    public @Nullable SectionPos ip_getCullOrigin() {
        return ip_cullOrigin;
    }
}
