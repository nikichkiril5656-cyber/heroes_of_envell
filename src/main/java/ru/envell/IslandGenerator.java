package ru.envell;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Островной мир Энвелла. Пространство разбито на клетки CELL x CELL, в каждой (почти всегда)
 * висит один остров. Соседние острова соединены деревянными мостами с перилами и фонарями.
 * Всё детерминировано по сиду мира, поэтому чанки совпадают друг с другом на границах.
 *
 * Настройки: CELL (расстояние между островами), размеры радиуса и высоты в island().
 */
public class IslandGenerator {
    public static final int CELL = 112;

    public record Island(int cx, int cz, boolean exists, int x, int z, double r, int topY, double p1, double p2) {}
    private record Bridge(Island a, Island b) {}
    private record BridgeCell(int kind, int y, int step) {} // kind: 1 настил, 2 перила

    private static final BlockState GRASS = Blocks.GRASS_BLOCK.getDefaultState();
    private static final BlockState DIRT = Blocks.DIRT.getDefaultState();
    private static final BlockState STONE = Blocks.STONE.getDefaultState();
    private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.getDefaultState();
    private static final BlockState LOG = Blocks.OAK_LOG.getDefaultState();
    private static final BlockState LEAVES = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);

    // ---------- хэши ----------
    private static long mix(long a) {
        a += 0x9E3779B97F4A7C15L;
        a = (a ^ (a >>> 30)) * 0xBF58476D1CE4E5B9L;
        a = (a ^ (a >>> 27)) * 0x94D049BB133111EBL;
        return a ^ (a >>> 31);
    }

    private static double rand01(long seed, int x, int y, int z) {
        long h = mix(seed ^ mix(x * 0x9E3779B1L) ^ mix(y * 0x85EBCA6BL + 17) ^ mix(z * 0xC2B2AE35L + 31));
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    // ---------- острова ----------
    public static Island island(long seed, int cx, int cz) {
        Random rnd = new Random(mix(mix(seed + cx * 7919L) + cz * 104729L));
        rnd.nextDouble();
        boolean core = Math.abs(cx) <= 1 && Math.abs(cz) <= 1; // 3x3 вокруг центра всегда есть
        boolean exists = core || rnd.nextDouble() < 0.85;
        double r = 18 + rnd.nextDouble() * 14;
        int margin = (int) r + 10;
        int x = cx * CELL + margin + (int) (rnd.nextDouble() * (CELL - 2 * margin));
        int z = cz * CELL + margin + (int) (rnd.nextDouble() * (CELL - 2 * margin));
        int topY = 88 + rnd.nextInt(9);
        return new Island(cx, cz, exists, x, z, r, topY, rnd.nextDouble() * 6.28, rnd.nextDouble() * 6.28);
    }

    private static double d(Island i, int x, int z) {
        double dx = x - i.x(), dz = z - i.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double a = Math.atan2(dz, dx);
        double reff = i.r() * (1 + 0.18 * Math.sin(3 * a + i.p1()) + 0.12 * Math.sin(5 * a + i.p2()));
        return dist / reff;
    }

    private static int surface(Island i, double d) {
        return i.topY() + (int) (3 * (1 - d * d));
    }

    /** Высота верхнего блока острова в точке или Integer.MIN_VALUE, если там пустота. */
    public static int surfaceAt(Island i, int x, int z) {
        double d = d(i, x, z);
        return d >= 1.0 ? Integer.MIN_VALUE : surface(i, d);
    }

    /** Точка появления игрока: центр острова (0,0). */
    public static Vec3d hubSpawn(ServerWorld w) {
        Island i = island(w.getSeed(), 0, 0);
        w.getChunk(i.x() >> 4, i.z() >> 4); // форсируем генерацию чанка
        return new Vec3d(i.x() + 0.5, i.topY() + 5, i.z() + 0.5);
    }

    // ---------- мосты ----------
    private static List<Bridge> bridgesNear(long seed, int c0x, int c1x, int c0z, int c1z) {
        List<Bridge> list = new ArrayList<>();
        for (int i = c0x - 1; i <= c1x + 1; i++) {
            for (int j = c0z - 1; j <= c1z + 1; j++) {
                Island a = island(seed, i, j);
                if (!a.exists()) continue;
                Island east = island(seed, i + 1, j);
                if (east.exists()) list.add(new Bridge(a, east));
                Island south = island(seed, i, j + 1);
                boolean link = Math.abs(i) <= 1 && Math.abs(j) <= 1
                        || rand01(seed, i, 99, j) < 0.75;
                if (south.exists() && link) list.add(new Bridge(a, south));
            }
        }
        return list;
    }

    private static BridgeCell bridgeAt(List<Bridge> bridges, int x, int z) {
        double px = x + 0.5, pz = z + 0.5;
        for (Bridge br : bridges) {
            Island a = br.a(), b = br.b();
            double vx = b.x() - a.x(), vz = b.z() - a.z();
            double len2 = vx * vx + vz * vz;
            double t = ((px - a.x()) * vx + (pz - a.z()) * vz) / len2;
            if (t < 0 || t > 1) continue;
            double len = Math.sqrt(len2);
            double dist = Math.abs((px - a.x()) * vz - (pz - a.z()) * vx) / len;
            if (dist > 2.5) continue;
            if (d(a, x, z) < 1.0 || d(b, x, z) < 1.0) continue; // мост только вне островов
            int y = (int) Math.round(a.topY() + (b.topY() - a.topY()) * t);
            return new BridgeCell(dist <= 1.5 ? 1 : 2, y, (int) Math.floor(t * len));
        }
        return null;
    }

    // ---------- генерация чанка ----------
    public static void generate(ServerWorld world, WorldChunk chunk) {
        long seed = world.getSeed();
        ChunkPos cp = chunk.getPos();
        int bx = cp.getStartX(), bz = cp.getStartZ();
        int c0x = Math.floorDiv(bx, CELL), c1x = Math.floorDiv(bx + 15, CELL);
        int c0z = Math.floorDiv(bz, CELL), c1z = Math.floorDiv(bz + 15, CELL);

        List<Island> islands = new ArrayList<>();
        for (int i = c0x; i <= c1x; i++)
            for (int j = c0z; j <= c1z; j++) {
                Island is = island(seed, i, j);
                if (is.exists()) islands.add(is);
            }
        List<Bridge> bridges = bridgesNear(seed, c0x, c1x, c0z, c1z);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = bx + lx, z = bz + lz;
                boolean onIsland = false;
                for (Island is : islands) {
                    double d = d(is, x, z);
                    if (d >= 1.0) continue;
                    onIsland = true;
                    int ts = surface(is, d);
                    int depth = (int) (is.r() * 0.8 * Math.pow(1 - d, 0.7)) + 3;
                    int bottom = is.topY() - depth;
                    for (int y = bottom; y <= ts; y++) {
                        BlockState st = y == ts ? GRASS : (y >= ts - 3 ? DIRT : stone(seed, x, y, z, is.topY()));
                        chunk.setBlockState(pos.set(x, y, z), st, false);
                    }
                    decorate(seed, chunk, pos, x, z, lx, lz, ts + 1, d);
                    break;
                }
                if (!onIsland) placeBridge(chunk, pos, bridges, x, z);
            }
        }
    }

    private static BlockState stone(long seed, int x, int y, int z, int topY) {
        double h = rand01(seed ^ 0x5bd1e995L, x, y, z);
        if (h < 0.012) return Blocks.COAL_ORE.getDefaultState();
        if (h < 0.018) return Blocks.IRON_ORE.getDefaultState();
        if (h < 0.0205 && y < topY - 8) return Blocks.REDSTONE_ORE.getDefaultState();
        if (h < 0.0215 && y < topY - 12) return Blocks.DIAMOND_ORE.getDefaultState();
        return STONE;
    }

    private static void decorate(long seed, WorldChunk chunk, BlockPos.Mutable pos,
                                 int x, int z, int lx, int lz, int y, double d) {
        double h = rand01(seed ^ 0x1234567L, x, 0, z);
        if (h < 0.012) {
            // дерево: только вдали от границы чанка (крона не выходит за чанк) и от центра острова
            if (d > 0.15 && lx >= 3 && lx <= 12 && lz >= 3 && lz <= 12) tree(seed, chunk, pos, x, z, y);
        } else if (h < 0.15) {
            chunk.setBlockState(pos.set(x, y, z), Blocks.SHORT_GRASS.getDefaultState(), false);
        } else if (h < 0.17) {
            BlockState flower = switch ((int) (rand01(seed, x, 1, z) * 3)) {
                case 0 -> Blocks.DANDELION.getDefaultState();
                case 1 -> Blocks.POPPY.getDefaultState();
                default -> Blocks.CORNFLOWER.getDefaultState();
            };
            chunk.setBlockState(pos.set(x, y, z), flower, false);
        }
    }

    private static void tree(long seed, WorldChunk chunk, BlockPos.Mutable pos, int x, int z, int y) {
        int th = 4 + (int) (rand01(seed, x, 2, z) * 2);
        for (int ly = th - 1; ly <= th + 1; ly++) {
            int r = ly == th + 1 ? 1 : 2;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (r == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    chunk.setBlockState(pos.set(x + dx, y + ly, z + dz), LEAVES, false);
                }
        }
        for (int ly = 0; ly <= th; ly++) chunk.setBlockState(pos.set(x, y + ly, z), LOG, false);
    }

    private static void placeBridge(WorldChunk chunk, BlockPos.Mutable pos, List<Bridge> bridges, int x, int z) {
        BridgeCell c = bridgeAt(bridges, x, z);
        if (c == null) return;
        chunk.setBlockState(pos.set(x, c.y(), z), PLANKS, false);
        if (c.kind() != 2) return;
        BlockState fence = Blocks.OAK_FENCE.getDefaultState()
                .with(FenceBlock.NORTH, rail(bridges, x, z - 1, c.y()))
                .with(FenceBlock.SOUTH, rail(bridges, x, z + 1, c.y()))
                .with(FenceBlock.WEST, rail(bridges, x - 1, z, c.y()))
                .with(FenceBlock.EAST, rail(bridges, x + 1, z, c.y()));
        chunk.setBlockState(pos.set(x, c.y() + 1, z), fence, false);
        if (c.step() % 12 == 0) {
            chunk.setBlockState(pos.set(x, c.y() + 2, z), Blocks.LANTERN.getDefaultState(), false);
        }
    }

    private static boolean rail(List<Bridge> bridges, int x, int z, int y) {
        BridgeCell n = bridgeAt(bridges, x, z);
        return n != null && n.kind() == 2 && n.y() == y;
    }
}
