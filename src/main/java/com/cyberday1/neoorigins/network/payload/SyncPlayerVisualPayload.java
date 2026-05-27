package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record SyncPlayerVisualPayload(
    int entityId,
    Optional<String> modelColor
) implements CustomPacketPayload {

    public static final Type<SyncPlayerVisualPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_player_visual"));

    public static final StreamCodec<FriendlyByteBuf, SyncPlayerVisualPayload> STREAM_CODEC =
        StreamCodec.of(SyncPlayerVisualPayload::encode, SyncPlayerVisualPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncPlayerVisualPayload payload) {
        buf.writeVarInt(payload.entityId());
        buf.writeBoolean(payload.modelColor().isPresent());
        payload.modelColor().ifPresent(buf::writeUtf);
    }

    private static SyncPlayerVisualPayload decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        Optional<String> modelColor = buf.readBoolean()
            ? Optional.of(buf.readUtf())
            : Optional.empty();
        return new SyncPlayerVisualPayload(entityId, modelColor);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
