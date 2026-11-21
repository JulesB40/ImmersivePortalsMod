package qouteall.imm_ptl.core.render;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.miscellaneous.ClientPerformanceMonitor;
import qouteall.imm_ptl.core.mixin.client.render.IELevelRenderer;
import qouteall.imm_ptl.core.mixin.client.render.IEViewArea;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.chunk_loading.PerformanceLevel;
import qouteall.q_misc_util.my_util.Plane;

/**
 * Discover visible sections by breadth-first traverse, for portal rendering.
 * <p>
 * The vanilla occlusion graph is built asynchronously for the player camera,
 * which is not suitable for portal views (their cameras are very dynamic).
 * <p>
 * The traverse starts from the portal's destination surface (not from the camera),
 * because with the oblique near plane the sections between the camera and the portal are culled.
 */
@Environment(EnvType.CLIENT)
public class VisibleSectionDiscovery {

    private static final LongOpenHashSet visited = new LongOpenHashSet();
    private static final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

    public static void discoverVisibleSections(
        ClientLevel world,
        LevelRenderer levelRenderer,
        Vec3 cameraPos,
        Frustum frustum,
        int renderDistance,
        @Nullable Portal portal,
        @Nullable Vec3 extraStart,
        ObjectArrayList<RenderSection> result
    ) {
        result.clear();
        visited.clear();
        queue.clear();

        ViewArea viewArea = ((IELevelRenderer) levelRenderer).ip_getViewArea();
        if (viewArea == null) {
            return;
        }
        IEViewArea ieViewArea = (IEViewArea) viewArea;

        int viewDistance = PerformanceLevel.getPortalRenderingDistance(
            ClientPerformanceMonitor.level, renderDistance
        );
        SectionPos cameraSection = SectionPos.of(cameraPos);
        int minSectionY = world.getMinSectionY();
        int maxSectionY = world.getMaxSectionY();

        // starting points
        if (extraStart != null) {
            addStart(SectionPos.of(extraStart), ieViewArea, result);
        }
        if (portal != null) {
            Vec3 destPos = portal.getDestPos();
            addStart(SectionPos.of(destPos), ieViewArea, result);

            Plane innerClipping = portal.getInnerClipping();
            if (innerClipping != null) {
                // the point on the destination plane that's closest to the camera
                Vec3 n = innerClipping.normal().normalize();
                double dist = cameraPos.subtract(innerClipping.pos()).dot(n);
                Vec3 projected = cameraPos.subtract(n.scale(dist));
                // slightly behind the plane (inside the visible region)
                Vec3 inside = projected.add(n.scale(0.5));
                addStart(SectionPos.of(inside), ieViewArea, result);
            }
        }
        else {
            addStart(cameraSection, ieViewArea, result);
        }

        // breadth-first searching
        while (!queue.isEmpty()) {
            long node = queue.dequeueLong();
            int cx = SectionPos.x(node);
            int cy = SectionPos.y(node);
            int cz = SectionPos.z(node);

            checkSection(cx + 1, cy, cz, ieViewArea, frustum, cameraSection, viewDistance, minSectionY, maxSectionY, result);
            checkSection(cx - 1, cy, cz, ieViewArea, frustum, cameraSection, viewDistance, minSectionY, maxSectionY, result);
            checkSection(cx, cy + 1, cz, ieViewArea, frustum, cameraSection, viewDistance, minSectionY, maxSectionY, result);
            checkSection(cx, cy - 1, cz, ieViewArea, frustum, cameraSection, viewDistance, minSectionY, maxSectionY, result);
            checkSection(cx, cy, cz + 1, ieViewArea, frustum, cameraSection, viewDistance, minSectionY, maxSectionY, result);
            checkSection(cx, cy, cz - 1, ieViewArea, frustum, cameraSection, viewDistance, minSectionY, maxSectionY, result);
        }

        visited.clear();
    }

    private static void addStart(SectionPos pos, IEViewArea viewArea, ObjectArrayList<RenderSection> result) {
        long node = pos.asLong();
        if (!visited.add(node)) {
            return;
        }
        RenderSection section = viewArea.ip_getRenderSection(node);
        if (section != null) {
            result.add(section);
        }
        // continue searching even if the start section is out of the stored area
        queue.enqueue(node);
    }

    private static void checkSection(
        int cx, int cy, int cz,
        IEViewArea viewArea, Frustum frustum,
        SectionPos cameraSection, int viewDistance,
        int minSectionY, int maxSectionY,
        ObjectArrayList<RenderSection> result
    ) {
        if (cy < minSectionY || cy > maxSectionY) {
            return;
        }
        if (Math.abs(cx - cameraSection.x()) > viewDistance) {
            return;
        }
        if (Math.abs(cz - cameraSection.z()) > viewDistance) {
            return;
        }

        long node = SectionPos.asLong(cx, cy, cz);
        if (!visited.add(node)) {
            return;
        }

        RenderSection section = viewArea.ip_getRenderSection(node);
        if (section == null) {
            return;
        }
        if (!frustum.isVisible(section.getBoundingBox())) {
            return;
        }

        result.add(section);
        queue.enqueue(node);
    }
}
