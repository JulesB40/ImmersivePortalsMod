package qouteall.imm_ptl.core.render.view;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The camera of a portal view.
 * <p>
 * Unlike the vanilla camera, its orientation is not limited to yaw and pitch
 * (rotating portals can roll the view), so the orientation-related accessors are overridden.
 * <p>
 * It's "detached", so the player entity gets rendered when seen through a portal.
 */
@Environment(EnvType.CLIENT)
public class PortalCamera extends Camera {
    private final Quaternionf orientation = new Quaternionf();
    private final Matrix4f viewRotation = new Matrix4f();
    private final Vector3f forwards = new Vector3f(0, 0, -1);
    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Vector3f left = new Vector3f(-1, 0, 0);
    private Frustum frustum = new Frustum(new Matrix4f(), new Matrix4f());
    private float xRotValue;
    private float yRotValue;

    @Nullable
    private ClientLevel currentLevel;

    public void setup(
        ClientLevel level,
        @Nullable Entity entity,
        Vec3 position,
        Matrix4f viewRotationMatrix,
        Frustum cullFrustum,
        float xRot, float yRot
    ) {
        if (currentLevel != level) {
            currentLevel = level;
            setLevel(level);
        }
        if (entity != null) {
            setEntity(entity);
        }
        setPosition(position);

        this.viewRotation.set(viewRotationMatrix);
        // the view rotation matrix transforms world-relative vectors into view space,
        // the camera orientation is its inverse
        this.viewRotation.getNormalizedRotation(this.orientation);
        this.orientation.conjugate();

        this.orientation.transform(new Vector3f(0, 0, -1), this.forwards);
        this.orientation.transform(new Vector3f(0, 1, 0), this.up);
        this.orientation.transform(new Vector3f(-1, 0, 0), this.left);

        this.frustum = cullFrustum;
        this.xRotValue = xRot;
        this.yRotValue = yRot;
    }

    /**
     * Tick the environment attribute probe (fog color, sky light, etc.) at the current position.
     */
    public void tickProbe() {
        if (currentLevel != null) {
            attributeProbe().tick(currentLevel, position());
        }
    }

    @Override
    public Frustum getCullFrustum() {
        return frustum;
    }

    @Override
    public @Nullable Frustum getCapturedFrustum() {
        return null;
    }

    @Override
    public Quaternionf rotation() {
        return orientation;
    }

    @Override
    public Matrix4f getViewRotationMatrix(Matrix4f dest) {
        return dest.set(viewRotation);
    }

    @Override
    public Vector3fc forwardVector() {
        return forwards;
    }

    @Override
    public Vector3fc upVector() {
        return up;
    }

    @Override
    public Vector3fc leftVector() {
        return left;
    }

    @Override
    public float xRot() {
        return xRotValue;
    }

    @Override
    public float yRot() {
        return yRotValue;
    }

    @Override
    public boolean isDetached() {
        return true;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }
}
