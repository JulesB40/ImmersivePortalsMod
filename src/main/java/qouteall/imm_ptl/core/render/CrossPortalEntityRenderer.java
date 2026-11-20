package qouteall.imm_ptl.core.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Renders the entities that are going through portals, on the other side of the portal.
 * <p>
 * When an entity is halfway through a portal, the part that has gone through should be visible
 * on the other side. For a view of world W, every entity that is colliding with a portal
 * leading into W gets an extra "projected" render state, transformed through that portal.
 * <p>
 * The parts that should not be visible are hidden by depth:
 * the portal surface is opaque and depth-tested, so it covers the part of the original entity
 * that went behind it, and in a portal view the oblique near plane cuts away the part of the
 * projection that has not gone through yet.
 */
@Environment(EnvType.CLIENT)
public class CrossPortalEntityRenderer {
    private static final Minecraft client = Minecraft.getInstance();

    /**
     * Extra transformation (rotation and scale, around the entity position)
     * applied to a projected entity render state when submitting.
     */
    public static final RenderStateDataKey<Matrix4f> PROJECTION_TRANSFORM =
        RenderStateDataKey.create(() -> "imm_ptl:cross_portal_projection_transform");

    // the entities that are colliding with portals (weak keys, values unused)
    private static final WeakHashMap<Entity, Object> collidedEntities = new WeakHashMap<>();

    public static boolean isRenderingEntityNormally = false;

    public static boolean isRenderingEntityProjection = false;

    public static void init() {
        IPGlobal.POST_CLIENT_TICK_EVENT.register(CrossPortalEntityRenderer::onClientTick);

        IPCGlobal.CLIENT_CLEANUP_EVENT.register(CrossPortalEntityRenderer::cleanUp);

        ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanUp());
    }

    private static void cleanUp() {
        collidedEntities.clear();
    }

    private static void onClientTick() {
        collidedEntities.entrySet().removeIf(entry -> {
            Entity entity = entry.getKey();
            return entity.isRemoved() || !((IEEntity) entity).ip_isCollidingWithPortal();
        });
    }

    public static void onEntityTickClient(Entity entity) {
        if (entity instanceof Portal) {
            return;
        }

        if (((IEEntity) entity).ip_isCollidingWithPortal()) {
            collidedEntities.put(entity, null);
        }
    }

    /**
     * Called after the vanilla entity extraction of a view of the given world.
     */
    public static void addProjectedEntities(
        ClientLevel viewWorld,
        EntityRenderDispatcher dispatcher,
        Vec3 cameraPos,
        float partialTicks,
        LevelRenderState output
    ) {
        if (!IPGlobal.correctCrossPortalEntityRendering) {
            return;
        }
        if (collidedEntities.isEmpty()) {
            return;
        }

        List<Entity> entities = new ArrayList<>(collidedEntities.keySet());
        for (Entity entity : entities) {
            if (entity.isRemoved()) {
                continue;
            }
            Portal portal = ((IEEntity) entity).ip_getCollidingPortal();
            if (portal == null || portal instanceof Mirror) {
                continue;
            }
            if (portal.getDestDim() != viewWorld.dimension()) {
                continue;
            }
            if (portal.level() != entity.level()) {
                continue;
            }
            if (!portal.isVisible()) {
                continue;
            }

            EntityRenderState state = dispatcher.extractEntity(entity, partialTicks);
            Vec3 originalPos = new Vec3(state.x, state.y, state.z);
            Vec3 projectedPos = portal.transformPoint(originalPos);
            state.x = projectedPos.x;
            state.y = projectedPos.y;
            state.z = projectedPos.z;
            state.distanceToCameraSq = projectedPos.distanceToSqr(cameraPos);

            Matrix4f transform = getProjectionTransform(portal);
            if (transform != null) {
                state.setData(PROJECTION_TRANSFORM, transform);
            }

            output.entityRenderStates.add(state);
        }
    }

    private static @Nullable Matrix4f getProjectionTransform(Portal portal) {
        Matrix4f result = null;
        if (portal.getRotation() != null) {
            Quaternionf rot = portal.getRotation().toMcQuaternion();
            result = new Matrix4f().rotation(rot);
        }
        if (portal.hasScaling()) {
            float s = (float) portal.getScale();
            if (result == null) {
                result = new Matrix4f();
            }
            result.scale(s, s, s);
        }
        return result;
    }

    public static boolean hasIntersection(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        return false;
    }
}
