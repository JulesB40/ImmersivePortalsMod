package qouteall.dimlib.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal JSON-backed config (DimLib previously used MidnightLib).
 */
public class DimLibConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static boolean suppressExperimentalWarning = false;
    
    private static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("dimlib.json");
    }
    
    public static void load() {
        Path path = getPath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            JsonObject obj = GSON.fromJson(Files.readString(path), JsonObject.class);
            if (obj != null && obj.has("suppressExperimentalWarning")) {
                suppressExperimentalWarning = obj.get("suppressExperimentalWarning").getAsBoolean();
            }
        }
        catch (Exception e) {
            LOGGER.error("Failed to read DimLib config", e);
        }
    }
    
    public static void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("suppressExperimentalWarning", suppressExperimentalWarning);
            Files.writeString(getPath(), GSON.toJson(obj));
        }
        catch (Exception e) {
            LOGGER.error("Failed to write DimLib config", e);
        }
    }
}
