package qouteall.imm_ptl.core.mixin.common.collision;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Projectile.class)
public abstract class MixinProjectile extends MixinEntity {
    
    @org.spongepowered.asm.mixin.Shadow
    protected net.minecraft.world.entity.EntityReference<Entity> owner;
    
    // make it recognize the owner in another dimension
    @org.spongepowered.asm.mixin.injection.Inject(method = "getOwner", at = @At("RETURN"), cancellable = true)
    private void onGetOwner(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() != null || owner == null) {
            return;
        }
        Entity this_ = (Entity) (Object) this;
        if (!(this_.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        java.util.UUID uuid = owner.getUUID();
        MinecraftServer server = serverLevel.getServer();
        for (ServerLevel world : server.getAllLevels()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                cir.setReturnValue(entity);
                return;
            }
        }
    }
    
//    @Shadow
//    public abstract void onHit(HitResult hitResult);
//
//    @Inject(method = "Lnet/minecraft/world/entity/projectile/Projectile;onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At(value = "HEAD"), cancellable = true)
//    protected void onHit(HitResult hitResult, CallbackInfo ci) {
//        Entity this_ = (Entity) (Object) this;
//        if (hitResult instanceof BlockHitResult) {
//            Block hittingBlock = this_.level().getBlockState(((BlockHitResult) hitResult).getBlockPos()).getBlock();
//            if (hitResult.getType() == HitResult.Type.BLOCK &&
//                hittingBlock == PortalPlaceholderBlock.instance
//            ) {
//                ci.cancel();
//            }
//        }
//    }
//
    
}