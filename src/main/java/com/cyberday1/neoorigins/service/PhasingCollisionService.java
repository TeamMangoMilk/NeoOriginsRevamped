package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.power.builtin.PhantomFormPower;
import com.cyberday1.neoorigins.power.builtin.WraithPhasePower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PhasingCollisionService {

    private static final Map<List<String>, Set<ResourceLocation>> BLOCKED_CACHE = new ConcurrentHashMap<>();

    private PhasingCollisionService() {}

    public static boolean shouldPhase(Entity entity, VoxelShape shape, BlockPos pos) {
        if (shape.isEmpty() || !doesApply(entity, pos)) {
            return false;
        }
        double top = pos.getY() + shape.max(Direction.Axis.Y);
        double standingOffset = entity.onGround() ? 8.05 / 16.0 : 0.0015;
        return entity.getY() < top - standingOffset || shouldPhaseDown(entity);
    }

    public static boolean doesApply(Entity entity, BlockPos pos) {
        if (entity instanceof ServerPlayer player) {
            return serverDoesApply(player, pos);
        }
        if (entity.level().isClientSide()) {
            return clientDoesApply(entity, pos);
        }
        return false;
    }

    public static boolean hasBlockPhase(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return ActiveOriginService.hasCapability(player, "phantom_phase")
                || ActiveOriginService.hasCapability(player, "wall_phase");
        }
        if (entity.level().isClientSide()) {
            return com.cyberday1.neoorigins.client.ClientActivePowers.hasCapability("phantom_phase")
                || com.cyberday1.neoorigins.client.ClientActivePowers.hasCapability("wall_phase");
        }
        return false;
    }

    public static boolean areFeetInsidePhasableSolid(Entity entity) {
        if (!hasBlockPhase(entity)) {
            return false;
        }

        AABB box = entity.getBoundingBox().deflate(0.1);
        double minY = entity.getY() + 0.05;
        double maxY = Math.min(entity.getY() + 0.45, box.maxY);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, minY, box.minZ),
                BlockPos.containing(box.maxX, maxY, box.maxZ))) {
            BlockState state = entity.level().getBlockState(pos);
            if (state.isAir() || !doesApply(entity, pos)) {
                continue;
            }
            if (!state.getCollisionShape(entity.level(), pos, CollisionContext.empty()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldPhaseDown(Entity entity) {
        return entity.isShiftKeyDown() && entity.onGround();
    }

    private static boolean serverDoesApply(ServerPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        for (PowerHolder<?> holder : ActiveOriginService.allPowers(player)) {
            if (!holder.isConditionSatisfied(player)) continue;
            if (holder.type() instanceof PhantomFormPower power
                && !power.isToggledOff(player, (PhantomFormPower.Config) holder.config())
                && !isBlocked(state, ((PhantomFormPower.Config) holder.config()).blockedBlocks())) {
                return true;
            }
            if (holder.type() instanceof WraithPhasePower power
                && !power.isToggledOff(player, (WraithPhasePower.Config) holder.config())
                && !isBlocked(state, ((WraithPhasePower.Config) holder.config()).blockedBlocks())) {
                return true;
            }
        }
        return false;
    }

    private static boolean clientDoesApply(Entity entity, BlockPos pos) {
        boolean hasPhase = com.cyberday1.neoorigins.client.ClientActivePowers.hasCapability("phantom_phase")
            || com.cyberday1.neoorigins.client.ClientActivePowers.hasCapability("wall_phase");
        return hasPhase && !isDefaultBlocked(entity.level().getBlockState(pos));
    }

    private static boolean isBlocked(BlockState state, List<String> blockedBlocks) {
        if (state.isAir() || blockedBlocks.isEmpty()) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return BLOCKED_CACHE.computeIfAbsent(blockedBlocks,
            list -> list.stream().map(ResourceLocation::parse).collect(Collectors.toUnmodifiableSet()))
            .contains(blockId);
    }

    private static boolean isDefaultBlocked(BlockState state) {
        return state.is(Blocks.BEDROCK)
            || state.is(Blocks.OBSIDIAN)
            || state.is(Blocks.CRYING_OBSIDIAN);
    }
}
