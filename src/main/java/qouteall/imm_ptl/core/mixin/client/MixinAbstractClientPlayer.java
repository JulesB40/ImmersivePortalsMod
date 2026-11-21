package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IEAbstractClientPlayer;

@Mixin(AbstractClientPlayer.class)
public class MixinAbstractClientPlayer implements IEAbstractClientPlayer {
    // since 26.x the client player has no separate client level reference (Entity.level is used)
    @Override
    public void ip_setClientLevel(ClientLevel clientWorld) {
    }
}
