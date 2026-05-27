package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.service.PhasingCollisionService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityPhasingMixin {

    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void neoorigins$usePhasingCollisionForSuffocation(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.noPhysics) {
            return;
        }

        float width = self.getBbWidth() * 0.8F;
        AABB eyeBox = AABB.ofSize(self.getEyePosition(), width, 1.0E-6, width);
        if (BlockPos.betweenClosedStream(eyeBox).noneMatch(pos -> PhasingCollisionService.doesApply(self, pos))) {
            return;
        }

        boolean inWall = BlockPos.betweenClosedStream(eyeBox).anyMatch(pos -> {
            BlockState state = self.level().getBlockState(pos);
            return !state.isAir()
                && state.isSuffocating(self.level(), pos)
                && Shapes.joinIsNotEmpty(
                    state.getCollisionShape(self.level(), pos, CollisionContext.of(self))
                        .move(pos.getX(), pos.getY(), pos.getZ()),
                    Shapes.create(eyeBox),
                    BooleanOp.AND
                );
        });
        cir.setReturnValue(inWall);
    }
}
