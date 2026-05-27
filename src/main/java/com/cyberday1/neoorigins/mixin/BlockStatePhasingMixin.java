package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.service.PhasingCollisionService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStatePhasingMixin {

    @Inject(
        method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void neoorigins$phaseThroughBlocks(BlockGetter level, BlockPos pos, CollisionContext context,
                                               CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape original = cir.getReturnValue();
        if (context == CollisionContext.empty() || original.isEmpty()) {
            return;
        }
        if (context instanceof EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity != null && PhasingCollisionService.shouldPhase(entity, original, pos)) {
                cir.setReturnValue(Shapes.empty());
            }
        }
    }

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void neoorigins$skipEntityInsideWhilePhasing(Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (PhasingCollisionService.doesApply(entity, pos)) {
            ci.cancel();
        }
    }
}
