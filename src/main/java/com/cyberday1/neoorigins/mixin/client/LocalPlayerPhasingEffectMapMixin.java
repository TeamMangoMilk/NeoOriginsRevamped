package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.VisualEffectsHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LocalPlayerPhasingEffectMapMixin {

    private static final MobEffectInstance NEOORIGINS_FAKE_BLINDNESS =
        new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, false);

    @Inject(method = "getActiveEffectsMap", at = @At("RETURN"), cancellable = true)
    private void neoorigins$exposePhasingAsBlindnessForFogConsumers(
        CallbackInfoReturnable<Map<Holder<MobEffect>, MobEffectInstance>> cir
    ) {
        if (!((Object) this instanceof LocalPlayer) || !VisualEffectsHandler.hasPhasingFog()) {
            return;
        }

        Map<Holder<MobEffect>, MobEffectInstance> effects = cir.getReturnValue();
        if (effects.containsKey(MobEffects.BLINDNESS)) {
            return;
        }

        Map<Holder<MobEffect>, MobEffectInstance> copy = new HashMap<>(effects);
        copy.put(MobEffects.BLINDNESS, NEOORIGINS_FAKE_BLINDNESS);
        cir.setReturnValue(copy);
    }
}
