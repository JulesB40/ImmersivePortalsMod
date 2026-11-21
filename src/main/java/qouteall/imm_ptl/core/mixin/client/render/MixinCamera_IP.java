package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IECamera;

@Mixin(Camera.class)
public abstract class MixinCamera_IP implements IECamera {
    @Shadow
    private Level level;
    @Shadow
    private Entity entity;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;
    
    @Shadow
    protected abstract void setPosition(Vec3 position);
    
    @Override
    public void ip_resetState(Vec3 pos, ClientLevel currWorld) {
        setPosition(pos);
        level = currWorld;
    }
    
    @Override
    public void portal_setPos(Vec3 pos) {
        setPosition(pos);
    }
    
    @Override
    public float ip_getCameraY() {
        return eyeHeight;
    }
    
    @Override
    public float ip_getLastCameraY() {
        return eyeHeightOld;
    }
    
    @Override
    public void ip_setCameraY(float cameraY, float lastCameraY) {
        eyeHeight = cameraY;
        eyeHeightOld = lastCameraY;
    }
    
    @Override
    public void portal_setFocusedEntity(Entity arg) {
        entity = arg;
    }
}
