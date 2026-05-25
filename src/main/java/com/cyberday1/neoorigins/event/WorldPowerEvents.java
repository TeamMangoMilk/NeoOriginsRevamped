package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.*;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class WorldPowerEvents {
    private static final int TREE_SCAN_HORIZONTAL_RADIUS = 8;
    private static final int TREE_SCAN_DOWN = 4;
    private static final int TREE_SCAN_UP = 48;
    private static final int LEAF_SEARCH_RADIUS = 6;
    private static final int BRANCH_LOG_SEARCH_RADIUS = 2;
    private static final int DETACHED_BRANCH_MAX_LOGS = 6;
    private static final int DETACHED_BRANCH_MAX_VERTICAL_RUN = 2;

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer sp)) return;

        // Summoned minions must never target their own summoner. Overrides the
        // retaliation window below — even if the player hits their own minion
        // by accident, the minion should not turn on them.
        if (com.cyberday1.neoorigins.service.MinionTracker.isTrackedMinionOf(event.getEntity(), sp.getUUID())) {
            event.setCanceled(true);
            return;
        }

        // ScareEntitiesPower — mobs that the player is scaring shouldn't be
        // able to target them in the first place. This runs BEFORE the
        // MobsIgnorePlayer check so scare-matched mobs are locked out even if
        // the MobsIgnore retaliation window has already expired. Retaliation
        // still works: if the mob is the last hurt-by source for the player
        // within vanilla's timer, we allow the target change so combat
        // feedback loops work.
        if (ActiveOriginService.has(sp, com.cyberday1.neoorigins.power.builtin.ScareEntitiesPower.class,
                cfg -> cfg.entityTypes().stream().anyMatch(id ->
                    com.cyberday1.neoorigins.event.CombatPowerEvents.matchesEntityIdOrTag(event.getEntity(), id)))) {
            if (event.getEntity().getLastHurtByMob() == sp) return;
            event.setCanceled(true);
            return;
        }

        if (ActiveOriginService.has(sp, MobsIgnorePlayerPower.class,
                cfg -> cfg.entityTypes().isEmpty()
                    || cfg.entityTypes().stream().anyMatch(id ->
                        com.cyberday1.neoorigins.event.CombatPowerEvents.matchesEntityIdOrTag(event.getEntity(), id)))) {
            // Retaliation window: if the player recently hit this mob, allow
            // targeting so the mob can fight back. Vanilla clears
            // getLastHurtByMob() on its own timer; we just defer to it.
            if (event.getEntity().getLastHurtByMob() == sp) return;
            event.setCanceled(true);
            return;
        }

        // SneakyPower — reduce detection range
        if (ActiveOriginService.has(sp, SneakyPower.class, c -> true)) {
            var mob = event.getEntity();
            double dist = mob.distanceTo(sp);
            var followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
            double range = followRange != null ? followRange.getValue() : 16.0;
            final double[] mult = {1.0};
            ActiveOriginService.forEachOfType(sp, SneakyPower.class, cfg ->
                mult[0] = Math.min(mult[0], cfg.detectionMultiplier()));
            if (dist > range * mult[0]) {
                event.setCanceled(true);
                return;
            }
        }

        // StealthPower — if player has been sneaking long enough, cancel targeting
        if (ActiveOriginService.has(sp, StealthPower.class, c -> true)) {
            if (sp.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        Mob mob = event.getEntity();
        if (mob.getType().getCategory() != MobCategory.MONSTER) return;
        for (ServerPlayer sp : sl.players()) {
            ActiveOriginService.forEachOfType(sp, NoMobSpawnsNearbyPower.class, cfg -> {
                if (sp.distanceTo(mob) <= cfg.radius() && matchesSpawnCategory(cfg, mob)) {
                    event.setSpawnCancelled(true);
                }
            });
            if (event.isSpawnCancelled()) break;
        }
    }

    private static boolean matchesSpawnCategory(NoMobSpawnsNearbyPower.Config cfg, Mob mob) {
        if (cfg.coversAll()) return true;
        MobCategory cat = mob.getType().getCategory();
        for (String s : cfg.categories()) {
            if ("monster".equalsIgnoreCase(s)        && cat == MobCategory.MONSTER)        return true;
            if ("creature".equalsIgnoreCase(s)       && cat == MobCategory.CREATURE)       return true;
            if ("ambient".equalsIgnoreCase(s)        && cat == MobCategory.AMBIENT)        return true;
            if ("water_creature".equalsIgnoreCase(s) && cat == MobCategory.WATER_CREATURE) return true;
            if ("water_ambient".equalsIgnoreCase(s)  && cat == MobCategory.WATER_AMBIENT)  return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        float scaled = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_NATURAL_REGEN, null, event.getAmount());
        if (scaled != event.getAmount()) {
            // Defence-in-depth clamp against non-finite results — see
            // CombatPowerEvents.onLivingDamage for the full story of
            // how an unclamped multiply can brick a save via NaN health.
            if (!Float.isFinite(scaled)) scaled = Float.MAX_VALUE;
            event.setAmount(scaled);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.BLOCK_BREAK, event);

        var state = event.getState();
        BlockPos pos = event.getPos().immutable();
        boolean isLog = state.is(BlockTags.LOGS) && !isStrippedBlock(state);
        boolean brokenLogWasPlayerPlaced = isLog
            && com.cyberday1.neoorigins.service.PlayerPlacedLogTracker.isPlaced(sl, pos);

        // CropHarvestBonusPower (GitHub #91 fix layered in here):
        //   - Stripped logs are excluded entirely. The bonus is intended for
        //     chopping naturally-grown wood, not for un-shrinking the same
        //     block you stripped seconds ago.
        //   - Player-placed logs are tracked per-chunk and excluded — a
        //     builder placing a log wall and breaking it back down should not
        //     dupe their materials. World-gen logs and sapling-grown trees
        //     never get marked (TreeFeature places via FeaturePlaceContext,
        //     not the Entity place path), so they still earn the bonus.
        if (ActiveOriginService.has(sp, CropHarvestBonusPower.class, c -> true)) {
            boolean isMatureCrop = state.getBlock() instanceof CropBlock cb && cb.isMaxAge(state);
            if (isMatureCrop || isLog) {
                if (isLog && brokenLogWasPlayerPlaced) {
                    // Player-placed log: skip the bonus. The marker is cleared
                    // once below after all log-related powers see the original state.
                } else {
                    ItemStack tool = sp.getMainHandItem().copy();
                    sl.getServer().execute(() -> {
                        java.util.List<ItemStack> drops = Block.getDrops(state, sl, pos, null, sp, tool);
                        ActiveOriginService.forEachOfType(sp, CropHarvestBonusPower.class, cfg -> {
                            for (ItemStack drop : drops) {
                                for (int i = 0; i < cfg.extraDrops(); i++) {
                                    Block.popResource(sl, pos, drop.copy());
                                }
                            }
                        });
                    });
                }
            }
        }

        if (brokenLogWasPlayerPlaced) {
            // The block is gone after this event, so the per-chunk marker should
            // not survive even when the player has no harvest/felling powers.
            com.cyberday1.neoorigins.service.PlayerPlacedLogTracker.clear(sl, pos);
        }

        // TreeFellingPower - fell natural trees, not arbitrary connected wood.
        if (isLog && !brokenLogWasPlayerPlaced && !sp.isShiftKeyDown()) {
            if (ActiveOriginService.has(sp, TreeFellingPower.class, c -> true)) {
                final int[] maxBlocks = {64};
                ActiveOriginService.forEachOfType(sp, TreeFellingPower.class, cfg ->
                    maxBlocks[0] = Math.max(maxBlocks[0], cfg.maxBlocks()));
                fellTree(sl, pos, state, maxBlocks[0]);
            }
        }
    }

    private static void fellTree(ServerLevel level, BlockPos origin, BlockState originState, int maxBlocks) {
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        int scanLimit = Math.max(maxBlocks * 4, 128);
        Set<BlockPos> connectedLogs = collectConnectedTreeLogs(level, origin, originState, scanLimit, parents);
        if (connectedLogs.isEmpty()) return;

        Set<BlockPos> naturalLeaves = collectNaturalLeaves(level, origin, connectedLogs);
        if (naturalLeaves.isEmpty()) return;

        Set<BlockPos> logsToBreak = new HashSet<>(connectedLogs);
        for (BlockPos leaf : naturalLeaves) {
            for (int dx = -BRANCH_LOG_SEARCH_RADIUS; dx <= BRANCH_LOG_SEARCH_RADIUS; dx++) {
                for (int dy = -BRANCH_LOG_SEARCH_RADIUS; dy <= BRANCH_LOG_SEARCH_RADIUS; dy++) {
                    for (int dz = -BRANCH_LOG_SEARCH_RADIUS; dz <= BRANCH_LOG_SEARCH_RADIUS; dz++) {
                        BlockPos candidate = leaf.offset(dx, dy, dz).immutable();
                        if (!isInsideTreeScan(origin, candidate) || !isSameNaturalTreeLog(level, candidate, originState)) {
                            continue;
                        }
                        if (connectedLogs.contains(candidate)) {
                            addPathToOrigin(candidate, origin, parents, logsToBreak);
                            continue;
                        }

                        Set<BlockPos> detachedBranch = collectBranchComponent(level, origin, originState, candidate);
                        if (isValidDetachedBranch(detachedBranch, connectedLogs)) {
                            logsToBreak.addAll(detachedBranch);
                        }
                    }
                }
            }
        }
        if (logsToBreak.isEmpty()) return;

        logsToBreak.remove(origin);
        List<BlockPos> ordered = new ArrayList<>(logsToBreak);
        ordered.sort(Comparator.comparingInt((BlockPos bp) -> bp.getY()).reversed());

        int broken = 0;
        for (BlockPos bp : ordered) {
            if (broken >= maxBlocks) break;
            if (!isSameNaturalTreeLog(level, bp, originState)) continue;
            level.destroyBlock(bp, true);
            broken++;
        }
    }

    private static Set<BlockPos> collectConnectedTreeLogs(
        ServerLevel level,
        BlockPos origin,
        BlockState originState,
        int maxScan,
        Map<BlockPos, BlockPos> parents
    ) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> logs = new HashSet<>();

        visited.add(origin);
        queue.add(origin);

        while (!queue.isEmpty() && logs.size() < maxScan) {
            BlockPos bp = queue.poll();
            boolean isOrigin = bp.equals(origin);
            if (!isInsideTreeScan(origin, bp) || (!isOrigin && !isSameNaturalTreeLog(level, bp, originState))) continue;
            logs.add(bp);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = bp.offset(dx, dy, dz).immutable();
                        if (visited.contains(neighbor)
                            || !isInsideTreeScan(origin, neighbor)
                            || !isSameNaturalTreeLog(level, neighbor, originState)) {
                            continue;
                        }
                        visited.add(neighbor);
                        parents.put(neighbor, bp);
                        queue.add(neighbor);
                    }
                }
            }
        }

        return logs;
    }

    private static Set<BlockPos> collectBranchComponent(
        ServerLevel level,
        BlockPos origin,
        BlockState originState,
        BlockPos start
    ) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> logs = new HashSet<>();

        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty() && logs.size() <= DETACHED_BRANCH_MAX_LOGS) {
            BlockPos bp = queue.poll();
            if (!isInsideTreeScan(origin, bp) || !isSameNaturalTreeLog(level, bp, originState)) continue;
            logs.add(bp);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = bp.offset(dx, dy, dz).immutable();
                        if (visited.add(neighbor)) queue.add(neighbor);
                    }
                }
            }
        }

        return logs;
    }

    private static boolean isValidDetachedBranch(Set<BlockPos> branch, Set<BlockPos> connectedLogs) {
        if (branch.isEmpty() || branch.size() > DETACHED_BRANCH_MAX_LOGS) return false;
        return maxVerticalRun(branch) <= DETACHED_BRANCH_MAX_VERTICAL_RUN;
    }

    private static int maxVerticalRun(Set<BlockPos> logs) {
        int max = 0;
        for (BlockPos start : logs) {
            if (logs.contains(start.below())) continue;
            int run = 0;
            BlockPos cursor = start;
            while (logs.contains(cursor)) {
                run++;
                cursor = cursor.above();
            }
            max = Math.max(max, run);
        }
        return max;
    }

    private static Set<BlockPos> collectNaturalLeaves(ServerLevel level, BlockPos origin, Set<BlockPos> logs) {
        Set<BlockPos> leaves = new HashSet<>();

        for (BlockPos log : logs) {
            for (int dx = -LEAF_SEARCH_RADIUS; dx <= LEAF_SEARCH_RADIUS; dx++) {
                for (int dy = -LEAF_SEARCH_RADIUS; dy <= LEAF_SEARCH_RADIUS; dy++) {
                    for (int dz = -LEAF_SEARCH_RADIUS; dz <= LEAF_SEARCH_RADIUS; dz++) {
                        BlockPos candidate = log.offset(dx, dy, dz).immutable();
                        if (!isInsideTreeScan(origin, candidate) || !isNaturalLeaf(level.getBlockState(candidate))) {
                            continue;
                        }
                        leaves.add(candidate);
                    }
                }
            }
        }

        return leaves;
    }

    private static void addPathToOrigin(
        BlockPos start,
        BlockPos origin,
        Map<BlockPos, BlockPos> parents,
        Set<BlockPos> logsToBreak
    ) {
        BlockPos cursor = start;
        while (cursor != null && !cursor.equals(origin)) {
            logsToBreak.add(cursor);
            cursor = parents.get(cursor);
        }
    }

    private static boolean isSameNaturalTreeLog(ServerLevel level, BlockPos pos, BlockState originState) {
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.LOGS)
            && !isStrippedBlock(state)
            && state.getBlock() == originState.getBlock()
            && !com.cyberday1.neoorigins.service.PlayerPlacedLogTracker.isPlaced(level, pos);
    }

    private static boolean isNaturalLeaf(BlockState state) {
        if (!state.is(BlockTags.LEAVES)) return false;

        for (var property : state.getProperties()) {
            if (property instanceof BooleanProperty boolProperty && "persistent".equals(property.getName())) {
                return !state.getValue(boolProperty);
            }
        }
        return false;
    }

    private static boolean isInsideTreeScan(BlockPos origin, BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) <= TREE_SCAN_HORIZONTAL_RADIUS
            && Math.abs(pos.getZ() - origin.getZ()) <= TREE_SCAN_HORIZONTAL_RADIUS
            && pos.getY() >= origin.getY() - TREE_SCAN_DOWN
            && pos.getY() <= origin.getY() + TREE_SCAN_UP;
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.BLOCK_PLACE, event);
    }

    @SubscribeEvent
    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer sp)) return;
        if (!ActiveOriginService.has(sp, TwinBreedingPower.class, c -> true)) return;

        AgeableMob child = event.getChild();
        if (child == null) return;

        ActiveOriginService.forEachOfType(sp, TwinBreedingPower.class, cfg -> {
            if (sp.getRandom().nextFloat() < cfg.chance()) {
                var twin = (AgeableMob) child.getType().create(child.level());
                if (twin != null) {
                    twin.setBaby(true);
                    twin.setPos(child.getX(), child.getY(), child.getZ());
                    child.level().addFreshEntity(twin);
                }
            }
        });
    }

    /**
     * Marks logs placed by a player in the per-chunk PlacedLogs attachment so
     * {@link CropHarvestBonusPower} can exclude them from the harvest bonus
     * later (GitHub #91). Sapling-grown trees and world-gen logs flow through
     * {@code TreeFeature}/{@code FeaturePlaceContext} which doesn't fire
     * {@code EntityPlaceEvent}, so they're never marked and still earn the
     * bonus as expected.
     */
    @SubscribeEvent
    public static void onPlayerPlaceLog(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player)) return;
        if (event.getLevel().isClientSide()) return;
        BlockState placed = event.getPlacedBlock();
        if (!placed.is(BlockTags.LOGS) || isStrippedBlock(placed)) return;
        // EntityPlaceEvent's level is a LevelAccessor on both branches; cast
        // is safe at this dispatch site (player placement always happens on
        // a real Level, never a ProtoChunk).
        if (!(event.getLevel() instanceof net.minecraft.world.level.Level level)) return;
        com.cyberday1.neoorigins.service.PlayerPlacedLogTracker.markPlaced(level, event.getPos().immutable());
    }

    /** True if the block's registry id starts with {@code stripped_} — covers
     *  every vanilla stripped-log variant and most mods that follow the same
     *  naming convention. Cheap registry lookup, no allocation. */
    private static boolean isStrippedBlock(BlockState state) {
        if (state.is(Tags.Blocks.STRIPPED_LOGS) || state.is(Tags.Blocks.STRIPPED_WOODS)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getPath().startsWith("stripped_");
    }
}
