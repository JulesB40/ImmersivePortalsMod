package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Indirection layer for Sodium integration.
 * The default invoker is used when Sodium is absent.
 */
@Environment(EnvType.CLIENT)
public class SodiumInterface {
    
    public static class Invoker {
        public boolean isSodiumPresent() {
            return false;
        }
        
        public void onClientChunkLoaded(ClientLevel world, int chunkX, int chunkZ) {
        
        }
        
        public void onClientChunkUnloaded(ClientLevel world, int chunkX, int chunkZ) {

        }

        /**
         * Called when a renderer is created for a level that already has a renderer.
         */
        /**
         * Make Sodium's terrain rendering use the given projection.
         * @return the state to pass to {@link #restoreProjection}
         */
        public @org.jetbrains.annotations.Nullable Object overrideProjection(org.joml.Matrix4fc projection) {
            return null;
        }

        public void restoreProjection(@org.jetbrains.annotations.Nullable Object state) {
        }

        public void onSecondaryRendererCreated(net.minecraft.client.renderer.LevelRenderer renderer) {

        }
    }
    
    public static Invoker invoker = new Invoker();
}
