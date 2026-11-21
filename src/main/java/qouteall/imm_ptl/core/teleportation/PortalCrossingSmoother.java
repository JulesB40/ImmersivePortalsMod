package qouteall.imm_ptl.core.teleportation;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.List;
import java.util.UUID;

/**
 * Makes other entities move smoothly when they go through portals (on the client).
 * <p>
 * The client renders entities slightly in the past (vanilla interpolates toward the latest server position over
 * a few ticks). When an entity goes through a portal, the server position jumps to the other side.
 * Vanilla (and the old ImmPtl) then snapped the entity to the new position, so it visibly jumped forward
 * by the interpolation lag, and its walk animation/rotation restarted on the other side.
 * <p>
 * Instead, the entity's current (rendered) state is transformed through the portal,
 * and the interpolation continues from there toward the new server position.
 * <ul>
 *     <li>Same dimension: the position sync packet jumps across the portal. Detected and handled in
 *     {@link #beforeServerPositionUpdate}.</li>
 *     <li>Other dimension: the entity is removed from one world and re-added in the other world
 *     (as a new entity object). The removed entity's state is remembered and carried over in
 *     {@link #onEntityAdded}.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class PortalCrossingSmoother {
    private PortalCrossingSmoother() {}

    private record RemovedEntityState(
        ResourceKey<Level> dimension,
        Vec3 pos, Vec3 oldPos,
        float yRot, float xRot, float yRotO, float xRotO,
        float yBodyRot, float yBodyRotO, float yHeadRot, float yHeadRotO,
        Vec3 deltaMovement,
        @Nullable Portal portal,
        long time
    ) {}

    private static final Object2ObjectOpenHashMap<UUID, RemovedEntityState> recentlyRemoved =
        new Object2ObjectOpenHashMap<>();

    private static long clientTime = 0;

    public static void tick() {
        clientTime++;
        if (!recentlyRemoved.isEmpty()) {
            recentlyRemoved.values().removeIf(s -> clientTime - s.time > 20);
        }
    }

    // ---- same dimension ----

    /**
     * Called before the client applies a new server position to a non-local entity.
     */
    public static void beforeServerPositionUpdate(Entity entity, Vec3 newServerPos) {
        if (!IPGlobal.allowClientEntityPosInterpolation) {
            return;
        }
        Vec3 reference = getInterpolationTargetOrPos(entity);
        if (reference.distanceToSqr(newServerPos) < 4) {
            return;
        }
        Portal portal = findCrossedPortal(entity.level(), entity, reference, newServerPos);
        if (portal == null) {
            return;
        }
        transformEntityState(entity, portal, entity.level(), entity.level());
    }

    private static Vec3 getInterpolationTargetOrPos(Entity entity) {
        var target = entity.getInterpolation().target();
        if (target != null) {
            return target.position();
        }
        return entity.position();
    }

    /**
     * Find a portal that maps the position on this side to the new position.
     */
    private static @Nullable Portal findCrossedPortal(
        Level world, Entity entity, Vec3 fromPos, Vec3 newPos
    ) {
        Portal colliding = ((IEEntity) entity).ip_getCollidingPortal();
        if (isCrossing(colliding, world, fromPos, newPos)) {
            return colliding;
        }

        double speed = entity.getDeltaMovement().length();
        double range = Math.max(4, speed * 4 + entity.getBbWidth() + entity.getBbHeight());
        List<Portal> portals = McHelper.findEntitiesRough(
            Portal.class, world, fromPos, (int) Math.ceil(range / 16) + 1,
            p -> p.getBoundingBox().inflate(range).contains(fromPos)
        );
        for (Portal portal : portals) {
            if (isCrossing(portal, world, fromPos, newPos)) {
                return portal;
            }
        }
        return null;
    }

    private static boolean isCrossing(@Nullable Portal portal, Level world, Vec3 fromPos, Vec3 newPos) {
        if (portal == null || portal instanceof Mirror || !portal.isTeleportable()) {
            return false;
        }
        if (portal.getDestDim() != world.dimension() && world.dimension() == portal.getOriginDim()) {
            // handled by the cross-dimension path
            return false;
        }
        Vec3 transformed = portal.transformPoint(fromPos);
        double tolerance = 3 * Math.max(1, portal.getScale());
        return transformed.distanceToSqr(newPos) < tolerance * tolerance;
    }

    /**
     * Transform the entity's current position, last tick position and rotations through the portal,
     * so that the rendered position stays continuous and the interpolation goes on from there.
     */
    private static void transformEntityState(Entity entity, Portal portal, Level fromWorld, Level toWorld) {
        Vec3 pos = portal.transformPoint(entity.position());
        Vec3 oldPos = portal.transformPoint(McHelper.lastTickPosOf(entity));

        float[] rot = transformRotation(portal, entity.getYRot(), entity.getXRot());
        float[] rotO = transformRotation(portal, entity.yRotO, entity.xRotO);

        entity.setPos(pos);
        entity.xo = oldPos.x;
        entity.yo = oldPos.y;
        entity.zo = oldPos.z;
        entity.xOld = oldPos.x;
        entity.yOld = oldPos.y;
        entity.zOld = oldPos.z;
        entity.setYRot(rot[0]);
        entity.setXRot(rot[1]);
        entity.yRotO = rotO[0];
        entity.xRotO = rotO[1];
        entity.setDeltaMovement(portal.transformLocalVec(entity.getDeltaMovement()));

        if (entity instanceof LivingEntity living) {
            float bodyDelta = transformRotation(portal, living.yBodyRot, 0)[0] - living.yBodyRot;
            living.yBodyRot += bodyDelta;
            living.yBodyRotO += bodyDelta;
            living.yHeadRot += bodyDelta;
            living.yHeadRotO += bodyDelta;
        }

        McHelper.updateBoundingBox(entity);

        // the interpolation keeps going from the transformed position.
        // don't let the vanilla "delta since last interpolation" logic shift the target by the jump
        entity.getInterpolation().cancel();
    }

    private static float[] transformRotation(Portal portal, float yRot, float xRot) {
        if (portal.getRotation() == null && !(portal instanceof Mirror)) {
            return new float[]{yRot, xRot};
        }
        Vec3 forward = Vec3.directionFromRotation(xRot, yRot);
        Vec3 transformed = portal.transformLocalVecNonScale(forward).normalize();
        float newYRot = (float) Math.toDegrees(Math.atan2(-transformed.x, transformed.z));
        float newXRot = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -transformed.y))));
        // keep the yaw continuous (avoid 360 degree spins in interpolation)
        float diff = newYRot - yRot;
        diff = (float) (diff - 360 * Math.floor((diff + 180) / 360));
        return new float[]{yRot + diff, newXRot};
    }

    // ---- other dimension ----

    /**
     * Called when an entity is removed from a client world.
     */
    public static void onEntityRemoved(ClientLevel world, Entity entity) {
        if (!IPGlobal.allowClientEntityPosInterpolation) {
            return;
        }
        if (entity instanceof Portal || entity == net.minecraft.client.Minecraft.getInstance().player) {
            return;
        }
        Portal portal = ((IEEntity) entity).ip_getCollidingPortal();
        if (portal == null) {
            // the colliding portal may not be ticked yet. search nearby
            AABB box = entity.getBoundingBox().inflate(2);
            List<Portal> portals = McHelper.findEntitiesRough(
                Portal.class, world, entity.position(), 1, p -> p.getBoundingBox().intersects(box)
            );
            portal = portals.stream()
                .filter(p -> p.getDestDim() != world.dimension() && p.isTeleportable())
                .findFirst().orElse(null);
        }
        if (portal == null || portal.getDestDim() == world.dimension()) {
            return;
        }
        float yBodyRot = 0, yBodyRotO = 0, yHeadRot = 0, yHeadRotO = 0;
        if (entity instanceof LivingEntity living) {
            yBodyRot = living.yBodyRot;
            yBodyRotO = living.yBodyRotO;
            yHeadRot = living.yHeadRot;
            yHeadRotO = living.yHeadRotO;
        }
        recentlyRemoved.put(entity.getUUID(), new RemovedEntityState(
            world.dimension(), entity.position(), McHelper.lastTickPosOf(entity),
            entity.getYRot(), entity.getXRot(), entity.yRotO, entity.xRotO,
            yBodyRot, yBodyRotO, yHeadRot, yHeadRotO,
            entity.getDeltaMovement(), portal, clientTime
        ));
        
        // the entity may have been added into the destination world before being removed here
        ClientLevel destWorld = ClientWorldLoader.getOptionalWorld(portal.getDestDim());
        if (destWorld != null) {
            for (Entity added : destWorld.entitiesForRendering()) {
                if (added != entity && added.tickCount < 2 && added.getUUID().equals(entity.getUUID())) {
                    onEntityAdded(destWorld, added);
                    break;
                }
            }
        }
    }

    /**
     * Called when an entity is added into a client world.
     */
    public static void onEntityAdded(ClientLevel world, Entity entity) {
        if (recentlyRemoved.isEmpty()) {
            return;
        }
        RemovedEntityState state = recentlyRemoved.remove(entity.getUUID());
        if (state == null || state.portal == null) {
            return;
        }
        Portal portal = state.portal;
        if (portal.getDestDim() != world.dimension()) {
            return;
        }
        Vec3 serverPos = entity.position();
        Vec3 pos = portal.transformPoint(state.pos);
        double tolerance = 4 * Math.max(1, portal.getScale());
        if (pos.distanceToSqr(serverPos) > tolerance * tolerance) {
            return;
        }
        Vec3 oldPos = portal.transformPoint(state.oldPos);
        float[] rot = transformRotation(portal, state.yRot, state.xRot);
        float[] rotO = transformRotation(portal, state.yRotO, state.xRotO);

        float serverYRot = entity.getYRot();
        float serverXRot = entity.getXRot();

        // continue from where the entity was rendered on the other side
        entity.setPos(pos);
        entity.xo = oldPos.x;
        entity.yo = oldPos.y;
        entity.zo = oldPos.z;
        entity.xOld = oldPos.x;
        entity.yOld = oldPos.y;
        entity.zOld = oldPos.z;
        entity.setYRot(rot[0]);
        entity.setXRot(rot[1]);
        entity.yRotO = rotO[0];
        entity.xRotO = rotO[1];
        entity.setDeltaMovement(portal.transformLocalVec(state.deltaMovement));
        if (entity instanceof LivingEntity living) {
            float bodyDelta = rot[0] - state.yRot;
            living.yBodyRot = state.yBodyRot + bodyDelta;
            living.yBodyRotO = state.yBodyRotO + bodyDelta;
            living.yHeadRot = state.yHeadRot + bodyDelta;
            living.yHeadRotO = state.yHeadRotO + bodyDelta;
        }
        McHelper.updateBoundingBox(entity);

        // and interpolate to the server position
        entity.getInterpolation().cancel();
        entity.moveOrInterpolateTo(serverPos, serverYRot, serverXRot);
    }
}
