package dev.realistictreephysics;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Realistic Tree Physics: an original, clean-room implementation written from scratch.
 *
 * Behaviour only, no code copied from any other tree-fall mod:
 * chop a tree with an axe and the whole tree tips over as one piece,
 * bounces slightly, then settles back into blocks that can still be
 * broken by hand. Nothing is auto-harvested and no extra axe
 * durability is consumed beyond the one block the player broke.
 *
 * Server-side only. The animation uses vanilla BlockDisplay
 * entities, which a vanilla Java client can already render, so
 * Java players need no client mod. Bedrock players through Geyser
 * will not see the custom animation reliably; they still get the
 * correct settled blocks (see README).
 */
public class RealisticTreePhysicsMod implements ModInitializer {
    public static final String MOD_ID = "realistic-tree-physics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Active falls, ticked from the end-of-server-tick event. */
    private static final List<FallingTree> ACTIVE = new ArrayList<>();

    @Override
    public void onInitialize() {
        RealisticTreePhysicsConfig.load();
        DisplayHelper.init();
        LOGGER.info("[Realistic Tree Physics] loaded: chop a tree with an axe and it tips over");

        // Remove any stray animation displays left by a previous run
        // (e.g. a server stop mid-fall). Their blocks are already gone
        // from the world, so this only clears the visual leftovers.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                    if (entity instanceof Display display
                            && display.entityTags().contains(FallingTree.TAG)) {
                        display.discard();
                    }
                }
            }
        });

        // Player break: AFTER fires once the block is already gone, so
        // the broken log itself is not part of the fall. The rest of
        // the tree is what tips over. If detection aborts, the rest of
        // the tree simply stays standing (vanilla behaviour).
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level)) {
                return;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (serverPlayer.isSpectator()) {
                return;
            }
            // Sneak opt-out: crouching while chopping means "just this
            // one log, please" (harvesting individual logs for
            // crafting/building). No fall is started at all, and
            // vanilla single-block breaking applies instead. This
            // applies to any log break while sneaking, not just the
            // base log.
            if (RealisticTreePhysicsConfig.get().sneakOptOut && serverPlayer.isShiftKeyDown()) {
                return;
            }
            if (RealisticTreePhysicsConfig.get().requireAxe && !serverPlayer.getMainHandItem().is(ItemTags.AXES)) {
                return;
            }
            if (!RealisticTreePhysicsConfig.get().enabled) {
                return;
            }
            if (!TreeDetector.isLog(state)) {
                return;
            }
            Direction facing = serverPlayer.getDirection();
            if (facing.getAxis() == Direction.Axis.Y) {
                facing = Direction.NORTH;
            }
            startFall(level, pos, facing);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ACTIVE.isEmpty()) {
                return;
            }
            List<FallingTree> done = new ArrayList<>();
            for (FallingTree fall : new ArrayList<>(ACTIVE)) {
                try {
                    if (fall.tick()) {
                        done.add(fall);
                    }
                } catch (Exception e) {
                    LOGGER.error("[Realistic Tree Physics] error ticking a fall, settling it early", e);
                    fall.settleEarly();
                    done.add(fall);
                }
            }
            ACTIVE.removeAll(done);
        });

        // Debug command for testing the real player-break path without
        // a client: /realistic-tree-physics broken <x> <y> <z> <direction> runs
        // detection with the given position as the already-broken
        // (air) log, exactly as PlayerBlockBreakEvents.AFTER does.
        // Use it after /setblock-ing the target log to air. This is
        // how the bottom-log fix is regression-tested: a bottom
        // position should settle at its own Y, a mid-trunk position
        // should settle at the lowest remaining log's Y.
        // Debug commands are operator-only (permission level 2).
        // Without this, any player could fell trees remotely with
        // /realistic-tree-physics at / look / broken, bypassing the axe and sneak
        // checks. That is the one real griefing hole in the 1.0.1
        // build, and it must not ship in a public release.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("realistic-tree-physics")
                    .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("broken")
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                    .then(Commands.argument("direction", StringArgumentType.word())
                    .executes(context -> {
                        BlockPos target = new BlockPos(
                                IntegerArgumentType.getInteger(context, "x"),
                                IntegerArgumentType.getInteger(context, "y"),
                                IntegerArgumentType.getInteger(context, "z"));
                        Direction facing = switch (
                                StringArgumentType.getString(context, "direction").toLowerCase()) {
                            case "east" -> Direction.EAST;
                            case "south" -> Direction.SOUTH;
                            case "west" -> Direction.WEST;
                            default -> Direction.NORTH;
                        };
                        Level level = context.getSource().getLevel();
                        if (!(level instanceof ServerLevel serverLevel)) {
                            context.getSource().sendFailure(
                                    Component.literal("Only a player can use /realistic-tree-physics broken"));
                            return 0;
                        }
                        if (startFall(serverLevel, target, facing)) {
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Realistic Tree Physics: tree at " + target + " is falling"),
                                    false);
                            return 1;
                        }
                        context.getSource().sendFailure(
                                Component.literal("Realistic Tree Physics: no valid tree at " + target));
                        return 0;
                    }))))))
                    .then(Commands.literal("look")
                            .executes(context -> {
                                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                                    context.getSource().sendFailure(
                                            Component.literal("Only a player can use /realistic-tree-physics look"));
                                    return 0;
                                }
                                HitResult hit = player.pick(12.0, 0.0f, false);
                                if (!(hit instanceof BlockHitResult blockHit)
                                        || hit.getType() != HitResult.Type.BLOCK) {
                                    context.getSource().sendFailure(
                                            Component.literal("Not looking at a block"));
                                    return 0;
                                }
                                BlockPos target = blockHit.getBlockPos();
                                Direction facing = player.getDirection();
                                if (facing.getAxis() == Direction.Axis.Y) {
                                    facing = Direction.NORTH;
                                }
                                if (startFall(player.level(), target, facing)) {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Realistic Tree Physics: tree at " + target + " is falling"),
                                            false);
                                    return 1;
                                }
                                context.getSource().sendFailure(
                                        Component.literal("Realistic Tree Physics: no valid tree at " + target));
                                return 0;
                            }))
                    .then(Commands.literal("at")
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                            .then(Commands.argument("direction", StringArgumentType.word())
                            .executes(context -> {
                                BlockPos target = new BlockPos(
                                        IntegerArgumentType.getInteger(context, "x"),
                                        IntegerArgumentType.getInteger(context, "y"),
                                        IntegerArgumentType.getInteger(context, "z"));
                                Direction facing = switch (
                                        StringArgumentType.getString(context, "direction").toLowerCase()) {
                                    case "east" -> Direction.EAST;
                                    case "south" -> Direction.SOUTH;
                                    case "west" -> Direction.WEST;
                                    default -> Direction.NORTH;
                                };
                                Level level = context.getSource().getLevel();
                                if (!(level instanceof ServerLevel serverLevel)) {
                                    context.getSource().sendFailure(
                                            Component.literal("Not a server level"));
                                    return 0;
                                }
                                if (startFall(serverLevel, target, facing)) {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Realistic Tree Physics: tree at " + target + " is falling"),
                                            false);
                                    return 1;
                                }
                                context.getSource().sendFailure(
                                        Component.literal("Realistic Tree Physics: no valid tree at " + target));
                                return 0;
                            })))))));
        });
    }

    /**
     * Detect a tree rooted at (or containing) {@code brokenOrTarget} and,
     * if valid, remove its blocks and start the falling animation.
     * Returns true when a fall was started. The block at
     * {@code brokenOrTarget} itself is treated as already gone: for a
     * real player break it was just broken, and for /realistic-tree-physics at it is
     * the trunk block the command is aimed at (it is removed as part
     * of the fall, without dropping an item, matching the player case
     * where that block's normal drop already happened).
     */
    public static boolean startFall(ServerLevel level, BlockPos brokenOrTarget, Direction facing) {
        TreeDetector.Tree tree = TreeDetector.detect(level, brokenOrTarget);
        if (tree == null) {
            return false;
        }
        // Remove the falling blocks from the world without drops.
        // Using setBlock to air never drops items; the player's own
        // broken block already dropped normally through the break
        // event. Blocks at or below the chop (the stump) are not in
        // tree.blocks() and are left exactly where they are.
        for (BlockPos pos : tree.blocks().keySet()) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        // For /realistic-tree-physics at / look, the target log itself is still
        // present and sits at the chop height, so it is part of the
        // stump, not the fall. Remove it without a drop to mimic a
        // player break exactly; for a real player break it is
        // already air and this is a no-op.
        if (TreeDetector.isLog(level.getBlockState(brokenOrTarget))) {
            level.setBlock(brokenOrTarget, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        FallingTree fall = new FallingTree(level, tree, facing);
        ACTIVE.add(fall);
        LOGGER.info("[Realistic Tree Physics] fall started at {} facing {} with {} blocks ({} logs, {} leaves, {} stump logs)",
                brokenOrTarget, facing, tree.blocks().size(), tree.logCount(), tree.leafCount(),
                tree.stumpLogCount());
        return true;
    }
}
