package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(net.minecraft.client.renderer.ScreenEffectRenderer.class)
public abstract class ScreenEffectPhasingMixin {

    @Redirect(
        method = "getOverlayBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isViewBlocking(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private static boolean neoorigins$doNotOverlayPhasedBlocks(BlockState state, BlockGetter level, BlockPos pos) {
        boolean phasing = ClientActivePowers.hasCapability("phantom_phase")
            || ClientActivePowers.hasCapability("wall_phase");
        if (phasing && !neoorigins$isDefaultBlocked(state)) {
            return false;
        }
        return state.isViewBlocking(level, pos);
    }

    private static boolean neoorigins$isDefaultBlocked(BlockState state) {
        return state.is(Blocks.BEDROCK)
            || state.is(Blocks.OBSIDIAN)
            || state.is(Blocks.CRYING_OBSIDIAN);
    }
}
