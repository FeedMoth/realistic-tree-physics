package dev.realistictreephysics;

import com.mojang.math.Transformation;
import java.lang.reflect.Field;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

/**
 * Sets BlockDisplay state on Minecraft 26.3.
 *
 * Why reflection is needed at all: in 26.3 Mojang made
 * BlockDisplay.setBlockState and Display.setTransformation private
 * (verified with javap against the 26.3 server jar). The underlying
 * synced-data accessors are still the mechanism the game itself uses
 * to send display state to clients, and SynchedEntityData.set is
 * public, so this helper obtains the accessor objects once via
 * reflection and then only uses public set() calls. If Mojang
 * renames those fields in a later version, this helper reports
 * unavailable and falls still settle correctly (the animation
 * simply becomes invisible, because the displays default to air).
 */
public final class DisplayHelper {
    private static EntityDataAccessor<BlockState> blockStateAccessor;
    private static EntityDataAccessor<Vector3fc> translationAccessor;
    private static EntityDataAccessor<Vector3fc> scaleAccessor;
    private static EntityDataAccessor<Quaternionfc> leftRotationAccessor;
    private static EntityDataAccessor<Quaternionfc> rightRotationAccessor;
    private static boolean available = false;

    private DisplayHelper() {
    }

    @SuppressWarnings("unchecked")
    public static void init() {
        try {
            blockStateAccessor = (EntityDataAccessor<BlockState>) field(
                    Display.BlockDisplay.class, "DATA_BLOCK_STATE_ID");
            translationAccessor = (EntityDataAccessor<Vector3fc>) field(
                    Display.class, "DATA_TRANSLATION_ID");
            scaleAccessor = (EntityDataAccessor<Vector3fc>) field(
                    Display.class, "DATA_SCALE_ID");
            leftRotationAccessor = (EntityDataAccessor<Quaternionfc>) field(
                    Display.class, "DATA_LEFT_ROTATION_ID");
            rightRotationAccessor = (EntityDataAccessor<Quaternionfc>) field(
                    Display.class, "DATA_RIGHT_ROTATION_ID");
            available = true;
            RealisticTreePhysicsMod.LOGGER.info("[Realistic Tree Physics] display helper ready");
        } catch (Exception e) {
            available = false;
            RealisticTreePhysicsMod.LOGGER.error(
                    "[Realistic Tree Physics] display helper unavailable (Mojang field names changed?); "
                            + "falls will still settle, but the animation will be invisible", e);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    private static Object field(Class<?> clazz, String name) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    public static void setBlockState(Display.BlockDisplay display, BlockState state) {
        if (available) {
            display.getEntityData().set(blockStateAccessor, state);
        }
    }

    public static void setTransformation(Display display, Transformation transformation) {
        if (!available) {
            return;
        }
        SynchedEntityData data = display.getEntityData();
        data.set(translationAccessor, transformation.translation());
        data.set(leftRotationAccessor, transformation.leftRotation());
        data.set(scaleAccessor, transformation.scale());
        data.set(rightRotationAccessor, transformation.rightRotation());
    }
}
