package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;

public interface IESodiumViewport_IP {
    /**
     * For a portal view: the section where the terrain graph search starts.
     * Stored in the viewport because Sodium culls on another thread.
     */
    @Nullable SectionPos ip_getCullOrigin();
}
