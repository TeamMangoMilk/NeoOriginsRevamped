package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Original-Phantom-style toggle: while active the player becomes a spectator-lite
 * phantom form, with configurable hunger gating and unphasable blocks.
 */
public class PhantomFormPower extends AbstractTogglePower<PhantomFormPower.Config> {

    private static final Set<String> CAPS = Set.of("no_physics");
    private static final Map<List<String>, Set<ResourceLocation>> BLOCKED_CACHE = new ConcurrentHashMap<>();

    @Override
    public Set<String> capabilities(Config config) { return CAPS; }

    public record Config(
        boolean invisibility,
        boolean noGravity,
        boolean defaultOff,
        int minFoodLevel,
        List<String> blockedBlocks,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("invisibility", true).forGetter(Config::invisibility),
            Codec.BOOL.optionalFieldOf("no_gravity", true).forGetter(Config::noGravity),
            Codec.BOOL.optionalFieldOf("default_off", false).forGetter(Config::defaultOff),
            Codec.INT.optionalFieldOf("min_food_level", 0).forGetter(Config::minFoodLevel),
            Codec.STRING.listOf().optionalFieldOf("blocked_blocks",
                List.of("minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock"))
                .forGetter(Config::blockedBlocks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean defaultToggledOff(Config config) {
        return config.defaultOff();
    }

    @Override
    protected boolean canToggleOn(ServerPlayer player, Config config) {
        return player.getFoodData().getFoodLevel() > config.minFoodLevel();
    }

    @Override
    protected void onToggleOnRejected(ServerPlayer player, Config config) {
        player.sendSystemMessage(Component.translatable("neoorigins.toggle.no_food")
            .withStyle(ChatFormatting.RED));
    }

    @Override
    protected void tickEffect(ServerPlayer player, Config config) {
        if (player.getFoodData().getFoodLevel() <= config.minFoodLevel()) {
            player.getData(OriginAttachments.originData()).setPowerToggledOff(getToggleKey(config), true);
            removeEffect(player, config);
            player.sendSystemMessage(Component.translatable("neoorigins.toggle.no_food")
                .withStyle(ChatFormatting.RED));
            return;
        }

        if (config.invisibility()) {
            var existing = player.getEffect(MobEffects.INVISIBILITY);
            if (existing == null || existing.getDuration() < 210) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 300, 0, true, false));
            }
        }
        if (config.noGravity()) {
            player.setNoGravity(true);
        }

        var abilities = player.getAbilities();
        boolean abilitiesChanged = false;
        if (!abilities.mayfly)  { abilities.mayfly  = true;  abilitiesChanged = true; }
        if (!abilities.flying)  { abilities.flying  = true;  abilitiesChanged = true; }
        if (abilitiesChanged) player.onUpdateAbilities();

        player.noPhysics = !isInsideBlockedBlock(player, config);
        player.fallDistance = 0.0F;
    }

    @Override
    protected void removeEffect(ServerPlayer player, Config config) {
        if (config.invisibility()) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        player.setNoGravity(false);
        player.noPhysics = false;
        var abilities = player.getAbilities();
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        player.onUpdateAbilities();
        player.fallDistance = 0.0F;
    }

    private static boolean isInsideBlockedBlock(ServerPlayer player, Config config) {
        Set<ResourceLocation> blocked = BLOCKED_CACHE.computeIfAbsent(config.blockedBlocks(),
            list -> list.stream().map(ResourceLocation::parse).collect(Collectors.toUnmodifiableSet()));
        if (blocked.isEmpty()) return false;

        AABB box = player.getBoundingBox().deflate(0.05);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = player.level().getBlockState(pos);
            if (state.isAir()) continue;
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blocked.contains(blockId)) return true;
        }
        return false;
    }
}
