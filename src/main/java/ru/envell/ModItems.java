package ru.envell;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item PORTAL_LAPTOP = register("portal_laptop",
            new PortalLaptopItem(new Item.Settings().maxCount(1)));

    public static final Item VIK_WRENCH = register("vik_wrench",
            new SwordItem(ToolMaterials.IRON, new Item.Settings()
                    .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.IRON, 3, -2.0f))));

    public static final Item ENVELL_COIN = register("envell_coin", new Item(new Item.Settings()));

    public static final Item ACID_FLASK = register("acid_flask", new AcidFlaskItem(new Item.Settings().maxCount(16)));

    public static final Item GREED_CHEST_SPAWN_EGG = register("greed_chest_spawn_egg",
            new SpawnEggItem(ModEntities.GREED_CHEST, 0x5B2A86, 0xE6B422, new Item.Settings()));

    public static final Item MORGART_SPAWN_EGG = register("morgart_spawn_egg",
            new SpawnEggItem(ModEntities.MORGART, 0x4B0082, 0x111111, new Item.Settings()));

    public static final ItemGroup ENVELL_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(EnvellMod.MOD_ID, "envell"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(PORTAL_LAPTOP))
                    .displayName(Text.translatable("itemGroup.envell"))
                    .entries((ctx, entries) -> {
                        entries.add(PORTAL_LAPTOP);
                        entries.add(VIK_WRENCH);
                        entries.add(ENVELL_COIN);
                        entries.add(ACID_FLASK);
                        entries.add(GREED_CHEST_SPAWN_EGG);
                        entries.add(MORGART_SPAWN_EGG);
                    })
                    .build());

    private static Item register(String id, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(EnvellMod.MOD_ID, id), item);
    }

    public static void register() {}
}
