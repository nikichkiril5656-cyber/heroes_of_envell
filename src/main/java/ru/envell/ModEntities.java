package ru.envell;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static final EntityType<MorgartEntity> MORGART = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(EnvellMod.MOD_ID, "morgart"),
            EntityType.Builder.create(MorgartEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f)
                    .maxTrackingRange(10)
                    .build("morgart"));

    public static final EntityType<GreedChestEntity> GREED_CHEST = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(EnvellMod.MOD_ID, "greed_chest"),
            EntityType.Builder.create(GreedChestEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.9f, 0.8f)
                    .maxTrackingRange(10)
                    .build("greed_chest"));

    public static void register() {
        FabricDefaultAttributeRegistry.register(GREED_CHEST, GreedChestEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MORGART, MorgartEntity.createMorgartAttributes());
    }
}
