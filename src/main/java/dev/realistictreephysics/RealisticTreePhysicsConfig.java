package dev.realistictreephysics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/realistic-tree-physics.json, created with defaults on first run.
 *
 * Only server-side tuning knobs live here. The stump rule itself
 * (only the part above the chop falls) is core behaviour, not a
 * toggle: a toggle that reverted to whole-tree falls would need a
 * second, untested settle path.
 */
public final class RealisticTreePhysicsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RealisticTreePhysicsConfig instance = new RealisticTreePhysicsConfig();

    public boolean enabled = true;
    public boolean requireAxe = true;
    public boolean sneakOptOut = true;
    // Thresholds for the whole detected tree, before the cut.
    // The falling part above the chop only needs >= 1 log and
    // >= 1 leaf of its own (see TreeDetector).
    public int minLogs = 3;
    public int minLeaves = 3;
    public int minSpan = 3;
    public int maxBlocks = 256;
    // Large trees: off by default, because a big canopy is a lot of
    // falling blocks and a touching forest can merge into one fall.
    // Turn this on to raise the cap to 4096 blocks instead of
    // maxBlocks. maxBlocks still applies when this is off.
    public boolean allowLargeTrees = false;
    public int tipTicks = 20;
    public int bounceTicks = 6;
    public boolean groundDrop = true;

    private RealisticTreePhysicsConfig() {
    }

    public static RealisticTreePhysicsConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("realistic-tree-physics.json");
        try {
            if (Files.exists(path)) {
                RealisticTreePhysicsConfig loaded = GSON.fromJson(Files.readString(path), RealisticTreePhysicsConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
                RealisticTreePhysicsMod.LOGGER.info("[Realistic Tree Physics] config loaded from {}", path);
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(instance));
                RealisticTreePhysicsMod.LOGGER.info("[Realistic Tree Physics] config created with defaults at {}", path);
            }
        } catch (IOException e) {
            RealisticTreePhysicsMod.LOGGER.error("[Realistic Tree Physics] config could not be read, using defaults", e);
        }
    }

    public static void reload() {
        load();
    }
}
