package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import qouteall.q_misc_util.Helper;

/**
 * Sodium integration entry point (only class-loaded when Sodium is present).
 */
@Environment(EnvType.CLIENT)
public class SodiumCompat {
    public static void init() {
        SodiumInterface.invoker = new SodiumInterface.Invoker() {
            @Override
            public boolean isSodiumPresent() {
                return true;
            }
        };
        Helper.log("Sodium integration initialized");
    }
}
