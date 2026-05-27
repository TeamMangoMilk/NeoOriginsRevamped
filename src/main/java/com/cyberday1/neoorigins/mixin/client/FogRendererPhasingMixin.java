package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.VisualEffectsHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererPhasingMixin {

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private static void neoorigins$applyPhasingFog(
        Camera camera,
        FogRenderer.FogMode fogMode,
        float farPlaneDistance,
        boolean shouldCreateFog,
        float partialTick,
        CallbackInfo ci
    ) {
        if (VisualEffectsHandler.applyPhasingFog(camera, fogMode)) {
            ci.cancel();
        }
    }
}
