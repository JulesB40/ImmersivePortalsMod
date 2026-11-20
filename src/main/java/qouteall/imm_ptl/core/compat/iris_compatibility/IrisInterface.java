package qouteall.imm_ptl.core.compat.iris_compatibility;

import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;

/**
 * Iris integration is not yet ported to 26.x. This stub reports Iris as absent.
 */
public class IrisInterface {
    
    public static class Invoker {
        public boolean isIrisPresent() {
            return false;
        }
        
        public boolean isShaders() {
            return false;
        }
        
        public boolean isRenderingShadowMap() {
            return false;
        }
        
        public Object getPipeline(LevelRenderer worldRenderer) {
            return null;
        }
        
        public void setPipeline(LevelRenderer worldRenderer, Object pipeline) {
        
        }
        
        public void reloadPipelines() {}
        
        @Nullable
        public String getShaderpackName() {
            return null;
        }
    }
    
    public static Invoker invoker = new Invoker();
}
