package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientPlayerVisuals;
import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererModelColorMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow protected M model;
    @Unique private T neoorigins$renderingEntity;

    @Shadow
    protected abstract RenderType getRenderType(T livingEntity, boolean bodyVisible, boolean translucent, boolean glowing);

    @Unique
    @SuppressWarnings("unchecked")
    private ResourceLocation neoorigins$getTextureLocation(T entity) {
        return ((EntityRenderer<T>) (Object) this).getTextureLocation(entity);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void neoorigins$captureEntity(T entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        neoorigins$renderingEntity = entity;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void neoorigins$clearEntity(T entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        neoorigins$renderingEntity = null;
    }

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"
        )
    )
    private RenderType neoorigins$useModelColorRenderType(
            LivingEntityRenderer<T, M> renderer,
            T entity,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing) {
        ClientPlayerVisuals.ModelColor color = neoorigins$modelColor(entity);
        if (color != null) {
            ResourceLocation texture = neoorigins$getTextureLocation(entity);
            return color.alpha() < 1.0F
                ? RenderType.itemEntityTranslucentCull(texture)
                : this.model.renderType(texture);
        }
        return getRenderType(entity, bodyVisible, translucent, glowing);
    }

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isInvisibleTo(Lnet/minecraft/world/entity/player/Player;)Z"
        )
    )
    private boolean neoorigins$hideLocalPhantomGhost(T entity, Player player) {
        if (entity == Minecraft.getInstance().player
            && ClientActivePowers.hasCapability("phantom_phase")) {
            return true;
        }
        return entity.isInvisibleTo(player);
    }

    @ModifyArg(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
        ),
        index = 4
    )
    private int neoorigins$useModelColor(int original) {
        ClientPlayerVisuals.ModelColor color = neoorigins$modelColor(neoorigins$renderingEntity);
        return color != null ? color.argb() : original;
    }

    private ClientPlayerVisuals.ModelColor neoorigins$modelColor(T entity) {
        if (entity == null || entity.isInvisible()) return null;
        return entity instanceof AbstractClientPlayer
            ? ClientPlayerVisuals.modelColor(entity.getId())
            : null;
    }
}
