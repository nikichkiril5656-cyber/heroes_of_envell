package ru.envell;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: игрок нажал кнопку способности. */
public record AbilityPayload() implements CustomPayload {
    public static final Id<AbilityPayload> ID = new Id<>(Identifier.of(EnvellMod.MOD_ID, "ability"));
    public static final PacketCodec<RegistryByteBuf, AbilityPayload> CODEC = PacketCodec.unit(new AbilityPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
