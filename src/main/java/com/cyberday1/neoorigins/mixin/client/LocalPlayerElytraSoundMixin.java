package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.NeoOriginsElytraSoundController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.ElytraOnPlayerSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Routes vanilla's {@code item.elytra.flying} wind sound when a player
 * enters fall-flying <em>without an actual Elytra equipped</em> — i.e., the
 * gliding is being driven by a NeoOrigins flight power like
 * {@code natural_glide} (via {@code LocalPlayerNaturalGlideMixin}) or by the
 * {@code AirJumpPayload} server hook calling {@code startFallFlying()}.
 *
 * <p>Vanilla emission site is
 * {@code LocalPlayer.onSyncedDataUpdated(EntityDataAccessor)}: when the
 * shared {@code DATA_SHARED_FLAGS_ID} flips and {@code isFallFlying() &&
 * !wasFallFlying}, vanilla unconditionally calls
 * {@code getSoundManager().play(new ElytraOnPlayerSoundInstance(this))} —
 * never checking the chest slot. Vanilla assumed fall-flying always implies
 * a real elytra; the mod's powers legitimately break that assumption, so the
 * sound is controlled here.
 *
 * <p>{@link Redirect} the single {@code SoundManager.play(SoundInstance)} call
 * in that method and delegate the no-elytra case to a client-side preference.
 * Real-elytra users are unaffected.
 *
 * <p>Client-side only — listed in the {@code client} array of
 * {@code neoorigins.mixins.json} so it never loads on a dedicated server.
 * {@code require = 0} mirrors {@code LocalPlayerNaturalGlideMixin}'s cautious
 * stance: if a future MC remap moves the call site, the mixin degrades
 * silently to vanilla behavior rather than failing the boot.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerElytraSoundMixin {

    @Redirect(
        method = "onSyncedDataUpdated",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"),
        require = 0
    )
    private void neoorigins$skipElytraSoundIfNoElytra(SoundManager mgr, SoundInstance sound) {
        if (sound instanceof ElytraOnPlayerSoundInstance) {
            NeoOriginsElytraSoundController.handleFallFlyingSound((LocalPlayer) (Object) this, mgr, sound);
            return;
        }
        mgr.play(sound);
    }
}
