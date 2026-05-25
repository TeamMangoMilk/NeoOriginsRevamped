package com.cyberday1.neoorigins.client;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public class NeoOriginsConfigSectionScreen extends ConfigurationScreen.ConfigurationSectionScreen {

    private static final String NATURAL_GLIDE_SOUND_KEY = "natural_glide_elytra_sound";

    public NeoOriginsConfigSectionScreen(ConfigurationScreen parent, ModConfig.Type type,
                                         ModConfig modConfig, Component title) {
        super(parent, type, modConfig, title);
    }

    @Override
    protected void onChanged(String key) {
        super.onChanged(key);
        if (NATURAL_GLIDE_SOUND_KEY.equals(key)) {
            NeoOriginsElytraSoundController.refreshCurrentPlayer();
        }
    }
}
