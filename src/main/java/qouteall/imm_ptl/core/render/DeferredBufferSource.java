package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immediate-mode-like buffer source for the debug/wand line rendering code.
 * <p>
 * Since 26.x, geometry is submitted through {@link SubmitNodeCollector} and the vertex consumer is only
 * available inside a callback. This records the vertex operations and replays them on submission.
 * Positions are recorded already transformed, so they are replayed with an identity pose.
 */
@Environment(EnvType.CLIENT)
public final class DeferredBufferSource {
    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

    public VertexConsumer getBuffer(RenderType renderType) {
        return recorders.computeIfAbsent(renderType, k -> new Recorder(false));
    }
    
    /**
     * The line strip render types were removed. The strip is converted to separate line segments.
     */
    public VertexConsumer getLineStripBuffer() {
        return lineStripRecorder;
    }
    
    private final Recorder lineStripRecorder = new Recorder(true);

    public void submitAll(SubmitNodeCollector collector) {
        PoseStack identity = new PoseStack();
        for (Map.Entry<RenderType, Recorder> e : recorders.entrySet()) {
            Recorder recorder = e.getValue();
            if (recorder.ops.isEmpty()) {
                continue;
            }
            collector.submitCustomGeometry(identity, e.getKey(), (pose, buffer) -> recorder.replay(buffer));
        }
        if (!lineStripRecorder.ops.isEmpty()) {
            Recorder strip = lineStripRecorder;
            collector.submitCustomGeometry(
                identity, net.minecraft.client.renderer.rendertype.RenderTypes.lines(),
                (pose, buffer) -> strip.replay(buffer)
            );
        }
        recorders.clear();
    }

    private static final class Recorder implements VertexConsumer {
        private static final float OP_VERTEX = 0, OP_COLOR = 1, OP_UV = 2, OP_UV1 = 3, OP_UV2 = 4,
            OP_UV3 = 5, OP_NORMAL = 6, OP_LINE_WIDTH = 7;

        private final FloatArrayList ops = new FloatArrayList();
        private final boolean isStrip;
        
        Recorder(boolean isStrip) {
            this.isStrip = isStrip;
        }

        private VertexConsumer op(float type, float a, float b, float c, float d) {
            ops.add(type);
            ops.add(a);
            ops.add(b);
            ops.add(c);
            ops.add(d);
            return this;
        }

        void replay(VertexConsumer out) {
            if (isStrip) {
                replayStripAsLines(out);
                return;
            }
            float[] arr = ops.elements();
            int n = ops.size();
            for (int i = 0; i + 4 < n; i += 5) {
                int type = (int) arr[i];
                float a = arr[i + 1], b = arr[i + 2], c = arr[i + 3], d = arr[i + 4];
                switch (type) {
                    case 0 -> out.addVertex(a, b, c);
                    case 1 -> out.setColor((int) a, (int) b, (int) c, (int) d);
                    case 2 -> out.setUv(a, b);
                    case 3 -> out.setUv1((int) a, (int) b);
                    case 4 -> out.setUv2((int) a, (int) b);
                    case 5 -> out.setUv3(a, b);
                    case 6 -> out.setNormal(a, b, c);
                    case 7 -> out.setLineWidth(a);
                    default -> {}
                }
            }
        }

        private void replayStripAsLines(VertexConsumer out) {
            float[] arr = ops.elements();
            int n = ops.size();
            float px = 0, py = 0, pz = 0;
            int pColor = 0xffffffff;
            boolean hasPrev = false;
            int color = 0xffffffff;
            float width = 1;
            float vx = 0, vy = 0, vz = 0;
            boolean pending = false;
            for (int i = 0; i + 4 < n + 5; i += 5) {
                boolean end = i + 4 >= n;
                int type = end ? 0 : (int) arr[i];
                if (end || type == 0) {
                    if (pending) {
                        if (hasPrev) {
                            float dx = vx - px, dy = vy - py, dz = vz - pz;
                            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                            if (len > 1e-6f) {
                                dx /= len;
                                dy /= len;
                                dz /= len;
                            }
                            out.addVertex(px, py, pz).setColor(pColor).setNormal(dx, dy, dz).setLineWidth(width);
                            out.addVertex(vx, vy, vz).setColor(color).setNormal(dx, dy, dz).setLineWidth(width);
                        }
                        px = vx;
                        py = vy;
                        pz = vz;
                        pColor = color;
                        hasPrev = true;
                    }
                    if (end) {
                        break;
                    }
                    vx = arr[i + 1];
                    vy = arr[i + 2];
                    vz = arr[i + 3];
                    pending = true;
                }
                else if (type == 1) {
                    color = ((int) arr[i + 4] << 24) | ((int) arr[i + 1] << 16) | ((int) arr[i + 2] << 8) | (int) arr[i + 3];
                }
                else if (type == 7) {
                    width = arr[i + 1];
                }
            }
        }
        
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return op(OP_VERTEX, x, y, z, 0);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return op(OP_COLOR, r, g, b, a);
        }

        @Override
        public VertexConsumer setColor(int color) {
            return op(OP_COLOR, (color >> 16) & 0xff, (color >> 8) & 0xff, color & 0xff, (color >>> 24) & 0xff);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return op(OP_UV, u, v, 0, 0);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return op(OP_UV1, u, v, 0, 0);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return op(OP_UV2, u, v, 0, 0);
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return op(OP_UV3, u, v, 0, 0);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return op(OP_NORMAL, x, y, z, 0);
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return op(OP_LINE_WIDTH, width, 0, 0, 0);
        }
    }
}
