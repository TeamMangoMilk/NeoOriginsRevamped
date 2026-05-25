package com.cyberday1.neoorigins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.ElytraOnPlayerSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/**
 * Applies the client-side natural-glide sound preference without affecting
 * real elytra users or other players' flight sounds.
 */
public final class NeoOriginsElytraSoundController {

    private static ElytraOnPlayerSoundInstance naturalGlideSound;
    private static boolean wasEnabled = true;

    private NeoOriginsElytraSoundController() {}

    public static void handleFallFlyingSound(LocalPlayer player, SoundManager soundManager, SoundInstance sound) {
        if (sound instanceof ElytraOnPlayerSoundInstance elytraSound && isNoElytraFallFlying(player)) {
            if (!NeoOriginsClientConfig.naturalGlideElytraSound()) {
                naturalGlideSound = null;
                wasEnabled = false;
                return;
            }
            naturalGlideSound = elytraSound;
        }
        soundManager.play(sound);
    }

    public static void tick(LocalPlayer player) {
        boolean enabled = NeoOriginsClientConfig.naturalGlideElytraSound();
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();

        if (naturalGlideSound != null && !soundManager.isActive(naturalGlideSound)) {
            naturalGlideSound = null;
        }

        if (!isNoElytraFallFlying(player)) {
            naturalGlideSound = null;
            wasEnabled = enabled;
            return;
        }

        if (!enabled) {
            if (naturalGlideSound != null) {
                soundManager.stop(naturalGlideSound);
                naturalGlideSound = null;
            }
        } else if (!wasEnabled && naturalGlideSound == null) {
            naturalGlideSound = new ElytraOnPlayerSoundInstance(player);
            soundManager.play(naturalGlideSound);
        }

        wasEnabled = enabled;
    }

    public static void refreshCurrentPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            tick(player);
        }
    }

    private static boolean isNoElytraFallFlying(LocalPlayer player) {
        return player.isFallFlying()
            && !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }
}
