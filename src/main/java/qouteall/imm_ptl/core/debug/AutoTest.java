package qouteall.imm_ptl.core.debug;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.portal.Portal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only automated in-game test.
 * Enabled by the JVM property {@code -Dimm_ptl.autotest=<scenario>}.
 * It creates a fresh world, runs a scenario of commands, takes screenshots into
 * {@code run/imm_ptl_autotest/} and quits the game.
 * The results are written into {@code run/imm_ptl_autotest/result.txt}.
 */
@Environment(EnvType.CLIENT)
public class AutoTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    private interface Step {
        /**
         * @return true when the step is done
         */
        boolean run(Minecraft mc, int ticksInStep);
    }

    private static final List<Step> steps = new ArrayList<>();
    private static int stepIndex = 0;
    private static int ticksInStep = 0;
    private static boolean worldCreated = false;
    private static final List<String> results = new ArrayList<>();
    private static Path outDir;

    public static void init() {
        String scenario = System.getProperty("imm_ptl.autotest");
        if (scenario == null) {
            return;
        }
        LOGGER.info("[ImmPtl AutoTest] enabled, scenario {}", scenario);
        outDir = Path.of("imm_ptl_autotest");
        try {
            Files.createDirectories(outDir);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        // skip the first-launch screens
        qouteall.imm_ptl.core.platform_specific.IPConfig config = qouteall.imm_ptl.core.platform_specific.IPConfig.getConfig();
        config.initialScreenShown = true;
        buildScenario(scenario);
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void cmd(String command) {
        steps.add((mc, t) -> {
            LOGGER.info("[ImmPtl AutoTest] command: {}", command);
            mc.player.connection.sendCommand(command);
            return true;
        });
    }

    private static void waitTicks(int ticks) {
        steps.add((mc, t) -> t >= ticks);
    }

    private static void look(float yRot, float xRot) {
        steps.add((mc, t) -> {
            mc.player.setYRot(yRot);
            mc.player.setXRot(xRot);
            mc.player.yRotO = yRot;
            mc.player.xRotO = xRot;
            return true;
        });
    }

    private static void screenshot(String name) {
        steps.add((mc, t) -> {
            if (t == 0) {
                // let the frame settle
                return false;
            }
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
                try {
                    File file = outDir.resolve(name + ".png").toFile();
                    image.writeToFile(file);
                    LOGGER.info("[ImmPtl AutoTest] screenshot {}", file.getAbsolutePath());
                }
                catch (IOException e) {
                    LOGGER.error("screenshot failed", e);
                }
                finally {
                    image.close();
                }
            });
            return true;
        });
    }

    private static void report(String label) {
        steps.add((mc, t) -> {
            int portalNum = 0;
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof Portal) {
                    portalNum++;
                }
            }
            String line = "%s: dim=%s pos=%s portals=%d fps=%d portalCpuMs=%.2f".formatted(
                label, mc.level.dimension().identifier(), mc.player.position(), portalNum, mc.getFps(),
                qouteall.imm_ptl.core.render.view.PortalViewRenderer.takeAveragePortalMillis()
            );
            results.add(line);
            LOGGER.info("[ImmPtl AutoTest] {}", line);
            return true;
        });
    }

    private static void waitForWorldReady() {
        // the loading screen closes when the player section is compiled
        steps.add((mc, t) -> mc.gui.screen() == null || t > 20 * 40);
        steps.add((mc, t) -> {
            if (t == 0) {
                results.add("world ready after loading screen: screen=" + mc.gui.screen());
            }
            return true;
        });
    }
    
    /**
     * Spawn a portal on the integrated server.
     */
    private static void spawnPortal(
        String label, boolean mirror,
        net.minecraft.world.phys.Vec3 origin,
        net.minecraft.world.phys.Vec3 axisW, net.minecraft.world.phys.Vec3 axisH,
        double width, double height,
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> destDim,
        net.minecraft.world.phys.Vec3 dest
    ) {
        steps.add((mc, t) -> {
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var world = server.overworld();
                Portal portal = mirror ?
                    qouteall.imm_ptl.core.portal.Mirror.ENTITY_TYPE.create(world, net.minecraft.world.entity.EntitySpawnReason.LOAD) :
                    Portal.ENTITY_TYPE.create(world, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                portal.setOriginPos(origin);
                portal.setOrientationAndSize(axisW, axisH, width, height);
                portal.setDestinationDimension(mirror ? world.dimension() : destDim);
                portal.setDestination(mirror ? origin : dest);
                qouteall.imm_ptl.core.McHelper.spawnServerEntity(portal);
                LOGGER.info("[ImmPtl AutoTest] spawned portal {} {}", label, portal);
            });
            return true;
        });
    }
    
    private static void removePortals() {
        steps.add((mc, t) -> {
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                for (var e : server.overworld().getAllEntities()) {
                    if (e instanceof Portal portal) {
                        portal.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                    }
                }
            });
            return true;
        });
    }
    
    private static void buildScenario(String scenario) {
        waitForWorldReady();
        waitTicks(40);
        steps.add((mc, t) -> {
            // clean screenshots
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            // measure the real frame rate: no AFK limit, no vsync, no frame limit
            mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            return true;
        });
        cmd("time set noon");
        cmd("weather clear");
        cmd("gamerule advance_time false");
        cmd("gamemode creative");
        // a platform in the air, so that the scene does not depend on the terrain
        // (the world spawn is not at 0 0, so load the area first)
        cmd("forceload add -16 -16 16 16");
        cmd("tp @s 0.5 120 0.5 0 0");
        waitTicks(60);
        cmd("fill -8 99 -8 8 99 16 minecraft:stone");
        cmd("fill -8 100 -8 8 110 16 minecraft:air");
        cmd("fill 6 100 6 6 103 6 minecraft:gold_block");
        cmd("tp @s 0.5 100 0.5 0 0");
        look(0, 0);
        waitTicks(60);
        screenshot("00_no_portal");
        report("before portal");
        
        var W = new net.minecraft.world.phys.Vec3(-1, 0, 0);
        var H = new net.minecraft.world.phys.Vec3(0, 1, 0);
        
        // a known scene on the other side
        cmd("execute in minecraft:the_nether run forceload add -16 -16 16 16");
        waitTicks(40);
        cmd("execute in minecraft:the_nether run fill -6 68 -6 6 68 12 minecraft:gold_block");
        cmd("execute in minecraft:the_nether run fill -6 69 -6 6 76 12 minecraft:air");
        cmd("execute in minecraft:the_nether run fill -2 69 8 2 73 8 minecraft:glowstone");
        cmd("forceload add 584 584 616 616");
        waitTicks(40);
        cmd("fill 594 129 594 606 129 612 minecraft:diamond_block");
        cmd("fill 594 130 594 606 136 612 minecraft:air");
        cmd("fill 598 130 608 602 134 608 minecraft:emerald_block");
        waitTicks(20);
        
        // portal to the nether (cross dimension), 5 blocks in front of the player
        spawnPortal(
            "nether", false, new net.minecraft.world.phys.Vec3(0.5, 102, 5.5), W, H, 3, 4,
            net.minecraft.world.level.Level.NETHER, new net.minecraft.world.phys.Vec3(0.5, 71, 0.5)
        );
        waitTicks(200);
        look(0, 0);
        waitTicks(10);
        screenshot("01_nether_portal");
        report("nether portal");
        
        // from an angle
        cmd("tp @s 4.5 100 1.5 30 0");
        look(30, 0);
        waitTicks(40);
        screenshot("02_nether_portal_angle");
        
        // walk through the nether portal
        cmd("tp @s 0.5 100 0.5 0 0");
        look(0, 0);
        waitTicks(20);
        steps.add((mc, t) -> {
            mc.options.keyUp.setDown(t < 50);
            return t >= 60;
        });
        waitTicks(40);
        screenshot("03_after_walking_through");
        report("after walking");
        
        steps.add((mc, t) -> {
            if (mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                mc.player.connection.sendCommand("execute in minecraft:overworld run tp @s 0.5 100 0.5 0 0");
            }
            return t > 60;
        });
        removePortals();
        waitTicks(20);
        
        // same dimension portal far away (the view uses a separate camera-centered storage)
        spawnPortal(
            "far", false, new net.minecraft.world.phys.Vec3(0.5, 102, 5.5), W, H, 3, 4,
            net.minecraft.world.level.Level.OVERWORLD, new net.minecraft.world.phys.Vec3(600.5, 132, 600.5)
        );
        cmd("tp @s 0.5 100 0.5 0 0");
        look(0, 0);
        waitTicks(200);
        screenshot("04_overworld_far_portal");
        report("same dim far portal");
        removePortals();
        
        // reference for the mirror: looking north
        cmd("tp @s 0.5 100 0.5 180 0");
        look(180, 0);
        waitTicks(40);
        screenshot("05_reference_north");
        
        // mirror
        spawnPortal(
            "mirror", true, new net.minecraft.world.phys.Vec3(0.5, 102, 5.5), W, H, 3, 4,
            net.minecraft.world.level.Level.OVERWORLD, null
        );
        cmd("tp @s 0.5 100 0.5 0 0");
        look(0, 0);
        waitTicks(100);
        report("mirror warmup");
        waitTicks(60);
        screenshot("06_mirror");
        report("mirror");

        
        steps.add((mc, t) -> {
            try {
                Files.write(outDir.resolve("result.txt"), results);
            }
            catch (IOException e) {
                LOGGER.error("", e);
            }
            LOGGER.info("[ImmPtl AutoTest] finished");
            mc.stop();
            return true;
        });
    }
    
    private static void tick(Minecraft mc) {
        if (!worldCreated) {
            mc.options.onboardAccessibility = false;
            if (mc.gui.screen() != null && !(mc.gui.screen() instanceof TitleScreen)
                && mc.level == null && mc.gui.overlay() == null) {
                // close the first-launch screens
                mc.gui.screen().onClose();
            }
            if (mc.gui.screen() instanceof TitleScreen) {
                worldCreated = true;
                String name = "imm_ptl_autotest_" + System.currentTimeMillis();
                LevelSettings settings = new LevelSettings(
                    name, GameType.CREATIVE,
                    LevelSettings.DifficultySettings.DEFAULT,
                    true, WorldDataConfiguration.DEFAULT
                );
                mc.createWorldOpenFlows().createFreshLevel(
                    name, settings, new WorldOptions(12345L, false, false),
                    WorldPresets::createNormalWorldDimensions, null
                );
            }
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (stepIndex >= steps.size()) {
            return;
        }
        try {
            boolean done = steps.get(stepIndex).run(mc, ticksInStep);
            ticksInStep++;
            if (done) {
                stepIndex++;
                ticksInStep = 0;
            }
        }
        catch (Throwable e) {
            LOGGER.error("[ImmPtl AutoTest] step {} failed", stepIndex, e);
            results.add("step " + stepIndex + " failed: " + e);
            stepIndex++;
            ticksInStep = 0;
        }
    }
}
