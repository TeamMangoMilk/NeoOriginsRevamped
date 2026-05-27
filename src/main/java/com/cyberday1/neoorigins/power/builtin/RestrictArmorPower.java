package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Prevents certain items from being equipped in certain slots. When a matching
 * item is put into a restricted slot, the power ejects it back to the player's
 * inventory (or drops it to the ground if inventory is full).
 */
public class RestrictArmorPower extends PowerType<RestrictArmorPower.Config> {

    public record SlotRestriction(
        String slot,
        Optional<ResourceLocation> item,
        Optional<ResourceLocation> tag
    ) {
        public static final Codec<SlotRestriction> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("slot").forGetter(SlotRestriction::slot),
            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(SlotRestriction::item),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(SlotRestriction::tag)
        ).apply(inst, SlotRestriction::new));
    }

    public record Config(java.util.List<SlotRestriction> restrictions, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            SlotRestriction.CODEC.listOf().optionalFieldOf("restrictions", java.util.List.of())
                .forGetter(Config::restrictions),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    public static boolean isRestricted(ServerPlayer player, ItemStack stack, EquipmentSlot slot, Config config) {
        if (stack.isEmpty()) return false;
        if (exceedsTotalArmorCap(player, stack, slot, config)) return true;
        return isRestricted(stack, slot, config);
    }

    public static boolean isRestricted(ItemStack stack, EquipmentSlot slot, Config config) {
        if (stack.isEmpty()) return false;
        String slotName = slot.getName();
        for (SlotRestriction r : config.restrictions()) {
            if (!r.slot().equalsIgnoreCase(slotName)) continue;
            if (r.item().isPresent()) {
                Item item = BuiltInRegistries.ITEM.get(r.item().get());
                if (stack.is(item)) return true;
            }
            if (r.tag().isPresent()) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, r.tag().get());
                if (stack.is(tag)) return true;
                // Also check config-defined armor class extensions
                if (matchesConfigArmorClass(stack, slot, r.tag().get())) return true;
            }
            if (r.item().isEmpty() && r.tag().isEmpty()) return true;
        }
        return false;
    }

    private static boolean exceedsTotalArmorCap(ServerPlayer player, ItemStack candidate, EquipmentSlot candidateSlot, Config config) {
        if (!hasHeavyArmorRestriction(config)) return false;
        double maxArmor = NeoOriginsConfig.maxEquippedArmorPoints();
        if (maxArmor < 0) return false;

        double total = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            ItemStack stack = slot == candidateSlot ? candidate : player.getItemBySlot(slot);
            total += armorPoints(stack, slot);
        }
        return total > maxArmor;
    }

    private static boolean hasHeavyArmorRestriction(Config config) {
        ResourceLocation heavyArmor = ResourceLocation.fromNamespaceAndPath("neoorigins", "heavy_armor");
        for (SlotRestriction restriction : config.restrictions()) {
            if (restriction.tag().isPresent() && heavyArmor.equals(restriction.tag().get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the stack matches any item in the config-defined armor class
     * list that extends the given tag. Only applies to neoorigins:heavy_armor
     * and neoorigins:light_armor tags.
     */
    private static boolean matchesConfigArmorClass(ItemStack stack, EquipmentSlot slot, ResourceLocation tagId) {
        List<String> configItems;
        if (tagId.equals(ResourceLocation.fromNamespaceAndPath("neoorigins", "heavy_armor"))) {
            configItems = NeoOriginsConfig.getHeavyArmorItems();
            double minArmor = NeoOriginsConfig.heavyArmorMinArmorPoints();
            if (minArmor >= 0 && armorPoints(stack, slot) >= minArmor) {
                return true;
            }
        } else if (tagId.equals(ResourceLocation.fromNamespaceAndPath("neoorigins", "light_armor"))) {
            configItems = NeoOriginsConfig.getLightArmorItems();
        } else {
            return false;
        }

        ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String entry : configItems) {
            if (entry.startsWith("#")) {
                // Tag reference
                TagKey<Item> extraTag = TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.substring(1)));
                if (stack.is(extraTag)) return true;
            } else {
                // Item ID
                if (stackId.equals(ResourceLocation.parse(entry))) return true;
            }
        }
        return false;
    }

    private static double armorPoints(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 0;
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getDefense();
        }

        double armor = 0;
        for (var entry : stack.getAttributeModifiers().modifiers()) {
            if (!entry.attribute().equals(Attributes.ARMOR) || !entry.slot().test(slot)) continue;
            AttributeModifier modifier = entry.modifier();
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                armor += modifier.amount();
            }
        }
        return armor;
    }
}
