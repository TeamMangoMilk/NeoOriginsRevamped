package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.cyberday1.neoorigins.service.PhasingCollisionService;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPhasingControlsMixin {

    private static final double PHASING_VERTICAL_SPEED = 0.2;
    private static final int PHASING_STEP_REPEAT_DELAY_TICKS = 8;
    @Unique
    private boolean neoorigins$wasPhasingJumpPressed;
    @Unique
    private double neoorigins$phaseUpTargetY = Double.NaN;
    @Unique
    private int neoorigins$phaseUpHeldTicks;

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void neoorigins$applyPhasingVerticalInput(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!neoorigins$hasBlockPhase(player) || player.isPassenger() || player.isFallFlying()) {
            neoorigins$wasPhasingJumpPressed = false;
            neoorigins$phaseUpTargetY = Double.NaN;
            neoorigins$phaseUpHeldTicks = 0;
            return;
        }

        boolean insideSolid = PhasingCollisionService.areFeetInsidePhasableSolid(player);
        if (player.input.jumping) {
            neoorigins$phaseUpHeldTicks++;
        } else {
            neoorigins$phaseUpHeldTicks = 0;
        }

        boolean phaseDown = player.input.shiftKeyDown && (insideSolid || player.onGround());
        if (phaseDown) {
            neoorigins$phaseUpTargetY = Double.NaN;
        }
        boolean startedPhaseUp = player.input.jumping
            && insideSolid
            && (Double.isNaN(neoorigins$phaseUpTargetY))
            && (!neoorigins$wasPhasingJumpPressed
                || neoorigins$phaseUpHeldTicks >= PHASING_STEP_REPEAT_DELAY_TICKS);
        if (startedPhaseUp) {
            neoorigins$phaseUpTargetY = Math.floor(player.getY()) + 1.0D;
        }
        neoorigins$wasPhasingJumpPressed = player.input.jumping;
        boolean phaseUp = !Double.isNaN(neoorigins$phaseUpTargetY);

        if (phaseUp && player.getY() >= neoorigins$phaseUpTargetY - 0.02D) {
            Vec3 velocity = player.getDeltaMovement();
            if (velocity.y > 0.0D) {
                player.setDeltaMovement(velocity.x, 0.0D, velocity.z);
            }
            player.fallDistance = 0.0F;
            neoorigins$phaseUpTargetY = Double.NaN;
            return;
        }

        if (!phaseDown && !phaseUp) {
            if (insideSolid && player.input.jumping) {
                Vec3 velocity = player.getDeltaMovement();
                if (velocity.y > 0.0D) {
                    player.setDeltaMovement(velocity.x, 0.0D, velocity.z);
                    player.fallDistance = 0.0F;
                }
            }
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        double y = phaseUp ? PHASING_VERTICAL_SPEED : -PHASING_VERTICAL_SPEED;
        player.setDeltaMovement(velocity.x, y, velocity.z);
        player.fallDistance = 0.0F;
    }

    @Unique
    private static boolean neoorigins$hasBlockPhase(LocalPlayer player) {
        return ClientActivePowers.hasCapability("phantom_phase")
            || ClientActivePowers.hasCapability("wall_phase");
    }

    @Unique
    private static boolean neoorigins$isInsidePhasableSolid(LocalPlayer player) {
        AABB box = player.getBoundingBox().deflate(0.1);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY + 0.1, box.minZ),
                BlockPos.containing(box.maxX, box.maxY - 0.1, box.maxZ))) {
            BlockState state = player.level().getBlockState(pos);
            if (!state.isAir()
                && !neoorigins$isDefaultBlocked(state)
                && !state.getCollisionShape(player.level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean neoorigins$isDefaultBlocked(BlockState state) {
        return state.is(Blocks.BEDROCK)
            || state.is(Blocks.OBSIDIAN)
            || state.is(Blocks.CRYING_OBSIDIAN);
    }
}
