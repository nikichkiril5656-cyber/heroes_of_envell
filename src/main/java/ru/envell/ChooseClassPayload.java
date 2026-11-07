package ru.envell;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: игрок выбрал класс. */
public record ChooseClassPayload(int classIndex) implements CustomPayload {
    public static final Id<ChooseClassPayload> ID = new Id<>(Identifier.of(EnvellMod.MOD_ID, "choose_class"));
    public static final PacketCodec<RegistryByteBuf, ChooseClassPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ChooseClassPayload::classIndex, ChooseClassPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
