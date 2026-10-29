package dev.realistictreephysics;

import java.lang.reflect.Field;
import net.minecraft.block.BlockState;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Sets BlockDisplay state on Minecraft 1.21.
 *
 * In 1.21 the display data tracker fields are private, so this
 * helper obtains them once via reflection and then only uses
 * public DataTracker.set() calls, the same mechanism the game
 * itself uses to send display state to clients. The field names
 * below are the runtime (intermediary) names, not the Yarn
 * names: string literals are not remapped at build time, so
 * Yarn names fail at runtime with NoSuchFieldException. If
 * Mojang renames those fields in a later version, this helper
 * reports unavailable and falls still settle correctly (the
 * animation simply becomes invisible, because the displays
 * default to air).
 */
public final class DisplayHelper {
    private static TrackedData<BlockState> blockStateAccessor;
    private static TrackedData<Vector3f> translationAccessor;
    private static TrackedData<Vector3f> scaleAccessor;
    private static TrackedData<Quaternionf> leftRotationAccessor;
    private static TrackedData<Quaternionf> rightRotationAccessor;
    private static boolean available = false;

    private DisplayHelper() {
    }

    @SuppressWarnings("unchecked")
    public static void init() {
        try {
            blockStateAccessor = (TrackedData<BlockState>) field(
                    DisplayEntity.BlockDisplayEntity.class, "field_42416");
            translationAccessor = (TrackedData<Vector3f>) field(
                    DisplayEntity.class, "field_42401");
            scaleAccessor = (TrackedData<Vector3f>) field(
                    DisplayEntity.class, "field_42402");
            leftRotationAccessor = (TrackedData<Quaternionf>) field(
                    DisplayEntity.class, "field_42403");
            rightRotationAccessor = (TrackedData<Quaternionf>) field(
                    DisplayEntity.class, "field_42367");
            available = true;
            RealisticTreePhysicsMod.LOGGER.info("[Realistic Tree Physics] display helper ready");
        } catch (Exception e) {
            available = false;
            RealisticTreePhysicsMod.LOGGER.error(
                    "[Realistic Tree Physics] display helper unavailable (field names changed?); "
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

    public static void setBlockState(DisplayEntity.BlockDisplayEntity display, BlockState state) {
        if (available) {
            display.getDataTracker().set(blockStateAccessor, state);
        }
    }

    public static void setTransformation(DisplayEntity display, AffineTransformation transformation) {
        if (!available) {
            return;
        }
        display.getDataTracker().set(translationAccessor, new Vector3f(transformation.getTranslation()));
        display.getDataTracker().set(leftRotationAccessor, new Quaternionf(transformation.getLeftRotation()));
        display.getDataTracker().set(scaleAccessor, new Vector3f(transformation.getScale()));
        display.getDataTracker().set(rightRotationAccessor, new Quaternionf(transformation.getRightRotation()));
    }
}
