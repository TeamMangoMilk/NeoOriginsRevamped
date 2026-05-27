package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.service.PhasingCollisionService;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerPhasingPoseMixin {

    @Redirect(
        method = "updatePlayerPose",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isShiftKeyDown()Z"
        )
    )
    private boolean neoorigins$doNotCrouchForPhasing(Player player) {
        return !PhasingCollisionService.hasBlockPhase(player) && player.isShiftKeyDown();
    }

    @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
    private void neoorigins$allowPhasingDownFromEdges(Vec3 vec, MoverType mover, CallbackInfoReturnable<Vec3> cir) {
        Player self = (Player) (Object) this;
        if (PhasingCollisionService.hasBlockPhase(self) && self.isShiftKeyDown()) {
            cir.setReturnValue(vec);
        }
    }
}
