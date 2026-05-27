package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

/**
 * Wraith Phase -- toggleable spectral phasing.
 *
 * <p>When active the player walks through solid blocks horizontally.
 * While inside a solid block, flight kicks in (jump = up, shift = down).
 * On the surface the player walks on the ground normally.
 *
 * <p>Certain blocks (obsidian, bedrock by default) cannot be phased through.
 * Phasing drains hunger.
 */
public class WraithPhasePower extends AbstractTogglePower<WraithPhasePower.Config> {

    private static final Set<String> CAPS = Set.of("wall_phase");

    @Override
    public Set<String> capabilities(Config config) { return CAPS; }

    public record Config(
        List<String> blockedBlocks,
        float exhaustionPerTick,
        boolean alwaysOn,
        boolean fogEnabled,
        double viewDistance,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("blocked_blocks", List.of("minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock"))
                .forGetter(Config::blockedBlocks),
            Codec.FLOAT.optionalFieldOf("exhaustion_per_tick", 0.15F).forGetter(Config::exhaustionPerTick),
            Codec.BOOL.optionalFieldOf("always_on", false).forGetter(Config::alwaysOn),
            Codec.BOOL.optionalFieldOf("fog_enabled", true).forGetter(Config::fogEnabled),
            Codec.DOUBLE.optionalFieldOf("fog_distance", 50.0).forGetter(Config::viewDistance),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Set<String> capabilities(ServerPlayer player, Config config) {
        return config.fogEnabled()
            ? Set.of("wall_phase", "phasing_visual:" + config.viewDistance())
            : CAPS;
    }

    /** When always_on, the power is passive — no skill key slot. */
    @Override
    public boolean isActivePower(Config config) { return !config.alwaysOn(); }

    /** When always_on, never report as toggled off. */
    @Override
    public boolean isToggledOff(ServerPlayer player, Config config) {
        if (config.alwaysOn()) return false;
        return super.isToggledOff(player, config);
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected void tickEffect(ServerPlayer player, Config config) {
        boolean insideSolid = isInsideSolid(player);

        var abilities = player.getAbilities();
        if ((abilities.mayfly || abilities.flying) && !player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
            player.onUpdateAbilities();
        }

        // Hunger drain while phasing through solid blocks
        if (insideSolid) {
            player.setSprinting(false);
            player.causeFoodExhaustion(config.exhaustionPerTick());
        }

        player.fallDistance = 0.0F;
    }

    @Override
    protected void removeEffect(ServerPlayer player, Config config) {
        if (!player.isSpectator()) {
            player.noPhysics = false;
        }
        var abilities = player.getAbilities();
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        // Always sync — even if flying was already false, mayfly may still
        // be true on the client from a previous in-block sync. Without this
        // the client keeps mayfly=true and can double-tap jump to fly.
        player.onUpdateAbilities();
        player.fallDistance = 0.0F;
    }

    /**
     * Returns true if any block overlapping the player's hitbox is solid
     * (non-air with a collision shape). Used to detect active phasing.
     */
    private static boolean isInsideSolid(ServerPlayer player) {
        AABB box = player.getBoundingBox().deflate(0.1);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY + 0.1, box.minZ),
                BlockPos.containing(box.maxX, box.maxY - 0.1, box.maxZ))) {
            BlockState state = player.level().getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(player.level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
