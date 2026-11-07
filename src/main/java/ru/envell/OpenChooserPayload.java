package ru.envell;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер -> клиент: открыть экран выбора класса. */
public record OpenChooserPayload() implements CustomPayload {
    public static final Id<OpenChooserPayload> ID = new Id<>(Identifier.of(EnvellMod.MOD_ID, "open_chooser"));
    public static final PacketCodec<RegistryByteBuf, OpenChooserPayload> CODEC =
            PacketCodec.unit(new OpenChooserPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
