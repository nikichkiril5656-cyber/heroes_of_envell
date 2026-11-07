package ru.envell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Random;

/**
 * Расставляет мобов на островах один раз при первом создании острова.
 * Чтобы добавить моба: допишите ветку в {@link #populate}.
 */
public class IslandMobs {
    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!world.getRegistryKey().equals(EnvellMod.ENVELL)) return;
            ChunkPos cp = chunk.getPos();
            int cx = Math.floorDiv(cp.getStartX(), IslandGenerator.CELL);
            int cz = Math.floorDiv(cp.getStartZ(), IslandGenerator.CELL);
            if (cx == 0 && cz == 0) return; // главный остров без мобов
            IslandGenerator.Island is = IslandGenerator.island(world.getSeed(), cx, cz);
            if (!is.exists() || (is.x() >> 4) != cp.x || (is.z() >> 4) != cp.z) return;
            // откладываем на следующий тик, чтобы не спавнить во время загрузки чанка
            world.getServer().execute(() -> populate(world, is, cp));
        });
    }

    private static void populate(ServerWorld world, IslandGenerator.Island is, ChunkPos cp) {
        EnvellState state = EnvellState.get(world.getServer());
        long key = ((long) is.cx() << 32) ^ (is.cz() & 0xffffffffL);
        if (!state.populated.add(key)) return;
        state.markDirty();

        Random rnd = new Random(world.getSeed() ^ key * 0x9E3779B97F4A7C15L);
        double roll = rnd.nextDouble();

        if (roll < 0.35) { // Шкатулка жадности
            for (int attempt = 0; attempt < 8; attempt++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double r = 2 + rnd.nextDouble() * 6;
                int x = clamp((int) (is.x() + Math.cos(ang) * r), cp.getStartX() + 1, cp.getStartX() + 14);
                int z = clamp((int) (is.z() + Math.sin(ang) * r), cp.getStartZ() + 1, cp.getStartZ() + 14);
                int ts = IslandGenerator.surfaceAt(is, x, z);
                if (ts == Integer.MIN_VALUE) continue;
                var mob = ModEntities.GREED_CHEST.spawn(world, new BlockPos(x, ts + 1, z), SpawnReason.EVENT);
                if (mob != null) mob.setPersistent();
                return;
            }
        }
        // сюда можно добавить других мобов островов (roll >= 0.35)
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
