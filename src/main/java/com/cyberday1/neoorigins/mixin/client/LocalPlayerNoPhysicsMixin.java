package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raw client-side noclip for powers that explicitly request full no-physics
 * movement. Block phasing uses collision-shape mixins instead.
 */
@Mixin(net.minecraft.client.player.LocalPlayer.class)
public abstract class LocalPlayerNoPhysicsMixin {

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void neoorigins$moveWithNoPhysics(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (!ClientActivePowers.hasCapability("no_physics")) {
            return;
        }

        Entity self = (Entity) (Object) this;
        self.setPos(self.getX() + movement.x, self.getY() + movement.y, self.getZ() + movement.z);
        self.horizontalCollision = false;
        self.minorHorizontalCollision = false;
        self.verticalCollision = false;
        self.verticalCollisionBelow = false;
        self.setDeltaMovement(movement);
        self.fallDistance = 0.0F;
        ci.cancel();
    }
}
