package com.cyberday1.neoorigins.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla {@code Player.tick()} resets {@code noPhysics = this.isSpectator()}.
 * Keep true only for powers that explicitly request full noclip; block phasing
 * is handled by collision-shape mixins.
 */
@Mixin(Player.class)
public abstract class PlayerPhaseOverrideMixin {

    @Inject(
        method = "tick",
        at = @At(
            value  = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
            opcode = Opcodes.PUTFIELD,
            shift  = At.Shift.AFTER
        )
    )
    private void neoorigins$restoreFullNoPhysics(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.noPhysics) return;

        if (self instanceof ServerPlayer player) {
            self.noPhysics = com.cyberday1.neoorigins.service.ActiveOriginService.hasCapability(player, "no_physics");
        } else if (self.level().isClientSide) {
            self.noPhysics = com.cyberday1.neoorigins.client.ClientActivePowers.hasCapability("no_physics");
        }
    }
}
