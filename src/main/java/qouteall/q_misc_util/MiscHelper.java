package qouteall.q_misc_util;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.q_misc_util.ducks.IEMinecraftServer_Misc;
import qouteall.q_misc_util.mixin.IELevelStorageAccess_Misc;

import java.lang.reflect.Type;
import java.nio.file.Path;

public class MiscHelper {
    /**
     * Replacement for the removed {@code CommandSourceStack.hasPermission(int)}.
     */
    public static boolean hasPermissionLevel(net.minecraft.commands.CommandSourceStack source, int level) {
        return hasPermissionLevel(source.permissions(), level);
    }
    
    public static boolean hasPermissionLevel(net.minecraft.server.permissions.PermissionSet permissions, int level) {
        net.minecraft.server.permissions.Permission permission = switch (level) {
            case 0 -> null;
            case 1 -> net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR;
            case 2 -> net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER;
            case 3 -> net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN;
            default -> net.minecraft.server.permissions.Permissions.COMMANDS_OWNER;
        };
        return permission == null || permissions.hasPermission(permission);
    }
    
    /**
     * Replacement for the removed {@code Direction.fromDelta}.
     */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.core.Direction directionFromDelta(int x, int y, int z) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            net.minecraft.core.Vec3i v = direction.getUnitVec3i();
            if (v.getX() == x && v.getY() == y && v.getZ() == z) {
                return direction;
            }
        }
        return null;
    }

    
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static final Gson gson;
    
    private static class DimensionIDJsonAdapter
        implements JsonSerializer<ResourceKey<Level>>, JsonDeserializer<ResourceKey<Level>> {
        
        @Override
        public ResourceKey<Level> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            String str = json.getAsString();
            return Helper.dimIdToKey(str);
        }
        
        @Override
        public JsonElement serialize(ResourceKey<Level> src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.identifier().toString());
        }
    }
    
    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        
        gsonBuilder.registerTypeAdapter(
            new TypeToken<ResourceKey<Level>>() {}.getType(),
            new DimensionIDJsonAdapter()
        );
        
        gson = gsonBuilder.create();
    }
    
    /**
     * {@link ReentrantBlockableEventLoop#scheduleExecutables()}
     * The execution may get deferred on the render thread
     */
    @Environment(EnvType.CLIENT)
    public static void executeOnRenderThread(Runnable runnable) {
        Minecraft client = Minecraft.getInstance();
        
        if (client.isSameThread()) {
            try {
                runnable.run();
            }
            catch (Exception e) {
                LOGGER.error("Processing task on render thread", e);
            }
        }
        else {
            client.execute(runnable);
        }
    }
    
    /**
     * TODO support multi-server-in-one-JVM
     */
    @Deprecated
    public static MinecraftServer getServer() {
        return MiscGlobals.refMinecraftServer.get();
    }
    
    public static void executeOnServerThread(MinecraftServer server, Runnable runnable) {
        if (server.isSameThread()) {
            try {
                runnable.run();
            }
            catch (Exception e) {
                LOGGER.error("Processing task on server thread", e);
            }
        }
        else {
            server.execute(runnable);
        }
    }
    
    public static boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }
    
    
    public static Path getWorldSavingDirectory(MinecraftServer server) {
        Validate.notNull(server);
        Path saveDir =
            ((IELevelStorageAccess_Misc) ((IEMinecraftServer_Misc) server).ip_getStorageSource())
                .ip_getLevelPath().path();
        return saveDir;
    }
}
