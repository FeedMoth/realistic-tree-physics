package dev.realistictreephysics;

import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;

/**
 * Sets BlockDisplay state on Minecraft 1.21.
 *
 * In Yarn the display data tracker fields are public, so no
 * reflection is needed: the block state and the transformation
 * components are set through the data tracker directly. If Mojang
 * changes those fields in a later version, this helper is the
 * only place that needs to change, and falls still settle
 * correctly (the animation simply becomes invisible).
 */
public final class DisplayHelper {
    private DisplayHelper() {
    }

    public static void init() {
        RealisticTreePhysicsMod.LOGGER.info("[Realistic Tree Physics] display helper ready");
    }

    public static boolean isAvailable() {
        return true;
    }

    public static void setBlockState(DisplayEntity.BlockDisplayEntity display, BlockState state) {
        display.getDataTracker().set(DisplayEntity.BlockDisplayEntity.BLOCK_STATE, state);
    }

    public static void setTransformation(DisplayEntity display, AffineTransformation transformation) {
        display.getDataTracker().set(DisplayEntity.TRANSLATION, transformation.translation());
        display.getDataTracker().set(DisplayEntity.LEFT_ROTATION, transformation.leftRotation());
        display.getDataTracker().set(DisplayEntity.SCALE, transformation.scale());
        display.getDataTracker().set(DisplayEntity.RIGHT_ROTATION, transformation.rightRotation());
    }
}
