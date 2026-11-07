package ru.envell;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/** Выбранный класс, опыт по каждому классу и точка возврата в обычный мир. */
public class EnvellState extends PersistentState {
    public static class Data {
        public int selected = -1;
        public int[] xp = new int[HeroClass.values().length];
        public String retDim = "";
        public double rx, ry, rz;
    }

    private final Map<UUID, Data> players = new HashMap<>();
    /** Острова, на которых уже расставлены мобы (ключ = cx и cz клетки). */
    public final Set<Long> populated = new HashSet<>();

    private static final Type<EnvellState> TYPE = new Type<>(EnvellState::new, EnvellState::fromNbt, null);

    public static EnvellState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "envell_heroes");
    }

    public Data get(UUID id) {
        return players.computeIfAbsent(id, k -> new Data());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound playersTag = new NbtCompound();
        for (var e : players.entrySet()) {
            Data d = e.getValue();
            NbtCompound c = new NbtCompound();
            c.putInt("selected", d.selected);
            c.putIntArray("xp", d.xp);
            c.putString("retDim", d.retDim);
            c.putDouble("rx", d.rx);
            c.putDouble("ry", d.ry);
            c.putDouble("rz", d.rz);
            playersTag.put(e.getKey().toString(), c);
        }
        nbt.put("players", playersTag);
        long[] arr = new long[populated.size()];
        int i = 0;
        for (long l : populated) arr[i++] = l;
        nbt.putLongArray("islands", arr);
        return nbt;
    }

    public static EnvellState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        EnvellState s = new EnvellState();
        for (long l : nbt.getLongArray("islands")) s.populated.add(l);
        NbtCompound playersTag = nbt.getCompound("players");
        for (String key : playersTag.getKeys()) {
            NbtCompound c = playersTag.getCompound(key);
            Data d = new Data();
            d.selected = c.getInt("selected");
            int[] xp = c.getIntArray("xp");
            System.arraycopy(xp, 0, d.xp, 0, Math.min(xp.length, d.xp.length));
            d.retDim = c.getString("retDim");
            d.rx = c.getDouble("rx");
            d.ry = c.getDouble("ry");
            d.rz = c.getDouble("rz");
            if (d.selected >= HeroClass.values().length) d.selected = -1;
            s.players.put(UUID.fromString(key), d);
        }
        return s;
    }
}
