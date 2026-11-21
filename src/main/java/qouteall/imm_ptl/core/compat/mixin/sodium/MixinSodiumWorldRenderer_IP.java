package qouteall.imm_ptl.core.compat.mixin.sodium;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.sodium_compatibility.IESodiumWorldRenderer_IP;

/**
 * Sodium's chunk tracker is per level and its load/unload events are consumed by one renderer.
 * ImmPtl can have several renderers of the same level (the player's one, and the one for
 * same-dimension portal views). The secondary renderers compute their own events from the set of ready chunks.
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class MixinSodiumWorldRenderer_IP implements IESodiumWorldRenderer_IP {
    @Shadow
    private ClientLevel level;

    @Unique
    private boolean ip_isSecondary = false;

    @Unique
    private @Nullable LongOpenHashSet ip_knownChunks;

    @Override
    public void ip_markSecondary() {
        ip_isSecondary = true;
        ip_snapshotReadyChunks();
    }

    @Unique
    private void ip_snapshotReadyChunks() {
        if (level == null) {
            ip_knownChunks = new LongOpenHashSet();
            return;
        }
        ip_knownChunks = new LongOpenHashSet(ChunkTrackerHolder.get(level).getReadyChunks());
    }

    /**
     * When (re)initialized, the renderer adds all the ready chunks.
     */
    @Inject(method = "initRenderer", at = @At("RETURN"))
    private void onInitRenderer(CallbackInfo ci) {
        if (ip_isSecondary) {
            ip_snapshotReadyChunks();
        }
    }

    @Redirect(
        method = "processChunkEvents",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/map/ChunkTracker;forEachEvent(Lnet/caffeinemc/mods/sodium/client/render/chunk/map/ChunkTracker$ChunkEventHandler;Lnet/caffeinemc/mods/sodium/client/render/chunk/map/ChunkTracker$ChunkEventHandler;)V"
        )
    )
    private void redirectForEachEvent(
        ChunkTracker tracker, ChunkTracker.ChunkEventHandler loadHandler, ChunkTracker.ChunkEventHandler unloadHandler
    ) {
        if (!ip_isSecondary) {
            tracker.forEachEvent(loadHandler, unloadHandler);
            return;
        }
        if (ip_knownChunks == null) {
            ip_snapshotReadyChunks();
            return;
        }
        LongCollection ready = tracker.getReadyChunks();
        LongIterator it = ip_knownChunks.iterator();
        while (it.hasNext()) {
            long pos = it.nextLong();
            if (!ready.contains(pos)) {
                it.remove();
                unloadHandler.apply(ChunkPos.getX(pos), ChunkPos.getZ(pos));
            }
        }
        LongIterator readyIt = ready.iterator();
        while (readyIt.hasNext()) {
            long pos = readyIt.nextLong();
            if (ip_knownChunks.add(pos)) {
                loadHandler.apply(ChunkPos.getX(pos), ChunkPos.getZ(pos));
            }
        }
    }
}
