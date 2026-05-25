package com.cyberday1.neoorigins.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only preferences. Stored at config/neoorigins-client.toml and never
 * synced from the server.
 */
public final class NeoOriginsClientConfig {

    private NeoOriginsClientConfig() {}

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue NATURAL_GLIDE_ELYTRA_SOUND =
        BUILDER
            .comment("Play the vanilla elytra flying loop while gliding without a real elytra.",
                     "Default true keeps the pre-2.1 natural glide audio for origins like Phantom, Elytrian, and Hiveling.",
                     "Set false if you prefer v2.1's quieter no-elytra glide behavior.")
            .translation("neoorigins.configuration.natural_glide_elytra_sound")
            .define("natural_glide_elytra_sound", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean naturalGlideElytraSound() {
        return NATURAL_GLIDE_ELYTRA_SOUND.getRaw();
    }
}
