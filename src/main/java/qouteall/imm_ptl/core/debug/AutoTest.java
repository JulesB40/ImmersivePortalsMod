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
        Minecraft.getInstance().options.onboardAccessibility = false;
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
            String line = "%s: dim=%s pos=%s portals=%d fps=%d".formatted(
                label, mc.level.dimension().identifier(), mc.player.position(), portalNum, mc.getFps()
            );
            results.add(line);
            LOGGER.info("[ImmPtl AutoTest] {}", line);
            return true;
        });
    }

    private static void buildScenario(String scenario) {
        waitTicks(100);
        cmd("time set noon");
        cmd("weather clear");
        cmd("gamerule advance_time false");
        cmd("gamemode creative");
        // stand on the ground, looking south and slightly down
        cmd("tp @s 0 ~ 0 0 25");
        look(0, 25);
        waitTicks(60);
        screenshot("00_no_portal");
        report("before portal");

        // portal to the nether (cross dimension)
        cmd("portal make_portal 3 4 minecraft:the_nether 0 80 0");
        waitTicks(200);
        look(0, 5);
        waitTicks(20);
        screenshot("01_nether_portal");
        report("nether portal");

        // walk to the side and look at the portal from an angle
        cmd("tp @s ~3 ~ ~-1 -40 5");
        look(-40, 5);
        waitTicks(40);
        screenshot("02_nether_portal_angle");

        // same dimension portal far away
        cmd("tp @s 0 ~ 0 180 25");
        look(180, 25);
        waitTicks(20);
        cmd("portal make_portal 3 4 minecraft:overworld 500 100 500");
        waitTicks(200);
        look(180, 5);
        waitTicks(20);
        screenshot("03_overworld_far_portal");
        report("same dim portal");

        // walk through the nether portal
        cmd("tp @s 0 ~ 0 0 5");
        look(0, 5);
        waitTicks(10);
        steps.add((mc, t) -> {
            mc.options.keyUp.setDown(t < 40);
            return t >= 60;
        });
        waitTicks(60);
        screenshot("04_after_walking_through");
        report("after walking");

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
