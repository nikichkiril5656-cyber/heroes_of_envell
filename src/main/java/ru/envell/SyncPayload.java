package ru.envell;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер -> клиент: класс, уровень и прогресс для HUD. */
public record SyncPayload(int classIndex, int level, int xpInLevel, int xpToNext) implements CustomPayload {
    public static final Id<SyncPayload> ID = new Id<>(Identifier.of(EnvellMod.MOD_ID, "sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SyncPayload::classIndex,
            PacketCodecs.VAR_INT, SyncPayload::level,
            PacketCodecs.VAR_INT, SyncPayload::xpInLevel,
            PacketCodecs.VAR_INT, SyncPayload::xpToNext,
            SyncPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
