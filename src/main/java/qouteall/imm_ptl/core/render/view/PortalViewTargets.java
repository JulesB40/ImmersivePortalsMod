package qouteall.imm_ptl.core.render.view;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * A pool of off-screen render targets that portal views are rendered into.
 * <p>
 * Each slot is also registered into the texture manager, so that a {@link net.minecraft.client.renderer.rendertype.RenderType}
 * can sample it when drawing the portal surface.
 */
@Environment(EnvType.CLIENT)
public final class PortalViewTargets {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final class Slot {
        public final int index;
        public final Identifier textureId;
        public final SlotTexture texture;
        @Nullable
        private RenderTarget target;

        private Slot(int index) {
            this.index = index;
            this.textureId = Identifier.fromNamespaceAndPath("immersive_portals", "portal_view_" + index);
            this.texture = new SlotTexture(this);
        }

        public RenderTarget getOrCreateTarget(int width, int height) {
            if (target == null) {
                target = new TextureTarget(
                    "ImmPtl portal view " + index, width, height,
                    GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT
                );
            }
            else if (target.width != width || target.height != height) {
                target.resize(width, height);
            }
            return target;
        }

        public @Nullable RenderTarget getTarget() {
            return target;
        }

        private void destroy() {
            if (target != null) {
                target.destroyBuffers();
                target = null;
            }
        }
    }

    /**
     * A texture whose content is the color attachment of a portal view slot.
     * The texture manager does not own the GPU texture.
     */
    public static final class SlotTexture extends AbstractTexture {
        private final Slot slot;

        private SlotTexture(Slot slot) {
            this.slot = slot;
        }

        @Override
        public GpuTexture getTexture() {
            RenderTarget t = slot.getTarget();
            if (t == null || t.getColorTexture() == null) {
                throw new IllegalStateException("Portal view slot " + slot.index + " is not rendered");
            }
            return t.getColorTexture();
        }

        @Override
        public GpuTextureView getTextureView() {
            RenderTarget t = slot.getTarget();
            if (t == null || t.getColorTextureView() == null) {
                throw new IllegalStateException("Portal view slot " + slot.index + " is not rendered");
            }
            return t.getColorTextureView();
        }

        @Override
        public GpuSampler getSampler() {
            return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }

        @Override
        public void close() {
            // the render target is owned by the slot
        }
    }

    private static final List<Slot> SLOTS = new ArrayList<>();
    private static int usedSlotNum = 0;

    public static Slot acquire() {
        if (usedSlotNum >= SLOTS.size()) {
            Slot slot = new Slot(SLOTS.size());
            SLOTS.add(slot);
            Minecraft.getInstance().getTextureManager().register(slot.textureId, slot.texture);
        }
        Slot slot = SLOTS.get(usedSlotNum);
        usedSlotNum++;
        return slot;
    }

    public static Slot get(int index) {
        return SLOTS.get(index);
    }

    public static void onFrameBegin() {
        usedSlotNum = 0;
    }

    public static int getUsedSlotNum() {
        return usedSlotNum;
    }

    /**
     * Release the GPU memory of the slots that were not used recently.
     */
    public static void trimUnused() {
        for (int i = usedSlotNum; i < SLOTS.size(); i++) {
            SLOTS.get(i).destroy();
        }
    }

    public static void destroyAll() {
        for (Slot slot : SLOTS) {
            slot.destroy();
        }
        usedSlotNum = 0;
    }
}
