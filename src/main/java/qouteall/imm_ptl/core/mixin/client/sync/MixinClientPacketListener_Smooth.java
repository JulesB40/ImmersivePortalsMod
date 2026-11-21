package qouteall.imm_ptl.core.mixin.client.sync;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.teleportation.PortalCrossingSmoother;

/**
 * Hooks for {@link PortalCrossingSmoother}.
 */
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener_Smooth {
    @Shadow
    private ClientLevel level;
    
    @Inject(
        method = "handleEntityPositionSync",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        )
    )
    private void onHandleEntityPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        Entity entity = level.getEntity(packet.id());
        if (entity != null && !entity.isLocalInstanceAuthoritative()) {
            PortalCrossingSmoother.beforeServerPositionUpdate(entity, packet.position().endPosition());
        }
    }
    
    @Inject(
        method = "handleTeleportEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        )
    )
    private void onHandleTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        Entity entity = level.getEntity(packet.id());
        if (entity != null && !entity.isLocalInstanceAuthoritative()) {
            PositionMoveRotation newValues = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(entity), packet.change(), packet.relatives()
            );
            PortalCrossingSmoother.beforeServerPositionUpdate(entity, newValues.position());
        }
    }
}
