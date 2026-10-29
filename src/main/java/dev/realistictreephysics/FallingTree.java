package dev.realistictreephysics;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One falling tree, animated with vanilla BlockDisplay entities.
 *
 * Geometry: the tree rotates as a rigid body about a horizontal axis
 * through the trunk base, in the plane spanned by the fall direction
 * and "up". A block corner C at vector v = C - pivot decomposes into
 * s = v along the fall direction, u = v vertical, p = v across the
 * fall (unchanged). At tip angle theta:
 *   s' = s*cos(theta) + u*sin(theta)
 *   u' = -s*sin(theta) + u*cos(theta)
 * BlockDisplay models are corner-anchored at the entity position, so
 * placing the display at the rotated corner and giving it the same
 * rotation (as its left rotation) reproduces the rigid rotation.
 *
 * Timing: 20 ticks of tipping (about 1 second, eased), then a
 * 6-tick bounce where the angle dips back up to 5 degrees and
 * settles. Total 26 ticks, about 1.3 seconds, deliberately short so
 * the window in which the blocks exist only as displays is small.
 *
 * Ground finding: the pivot is the chop point, which for a
 * mid-trunk chop sits above the ground. At settle time each block
 * is calculated on its own: it looks straight down from its
 * rotated position, finds the closest solid block below it
 * (terrain, a stump, a build, or a block already placed by this
 * fall), and lands directly on top of that block. That way a log
 * over low ground drops further than a log over high ground,
 * instead of the whole tree dropping by one shared amount and
 * some logs ending up a block too low, inside the ground, where
 * they would break instead of placing. Blocks that would clip
 * into uphill ground drop as items instead of overwriting it.
 * The settle step never overwrites a solid non-tree block.
 */
public class FallingTree {
    public static final String TAG = "realistic-tree-physics";

    private final int tipTicks;
    private final int bounceTicks;
    private final int totalTicks;

    private final ServerLevel level;
    private final TreeDetector.Tree tree;
    private final Direction fall;
    private final double pivotX;
    private final double pivotY;
    private final double pivotZ;
    private final double fallX;
    private final double fallZ;
    private final double acrossX;
    private final double acrossZ;
    private final List<AnimatedBlock> displays = new ArrayList<>();
    private int tick = 0;
    private boolean settled = false;

    private record AnimatedBlock(Display display, BlockPos original, BlockState state, double s, double u, double p) {
    }

    public FallingTree(ServerLevel level, TreeDetector.Tree tree, Direction fall) {
        this.level = level;
        this.tree = tree;
        this.fall = fall;
        this.tipTicks = RealisticTreePhysicsConfig.get().tipTicks;
        this.bounceTicks = RealisticTreePhysicsConfig.get().bounceTicks;
        this.totalTicks = tipTicks + bounceTicks;
        // Pivot: centre of the chop block, on all three axes.
        // Using the bottom of the block for Y used to put the
        // rotated trunk half a block low, which floors to a full
        // block too low, inside the ground. The stump below the
        // chop is not part of this fall and stays standing.
        this.pivotX = tree.pivot().getX() + 0.5;
        this.pivotY = tree.pivot().getY() + 0.5;
        this.pivotZ = tree.pivot().getZ() + 0.5;
        this.fallX = fall.getStepX();
        this.fallZ = fall.getStepZ();
        // Across axis: rotate fall by 90 degrees horizontally.
        this.acrossX = -fallZ;
        this.acrossZ = fallX;

        for (Map.Entry<BlockPos, BlockState> entry : tree.blocks().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            double vx = pos.getX() - pivotX;
            double vy = pos.getY() - pivotY;
            double vz = pos.getZ() - pivotZ;
            double s = vx * fallX + vz * fallZ;
            double p = vx * acrossX + vz * acrossZ;
            double u = vy;
            Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
            DisplayHelper.setBlockState(display, state);
            display.addTag(TAG);
            display.setPos(pos.getX(), pos.getY(), pos.getZ());
            DisplayHelper.setTransformation(display, new Transformation(
                    new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
            level.addFreshEntity(display);
            displays.add(new AnimatedBlock(display, pos, state, s, u, p));
        }
    }

    /** Advance one tick. Returns true when the fall has settled. */
    public boolean tick() {
        if (settled) {
            return true;
        }
        tick++;
        if (tick >= totalTicks) {
            settle();
            return true;
        }
        double theta;
        if (tick <= tipTicks) {
            double progress = (double) tick / tipTicks;
            // Ease in: slow start, accelerating tip, like a real tree.
            double eased = 1.0 - Math.cos(progress * Math.PI / 2.0);
            theta = (Math.PI / 2.0) * eased;
        } else {
            // Bounce: dip up to 5 degrees off the ground, then back.
            double bounceProgress = (double) (tick - tipTicks) / bounceTicks;
            theta = Math.PI / 2.0 - Math.toRadians(5.0) * Math.sin(Math.PI * bounceProgress);
        }
        applyAngle(theta);
        return false;
    }

    private void applyAngle(double theta) {
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        // Rotation axis is up x fall (right-hand rule), which tips
        // "up" towards the fall direction.
        double axisX = 1.0 * fallZ - 0.0;
        double axisY = 0.0;
        double axisZ = 0.0 - 1.0 * fallX;
        // up (0,1,0) x fall (fallX,0,fallZ) = (fallZ, 0, -fallX)
        Quaternionf rotation = new Quaternionf(new AxisAngle4f((float) theta, (float) axisX, (float) axisY, (float) axisZ));
        Transformation transformation = new Transformation(
                new Vector3f(0, 0, 0), rotation, new Vector3f(1, 1, 1), new Quaternionf());
        for (AnimatedBlock block : displays) {
            double sPrime = block.s() * cos + block.u() * sin;
            double uPrime = -block.s() * sin + block.u() * cos;
            double worldX = pivotX + sPrime * fallX + block.p() * acrossX;
            double worldY = pivotY + uPrime;
            double worldZ = pivotZ + sPrime * fallZ + block.p() * acrossZ;
            block.display().setPos(worldX, worldY, worldZ);
            DisplayHelper.setTransformation(block.display(), transformation);
        }
    }

    /** Place the fallen blocks, discarding the displays. */
    private void settle() {
        if (settled) {
            return;
        }
        settled = true;
        double theta = Math.PI / 2.0;
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        Map<BlockPos, BlockState> toPlace = new HashMap<>();
        // Logs claim cells before leaves, so a canopy leaf rotating
        // onto the trunk line cannot steal a log's cell: the promise
        // is that every log lands as a block the player can still
        // break by hand, while a displaced leaf drops as an item.
        List<AnimatedBlock> ordered = new ArrayList<>(displays);
        ordered.sort(java.util.Comparator.comparing(block -> !TreeDetector.isLog(block.state())));
        for (AnimatedBlock block : ordered) {
            double sPrime = block.s() * cos + block.u() * sin;
            double uPrime = -block.s() * sin + block.u() * cos;
            double worldX = pivotX + sPrime * fallX + block.p() * acrossX;
            double worldY = pivotY + uPrime;
            double worldZ = pivotZ + sPrime * fallZ + block.p() * acrossZ;
            // Corner -> block coordinate. No flat clamp here: the
            // ground-drop step below moves the whole shape down to
            // real terrain, instead of assuming the pivot's level
            // is the ground everywhere.
            int x = (int) Math.floor(worldX);
            int y = (int) Math.floor(worldY);
            int z = (int) Math.floor(worldZ);
            BlockPos target = new BlockPos(x, y, z);
            // First claimant wins if two blocks rotate onto one cell;
            // the loser drops as an item below rather than vanishing.
            if (!toPlace.containsKey(target)) {
                toPlace.put(target, TreeDetector.remapAxisForFall(block.state(), fall));
            } else {
                dropAsItem(block.state(), target);
            }
            block.display().discard();
        }
        // Logs place first, so a leaf dropping onto the trunk
        // line lands on a placed log instead of breaking.
        List<Map.Entry<BlockPos, BlockState>> orderedToPlace = new ArrayList<>(toPlace.entrySet());
        orderedToPlace.sort(java.util.Comparator.comparing(entry -> !TreeDetector.isLog(entry.getValue())));
        int placed = 0;
        java.util.Set<BlockPos> placedTargets = new java.util.HashSet<>();
        for (Map.Entry<BlockPos, BlockState> entry : orderedToPlace) {
            BlockPos target = entry.getKey();
            if (RealisticTreePhysicsConfig.get().groundDrop) {
                target = new BlockPos(target.getX(), landingY(target, placedTargets), target.getZ());
            }
            if (target.getY() < level.dimensionType().minY()) {
                dropAsItem(entry.getValue(), entry.getKey());
                continue;
            }
            if (placedTargets.contains(target)) {
                dropAsItem(entry.getValue(), target);
                continue;
            }
            BlockState existing = level.getBlockState(target);
            if (!existing.isAir() && !tree.blocks().containsKey(target)) {
                // Never overwrite solid terrain or a build.
                dropAsItem(entry.getValue(), target);
                continue;
            }
            level.setBlock(target, entry.getValue(), 3);
            placedTargets.add(target);
            placed++;
        }
        level.playSound(null, tree.pivot(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        RealisticTreePhysicsMod.LOGGER.info("[Realistic Tree Physics] fall settled at {} facing {} with {} blocks placed",
                tree.pivot(), fall, placed);
    }

    /**
     * Landing Y for one block, calculated on its own. Scan down
     * from its rotated position for the closest solid block below
     * it: terrain, a stump, a build, or a block already placed by
     * this fall. The block lands directly on top of that solid.
     * Falling blocks have already been removed from the world, so
     * anything solid found is not part of this fall, except blocks
     * placed earlier in this same settle. If no solid is found
     * within 64 blocks, the block stays at its rotated height.
     */
    private int landingY(BlockPos target, java.util.Set<BlockPos> placedTargets) {
        int minY = level.dimensionType().minY();
        int floor = Math.max(minY, target.getY() - 64);
        for (int y = target.getY() - 1; y >= floor; y--) {
            BlockPos below = new BlockPos(target.getX(), y, target.getZ());
            if (placedTargets.contains(below) || !level.getBlockState(below).isAir()) {
                return y + 1;
            }
        }
        return target.getY();
    }

    /** Settle immediately (used if ticking throws). */
    public void settleEarly() {
        settle();
    }

    private void dropAsItem(BlockState state, BlockPos at) {
        Block block = state.getBlock();
        ItemStack stack = new ItemStack(block.asItem());
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, stack);
        level.addFreshEntity(item);
    }
}
