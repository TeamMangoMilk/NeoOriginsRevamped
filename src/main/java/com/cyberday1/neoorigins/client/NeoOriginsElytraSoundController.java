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
    private static int pendingSoundStartTick = -1;
    private static final int SOUND_START_DELAY_TICKS = 6;

    private NeoOriginsElytraSoundController() {}

    public static void handleFallFlyingSound(LocalPlayer player, SoundManager soundManager, SoundInstance sound) {
        if (sound instanceof ElytraOnPlayerSoundInstance elytraSound && isNoElytraFallFlying(player)) {
            if (!NeoOriginsClientConfig.naturalGlideElytraSound()) {
                pendingSoundStartTick = -1;
                naturalGlideSound = null;
                wasEnabled = false;
                return;
            }
            if (isStableNaturalGlide(player)) {
                naturalGlideSound = elytraSound;
                soundManager.play(sound);
            } else {
                pendingSoundStartTick = player.tickCount;
            }
            return;
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
            pendingSoundStartTick = -1;
            naturalGlideSound = null;
            wasEnabled = enabled;
            return;
        }

        if (!enabled) {
            pendingSoundStartTick = -1;
            if (naturalGlideSound != null) {
                soundManager.stop(naturalGlideSound);
                naturalGlideSound = null;
            }
        } else if (naturalGlideSound == null) {
            if (pendingSoundStartTick < 0) {
                pendingSoundStartTick = player.tickCount;
            }
            if (isStableNaturalGlide(player)
                    && player.tickCount - pendingSoundStartTick >= SOUND_START_DELAY_TICKS) {
                naturalGlideSound = new ElytraOnPlayerSoundInstance(player);
                soundManager.play(naturalGlideSound);
                pendingSoundStartTick = -1;
            }
        } else if (!wasEnabled) {
            soundManager.stop(naturalGlideSound);
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

    private static boolean isStableNaturalGlide(LocalPlayer player) {
        return !player.onGround()
            && !player.isInWater()
            && !player.isPassenger()
            && player.getDeltaMovement().y < -0.01D
            && player.getFallFlyingTicks() >= SOUND_START_DELAY_TICKS;
    }
}
