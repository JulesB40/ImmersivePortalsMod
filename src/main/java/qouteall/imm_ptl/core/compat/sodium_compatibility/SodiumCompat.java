package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.caffeinemc.mods.sodium.client.util.GameRendererStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import qouteall.q_misc_util.Helper;

/**
 * Sodium integration entry point (only class-loaded when Sodium is present).
 */
@Environment(EnvType.CLIENT)
public class SodiumCompat {
    public static void init() {
        SodiumInterface.invoker = new SodiumInterface.Invoker() {
            @Override
            public boolean isSodiumPresent() {
                return true;
            }
            
            /**
             * Sodium tracks the loaded chunks with mixins into the vanilla client chunk cache,
             * which ImmPtl replaces. Notify its chunk tracker directly.
             */
            @Override
            public void onClientChunkLoaded(ClientLevel world, int chunkX, int chunkZ) {
                ChunkTrackerHolder.get(world).onChunkStatusAdded(chunkX, chunkZ, ChunkStatus.FLAG_HAS_BLOCK_DATA);
            }
            
            /**
             * Sodium captures the projection matrix in GameRenderer.renderLevel and uses it for terrain.
             * Portal views have their own projections (oblique near plane, mirror flipping).
             * The getter returns the stored matrix object itself.
             */
            @Override
            public Object overrideProjection(Matrix4fc projection) {
                Matrix4f stored = (Matrix4f) ((GameRendererStorage) Minecraft.getInstance().gameRenderer)
                    .sodium$getProjectionMatrix();
                Matrix4f saved = new Matrix4f(stored);
                stored.set(projection);
                return saved;
            }

            @Override
            public void restoreProjection(Object state) {
                if (state instanceof Matrix4f saved) {
                    Matrix4f stored = (Matrix4f) ((GameRendererStorage) Minecraft.getInstance().gameRenderer)
                        .sodium$getProjectionMatrix();
                    stored.set(saved);
                }
            }

            @Override
            public void onSecondaryRendererCreated(net.minecraft.client.renderer.LevelRenderer renderer) {
                SodiumWorldRenderer sodiumRenderer = ((LevelRendererExtension) renderer).sodium$getWorldRenderer();
                ((IESodiumWorldRenderer_IP) sodiumRenderer).ip_markSecondary();
            }

            @Override
            public void onClientChunkUnloaded(ClientLevel world, int chunkX, int chunkZ) {
                ChunkTrackerHolder.get(world).onChunkStatusRemoved(chunkX, chunkZ, ChunkStatus.FLAG_ALL);
            }
        };
        Helper.log("Sodium integration initialized");
    }
}
