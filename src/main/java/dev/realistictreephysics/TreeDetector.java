package dev.realistictreephysics;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Tree detection, written from scratch for this mod.
 *
 * Rules (kept deliberately conservative so log houses do not fall):
 * - The seed is a log block, or, for a real player break, a position
 *   whose neighbouring blocks are logs (the broken log is already gone).
 * - Logs are flood-filled through all 26 neighbouring positions, so
 *   diagonal branches are followed. This is also why two trees whose
 *   logs actually touch can merge into one fall; that is a documented
 *   limitation, not a bug to silently "fix" by dropping branches.
 * - Leaves are collected by flood-filling leaf blocks that touch a
 *   log or another collected leaf (face neighbours only).
 * - A tree needs a vertical log span of at least 3, at least 3 logs
 *   in total, and at least 3 leaves. Without leaves it is treated as
 *   a player-built structure and left alone.
 * - Hard cap of 256 blocks (logs + leaves). Over the cap the whole
 *   detection aborts and nothing is removed.
 */
public final class TreeDetector {
    public static final int MAX_BLOCKS = 256;

    private static int maxBlocks() {
        return Math.max(1, RealisticTreePhysicsConfig.get().maxBlocks);
    }

    private TreeDetector() {
    }

    // Cut-plane rule (1.1.0): only the part of the tree ABOVE the
    // chop falls. Everything at or below the chop Y stays standing
    // as a stump, including other trunk columns at the chop height
    // in a 2x2 tree. The pivot is the chop position itself, and the
    // falling part drops vertically in the settle step until it
    // rests on solid ground, so a mid-trunk chop does not leave a
    // tree floating at chop height.
    public record Tree(Map<BlockPos, BlockState> blocks, int logCount, int leafCount, BlockPos pivot,
                       int stumpLogCount) {
    }

    public static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    public static boolean isLeaf(BlockState state) {
        return state.is(BlockTags.LEAVES);
    }

    public static Tree detect(ServerLevel level, BlockPos seedPos) {
        Set<BlockPos> logs = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        BlockState seedState = level.getBlockState(seedPos);
        if (isLog(seedState)) {
            queue.add(seedPos.immutable());
        } else {
            // Player-break case: the broken log is already air. Seed
            // from any neighbouring log instead.
            for (BlockPos neighbour : neighbours26(seedPos)) {
                if (isLog(level.getBlockState(neighbour))) {
                    queue.add(neighbour.immutable());
                }
            }
        }
        if (queue.isEmpty()) {
            return null;
        }

        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            if (!logs.add(pos)) {
                continue;
            }
            if (logs.size() > maxBlocks()) {
                return null;
            }
            for (BlockPos neighbour : neighbours26(pos)) {
                if (!logs.contains(neighbour) && isLog(level.getBlockState(neighbour))) {
                    queue.add(neighbour.immutable());
                }
            }
        }

        // Leaves touching a log, then leaves touching those leaves.
        Set<BlockPos> leaves = new HashSet<>();
        Queue<BlockPos> leafQueue = new ArrayDeque<>();
        for (BlockPos log : logs) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbour = log.relative(dir);
                if (isLeaf(level.getBlockState(neighbour)) && leaves.add(neighbour.immutable())) {
                    leafQueue.add(neighbour.immutable());
                }
            }
        }
        while (!leafQueue.isEmpty()) {
            BlockPos pos = leafQueue.remove();
            for (Direction dir : Direction.values()) {
                BlockPos neighbour = pos.relative(dir);
                if (!leaves.contains(neighbour) && !logs.contains(neighbour)
                        && isLeaf(level.getBlockState(neighbour))) {
                    leaves.add(neighbour.immutable());
                    leafQueue.add(neighbour.immutable());
                }
            }
            if (logs.size() + leaves.size() > maxBlocks()) {
                return null;
            }
        }

        // Cut plane: the falling part is everything strictly above
        // the chop Y. Validation applies to the WHOLE detected tree
        // first (the original anti-build rule: enough logs, enough
        // leaves, enough span), because the falling part alone is
        // just a chunk of a real tree. A mid chop on a 5-log tree
        // leaves only 2 logs above the chop, and requiring 3 logs
        // in the falling part alone would wrongly refuse that fall.
        // The falling part itself only needs at least 1 log and at
        // least 1 leaf: a bare log above the canopy, with no leaves
        // above the chop, is just a log and does not fell.
        int chopY = seedPos.getY();
        {
            int wholeMinY = Integer.MAX_VALUE;
            int wholeMaxY = Integer.MIN_VALUE;
            for (BlockPos log : logs) {
                if (log.getY() < wholeMinY) {
                    wholeMinY = log.getY();
                }
                if (log.getY() > wholeMaxY) {
                    wholeMaxY = log.getY();
                }
            }
            if (logs.size() < RealisticTreePhysicsConfig.get().minLogs
                    || leaves.size() < RealisticTreePhysicsConfig.get().minLeaves
                    || (wholeMaxY - wholeMinY + 1) < RealisticTreePhysicsConfig.get().minSpan) {
                return null;
            }
        }
        Set<BlockPos> fallingLogs = new HashSet<>();
        Set<BlockPos> fallingLeaves = new HashSet<>();
        int stumpLogCount = 0;
        for (BlockPos log : logs) {
            if (log.getY() > chopY) {
                fallingLogs.add(log);
            } else {
                stumpLogCount++;
            }
        }
        for (BlockPos leaf : leaves) {
            if (leaf.getY() > chopY) {
                fallingLeaves.add(leaf);
            }
        }
        if (fallingLogs.isEmpty() || fallingLeaves.isEmpty()) {
            return null;
        }

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (BlockPos log : fallingLogs) {
            blocks.put(log, level.getBlockState(log));
        }
        for (BlockPos leaf : fallingLeaves) {
            blocks.put(leaf, level.getBlockState(leaf));
        }
        return new Tree(Map.copyOf(blocks), fallingLogs.size(), fallingLeaves.size(),
                seedPos.immutable(), stumpLogCount);
    }

    private static Iterable<BlockPos> neighbours26(BlockPos pos) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    out.add(pos.offset(dx, dy, dz));
                }
            }
        }
        return out;
    }

    /**
     * Remap a log's axis through the same 90-degree tip the tree
     * undergoes. A vertical (Y) log ends up pointing along the fall
     * direction, a log pointing along the fall direction ends up
     * vertical, and a log pointing across the fall stays across it.
     * Leaves have no axis and are returned unchanged.
     */
    public static BlockState remapAxisForFall(BlockState state, Direction fall) {
        if (!state.hasProperty(BlockStateProperties.AXIS)) {
            return state;
        }
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        Direction.Axis fallAxis = fall.getAxis();
        Direction.Axis acrossAxis = fallAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Direction.Axis result = switch (axis) {
            case Y -> fallAxis;
            case X -> fallAxis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
            case Z -> fallAxis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        };
        // The switch above is written per-axis; collapse the
        // across-axis case: a log already pointing across the fall
        // does not rotate, so it keeps its own axis.
        if (axis == acrossAxis) {
            result = axis;
        }
        if (result == axis) {
            return state;
        }
        return state.setValue(BlockStateProperties.AXIS, result);
    }
}
