package ru.envell;

import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

/**
 * Ноутбук-портал. Находится в сундуках структур (шанс 5%).
 * ПКМ: переход в Энвелл, внутри Энвелла ПКМ возвращает назад.
 * Shift+ПКМ внутри Энвелла: вызвать Моргарта.
 */
public class PortalLaptopItem extends Item {
    public PortalLaptopItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack, world.isClient());
        }
        MinecraftServer server = player.getServer();
        boolean inEnvell = HeroManager.isInEnvell(player);

        if (inEnvell && player.isSneaking()) {
            ServerWorld sw = (ServerWorld) world;
            BlockPos pos = player.getBlockPos().offset(player.getHorizontalFacing(), 5);
            ModEntities.MORGART.spawn(sw, pos, SpawnReason.MOB_SUMMONED);
            player.sendMessage(Text.translatable("message.envell.morgart_summoned"), true);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN,
                    SoundCategory.PLAYERS, 0.6f, 1.4f);
            player.getItemCooldownManager().set(this, 600);
            return TypedActionResult.success(stack, false);
        }

        EnvellState.Data d = EnvellState.get(server).get(player.getUuid());
        ServerWorld target;
        Vec3d dest;

        if (inEnvell) {
            // Возврат туда, откуда вошли
            Identifier id = Identifier.tryParse(d.retDim);
            target = id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
            if (target == null) {
                target = server.getOverworld();
                BlockPos sp = target.getSpawnPos();
                target.getChunk(sp.getX() >> 4, sp.getZ() >> 4);
                dest = new Vec3d(sp.getX() + 0.5,
                        target.getTopY(Heightmap.Type.MOTION_BLOCKING, sp.getX(), sp.getZ()) + 1, sp.getZ() + 0.5);
            } else {
                dest = new Vec3d(d.rx, d.ry, d.rz);
            }
        } else {
            target = server.getWorld(EnvellMod.ENVELL);
            if (target == null) {
                player.sendMessage(Text.translatable("message.envell.no_dimension"), true);
                return TypedActionResult.fail(stack);
            }
            // Запоминаем точку возврата
            d.retDim = world.getRegistryKey().getValue().toString();
            d.rx = player.getX();
            d.ry = player.getY();
            d.rz = player.getZ();
            EnvellState.get(server).markDirty();
            dest = IslandGenerator.hubSpawn(target);
        }

        player.teleportTo(new TeleportTarget(target, dest, Vec3d.ZERO,
                player.getYaw(), player.getPitch(), TeleportTarget.NO_OP));
        target.playSound(null, dest.x, dest.y, dest.z, SoundEvents.BLOCK_PORTAL_TRAVEL,
                SoundCategory.PLAYERS, 0.4f, 1.6f);
        player.sendMessage(Text.translatable(inEnvell ? "message.envell.left" : "message.envell.entered"), true);
        player.getItemCooldownManager().set(this, 100);
        HeroManager.check(player); // сразу применить/снять классовые бонусы и открыть выбор класса
        return TypedActionResult.success(stack, false);
    }
}
