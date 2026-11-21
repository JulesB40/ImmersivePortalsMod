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
            takeScreenshotNow(mc, name);
            return true;
        });
    }
    
    private static void takeScreenshotNow(Minecraft mc, String name) {
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try {
                // average brightness of the screen center (where the test portals are)
                int w = image.getWidth();
                int h = image.getHeight();
                double sum = 0;
                int count = 0;
                for (int y = (int) (h * 0.25); y < (int) (h * 0.7); y += 2) {
                    for (int x = (int) (w * 0.42); x < (int) (w * 0.58); x += 2) {
                        int c = image.getPixel(x, y);
                        sum += ((c & 0xFF) + ((c >> 8) & 0xFF) + ((c >> 16) & 0xFF)) / 3.0;
                        count++;
                    }
                }
                String line = "screenshot %s centerBrightness=%.1f".formatted(name, sum / count);
                results.add(line);
                LOGGER.info("[ImmPtl AutoTest] {}", line);
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

        if (scenario.equals("crossing")) {
            buildCrossingScenario();
        }
        else if (scenario.equals("lighting")) {
            buildLightingScenario();
        }
        else {
            buildBasicScenario();
        }

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

    // ---- entity crossing smoothness ----

    private static final java.util.UUID CROSSING_ENTITY_UUID =
        java.util.UUID.fromString("0f0e0d0c-0b0a-4909-8807-060504030201");
    private static boolean crossingRecording = false;
    private static net.minecraft.world.phys.Vec3 crossingBackOffset = net.minecraft.world.phys.Vec3.ZERO;
    private static final List<double[]> crossingSamples = new ArrayList<>();
    private static int crossingMissingFrames = 0;
    private static boolean crossingRecorderRegistered = false;

    /**
     * Records the rendered position of the test entity every frame,
     * mapped back to the portal's origin side, to detect snapping when it goes through the portal.
     */
    private static void recordCrossingFrame() {
        if (!crossingRecording) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        net.minecraft.world.entity.Entity found = null;
        for (net.minecraft.client.multiplayer.ClientLevel world : qouteall.imm_ptl.core.ClientWorldLoader.getClientWorlds()) {
            for (net.minecraft.world.entity.Entity e : world.entitiesForRendering()) {
                if (e.getUUID().equals(CROSSING_ENTITY_UUID)) {
                    found = e;
                    break;
                }
            }
            if (found != null) {
                break;
            }
        }
        if (found == null) {
            if (!crossingSamples.isEmpty()) {
                crossingMissingFrames++;
            }
            return;
        }
        net.minecraft.world.phys.Vec3 pos = found.getPosition(partialTick);
        boolean onDestSide = found.level().dimension() != net.minecraft.world.level.Level.OVERWORLD || pos.z > 300;
        if (onDestSide) {
            pos = pos.subtract(crossingBackOffset);
        }
        crossingSamples.add(new double[]{System.nanoTime() / 1e9, pos.x, pos.y, pos.z, onDestSide ? 1 : 0});
    }

    private static void analyzeCrossing(String label) {
        int n = crossingSamples.size();
        if (n < 10) {
            results.add(label + ": not enough samples " + n);
            return;
        }
        double[] speeds = new double[n - 1];
        double maxJump = 0;
        int maxJumpIndex = 0;
        int crossIndex = -1;
        for (int i = 1; i < n; i++) {
            double[] a = crossingSamples.get(i - 1);
            double[] b = crossingSamples.get(i);
            double d = Math.sqrt(
                (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]) + (b[3] - a[3]) * (b[3] - a[3])
            );
            double dt = Math.max(1e-4, b[0] - a[0]);
            speeds[i - 1] = d / dt;
            if (d > maxJump) {
                maxJump = d;
                maxJumpIndex = i;
            }
            if (crossIndex < 0 && a[4] != b[4]) {
                crossIndex = i;
            }
        }
        double[] sorted = speeds.clone();
        java.util.Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        double p99 = sorted[(int) (sorted.length * 0.99)];
        double max = sorted[sorted.length - 1];
        // the biggest positional jump in a single frame around the portal crossing
        double crossJump = 0;
        if (crossIndex > 0) {
            for (int i = Math.max(1, crossIndex - 5); i < Math.min(n, crossIndex + 30); i++) {
                double[] a = crossingSamples.get(i - 1);
                double[] b = crossingSamples.get(i);
                double d = Math.sqrt(
                    (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]) + (b[3] - a[3]) * (b[3] - a[3])
                );
                crossJump = Math.max(crossJump, d);
            }
        }
        String line = ("%s: frames=%d crossedAtFrame=%d missingFrames=%d medianSpeed=%.2f p99Speed=%.2f maxSpeed=%.2f " +
            "maxFrameJump=%.3f (frame %d) maxJumpNearCrossing=%.3f").formatted(
            label, n, crossIndex, crossingMissingFrames, median, p99, max, maxJump, maxJumpIndex, crossJump
        );
        results.add(line);
        LOGGER.info("[ImmPtl AutoTest] {}", line);
        // dump the path near the crossing
        if (crossIndex > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = Math.max(0, crossIndex - 4); i < Math.min(n, crossIndex + 40); i++) {
                double[] s = crossingSamples.get(i);
                sb.append("%d:(%.3f %.3f %.3f)%s ".formatted(i, s[1], s[2], s[3], s[4] > 0 ? "*" : ""));
            }
            results.add(label + " path: " + sb);
        }
    }

    /**
     * Spawn the test entity and drive it toward +z with constant speed (on the server).
     */
    private static void driveEntityThroughPortal(String label, net.minecraft.world.phys.Vec3 backOffset) {
        steps.add((mc, t) -> {
            if (!crossingRecorderRegistered) {
                crossingRecorderRegistered = true;
                qouteall.imm_ptl.core.IPGlobal.PRE_GAME_RENDER_EVENT.register(AutoTest::recordCrossingFrame);
            }
            crossingSamples.clear();
            crossingMissingFrames = 0;
            crossingBackOffset = backOffset;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var world = server.overworld();
                var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(
                    world, net.minecraft.world.entity.EntitySpawnReason.COMMAND
                );
                zombie.setUUID(CROSSING_ENTITY_UUID);
                zombie.setNoAi(true);
                zombie.setSilent(true);
                zombie.setPersistenceRequired();
                zombie.snapTo(0.5, 100, 1.5, 0, 0);
                zombie.setYHeadRot(0);
                zombie.setYBodyRot(0);
                world.addFreshEntity(zombie);
            });
            return true;
        });
        waitTicks(20);
        steps.add((mc, t) -> {
            crossingRecording = true;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                for (var world : server.getAllLevels()) {
                    var e = world.getEntity(CROSSING_ENTITY_UUID);
                    if (e != null) {
                        // walking speed, toward +z
                        // (a no-AI mob does not apply its velocity, so move it directly)
                        e.setDeltaMovement(0, 0, 0.12);
                        e.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(0, 0, 0.12));
                        e.setYRot(0);
                        if (e instanceof net.minecraft.world.entity.LivingEntity living) {
                            living.setYBodyRot(0);
                            living.setYHeadRot(0);
                        }
                    }
                }
            });
            if (t == 40) {
                takeScreenshotNow(mc, "crossing_" + label + "_approach");
            }
            if (t == 52) {
                takeScreenshotNow(mc, "crossing_" + label + "_middle");
            }
            return t >= 20 * 6;
        });
        steps.add((mc, t) -> {
            crossingRecording = false;
            analyzeCrossing("crossing " + label);
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                for (var world : server.getAllLevels()) {
                    var e = world.getEntity(CROSSING_ENTITY_UUID);
                    if (e != null) {
                        e.discard();
                    }
                }
            });
            return true;
        });
        waitTicks(20);
    }

    private static void buildCrossingScenario() {
        var W = new net.minecraft.world.phys.Vec3(-1, 0, 0);
        var H = new net.minecraft.world.phys.Vec3(0, 1, 0);

        // the player watches from behind
        cmd("tp @s 0.5 100 -3.5 0 0");
        look(0, 10);

        // same dimension portal
        spawnPortal(
            "far", false, new net.minecraft.world.phys.Vec3(0.5, 102, 5.5), W, H, 3, 4,
            net.minecraft.world.level.Level.OVERWORLD, new net.minecraft.world.phys.Vec3(600.5, 132, 600.5)
        );
        waitTicks(100);
        driveEntityThroughPortal("same_dim", new net.minecraft.world.phys.Vec3(600, 30, 595));
        removePortals();
        waitTicks(20);

        // cross dimension portal
        spawnPortal(
            "nether", false, new net.minecraft.world.phys.Vec3(0.5, 102, 5.5), W, H, 3, 4,
            net.minecraft.world.level.Level.NETHER, new net.minecraft.world.phys.Vec3(0.5, 71, 0.5)
        );
        cmd("tp @s 0.5 100 -3.5 0 0");
        look(0, 10);
        waitTicks(100);
        driveEntityThroughPortal("nether", new net.minecraft.world.phys.Vec3(0, -31, -5));
        removePortals();
    }

    private static void buildLightingScenario() {
        var W = new net.minecraft.world.phys.Vec3(-1, 0, 0);
        var H = new net.minecraft.world.phys.Vec3(0, 1, 0);
        
        // a closed dark room in the nether, at the destination
        cmd("execute in minecraft:the_nether run fill -6 68 -8 6 77 12 minecraft:blackstone hollow");
        waitTicks(20);
        spawnPortal(
            "nether", false, new net.minecraft.world.phys.Vec3(0.5, 102, 5.5), W, H, 3, 4,
            net.minecraft.world.level.Level.NETHER, new net.minecraft.world.phys.Vec3(0.5, 71, 0.5)
        );
        cmd("tp @s 0.5 100 0.5 0 0");
        look(0, 0);
        waitTicks(160);
        screenshot("light_1_dark_room");
        
        // a light source in the remote dimension: the lighting seen through the portal must update
        cmd("execute in minecraft:the_nether run setblock 0 69 4 minecraft:sea_lantern");
        waitTicks(60);
        screenshot("light_2_lantern_placed");
        
        cmd("execute in minecraft:the_nether run setblock 0 69 4 minecraft:air");
        waitTicks(60);
        screenshot("light_3_lantern_removed");
        removePortals();
        waitTicks(20);
        
        // time of day of the remote dimension, seen from another dimension
        cmd("execute in minecraft:the_nether run fill -6 68 -8 6 77 12 minecraft:air");
        cmd("execute in minecraft:the_nether run fill -6 68 -8 6 68 12 minecraft:gold_block");
        cmd("execute in minecraft:the_nether run tp @s 0.5 69 -3.5 0 0");
        waitTicks(60);
        steps.add((mc, t) -> {
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
                Portal portal = Portal.ENTITY_TYPE.create(nether, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                portal.setOriginPos(new net.minecraft.world.phys.Vec3(0.5, 71, 1.5));
                portal.setOrientationAndSize(W, H, 3, 4);
                portal.setDestinationDimension(net.minecraft.world.level.Level.OVERWORLD);
                portal.setDestination(new net.minecraft.world.phys.Vec3(0.5, 102, 0.5));
                qouteall.imm_ptl.core.McHelper.spawnServerEntity(portal);
            });
            return true;
        });
        look(0, 0);
        waitTicks(120);
        screenshot("light_4_overworld_noon_from_nether");
        cmd("execute in minecraft:overworld run time set midnight");
        waitTicks(60);
        screenshot("light_5_overworld_midnight_from_nether");
        cmd("execute in minecraft:overworld run time set noon");
        waitTicks(60);
        screenshot("light_6_overworld_noon_again");
    }
    
    private static void buildBasicScenario() {
        var W = new net.minecraft.world.phys.Vec3(-1, 0, 0);
        var H = new net.minecraft.world.phys.Vec3(0, 1, 0);

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
