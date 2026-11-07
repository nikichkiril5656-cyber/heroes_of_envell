package ru.envell;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class EnvellMod implements ModInitializer {
    public static final String MOD_ID = "envell";
    public static final RegistryKey<World> ENVELL =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of(MOD_ID, "envell"));

    /** Шанс найти ноутбук в каждом сундуке структур. */
    public static final float LAPTOP_CHANCE = 0.05f;

    @Override
    public void onInitialize() {
        ModEntities.register();
        ModItems.register();
        HeroManager.register();
        IslandMobs.register();

        // Островной мир: острова и мосты строятся поверх пустого измерения
        ServerChunkEvents.CHUNK_GENERATE.register((world, chunk) -> {
            if (world.getRegistryKey().equals(ENVELL)) IslandGenerator.generate(world, chunk);
        });

        // Ноутбук с шансом 5% в любом ванильном сундуке (сокровищницы, деревни, храмы, руины...)
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            Identifier id = key.getValue();
            if (source.isBuiltin() && id.getNamespace().equals("minecraft") && id.getPath().startsWith("chests/")) {
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(LAPTOP_CHANCE))
                        .with(ItemEntry.builder(ModItems.PORTAL_LAPTOP))
                        .build());
            }
        });
    }
}
