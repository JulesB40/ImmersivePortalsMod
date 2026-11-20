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
    }
    
    public static Invoker invoker = new Invoker();
}
