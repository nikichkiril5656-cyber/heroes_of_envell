package ru.envell;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandSource;
import net.minecraft.damage.DamageSource;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Классы, уровни и способности. Работают ТОЛЬКО внутри измерения Энвелла:
 * вне его игра обычная (нет бонусов, HUD и способностей).
 */
public class HeroManager {
    public static final int MAX_LEVEL = 20;
    private static final String[] MOD_NAMES = {"hp", "atk", "spd", "armor", "aspd", "luck"};
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Boolean> ACTIVE = new HashMap<>();

    private record Turret(ArmorStandEntity stand, UUID owner, int endTick, int level) {}
    private static final List<Turret> TURRETS = new ArrayList<>();

    // Базовая перезарядка способности (тики) по классам
    private static int baseCooldown(HeroClass c) {
        return switch (c) {
            case KNIGHT -> 160;
            case MAGE -> 200;
            case THIEF -> 400;
            case ENGINEER -> 500;
            case BARD -> 400;
        };
    }

    public static int xpForLevel(int level) {
        return 40 * (level - 1) * (level - 1);
    }

    public static int levelFromXp(int xp) {
        int l = 1;
        while (l < MAX_LEVEL && xp >= xpForLevel(l + 1)) l++;
        return l;
    }

    public static boolean isInEnvell(ServerPlayerEntity p) {
        return p.getWorld().getRegistryKey().equals(EnvellMod.ENVELL);
    }

    public static EnvellState.Data data(ServerPlayerEntity p) {
        return EnvellState.get(p.getServer()).get(p.getUuid());
    }

    // ---------- регистрация ----------
    public static void register() {
        PayloadTypeRegistry.playC2S().register(AbilityPayload.ID, AbilityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChooseClassPayload.ID, ChooseClassPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenChooserPayload.ID, OpenChooserPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.ID,
                (payload, ctx) -> useAbility(ctx.player()));

        ServerPlayNetworking.registerGlobalReceiver(ChooseClassPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity p = ctx.player();
            HeroClass hc = HeroClass.byIndex(payload.classIndex());
            if (hc != null && isInEnvell(p) && data(p).selected < 0) setClass(p, hc);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ACTIVE.remove(handler.player.getUuid());
            check(handler.player);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            ACTIVE.remove(newP.getUuid());
            check(newP);
        });

        // Раз в секунду: смена мира, турели
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) check(p);
            tickTurrets(server);
        });

        // Опыт за убийство монстров (только в Энвелле)
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killed) -> {
            if (entity instanceof ServerPlayerEntity p && killed instanceof Monster && isInEnvell(p)) {
                int xp = Math.max(1, (int) (killed.getMaxHealth() / 2));
                if (killed instanceof MorgartEntity) xp = 500;
                addXp(p, xp);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
                dispatcher.register(literal("envell")
                        .then(literal("class").executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            if (!isInEnvell(p)) {
                                p.sendMessage(Text.translatable("message.envell.only_envell"));
                                return 0;
                            }
                            if (data(p).selected >= 0) {
                                p.sendMessage(Text.translatable("message.envell.class_locked"));
                                return 0;
                            }
                            ServerPlayNetworking.send(p, new OpenChooserPayload());
                            return 1;
                        }))
                        .then(literal("stats").executes(c -> {
                            printStats(c.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        // Только для администраторов (тестирование): сбросить выбор класса
                        .then(literal("resetclass")
                                .requires(src -> src.hasPermissionLevel(2))
                                .executes(c -> {
                                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                                    EnvellState state = EnvellState.get(p.getServer());
                                    state.get(p.getUuid()).selected = -1;
                                    state.markDirty();
                                    refresh(p);
                                    p.sendMessage(Text.translatable("message.envell.reset"));
                                    return 1;
                                }))));
    }

    // ---------- проверка «в Энвелле или нет» ----------
    public static void check(ServerPlayerEntity p) {
        boolean active = isInEnvell(p);
        Boolean last = ACTIVE.put(p.getUuid(), active);
        if (last == null || last != active) {
            refresh(p);
            if (active && data(p).selected < 0) {
                ServerPlayNetworking.send(p, new OpenChooserPayload());
                p.sendMessage(Text.translatable("message.envell.choose_hint"), false);
            }
        }
    }

    // ---------- выбор класса / статистика ----------
    /** Выбор окончательный: если класс уже есть, повторный выбор игнорируется. */
    public static void setClass(ServerPlayerEntity p, HeroClass hc) {
        EnvellState state = EnvellState.get(p.getServer());
        EnvellState.Data d = state.get(p.getUuid());
        if (d.selected >= 0) {
            p.sendMessage(Text.translatable("message.envell.class_locked"));
            return;
        }
        d.selected = hc.ordinal();
        state.markDirty();
        refresh(p);
        p.sendMessage(Text.translatable("message.envell.class_set", hc.displayName(),
                levelFromXp(d.xp[hc.ordinal()])).formatted(Formatting.AQUA), false);
    }

    public static void printStats(ServerPlayerEntity p) {
        EnvellState.Data d = data(p);
        for (HeroClass hc : HeroClass.values()) {
            int xp = d.xp[hc.ordinal()];
            p.sendMessage(Text.translatable("message.envell.stats_line", hc.displayName(), levelFromXp(xp), xp)
                    .formatted(hc.ordinal() == d.selected ? Formatting.GOLD : Formatting.GRAY), false);
        }
    }

    // ---------- опыт ----------
    public static void addXp(ServerPlayerEntity p, int amount) {
        EnvellState state = EnvellState.get(p.getServer());
        EnvellState.Data d = state.get(p.getUuid());
        HeroClass hc = HeroClass.byIndex(d.selected);
        if (hc == null) return;
        int oldLevel = levelFromXp(d.xp[d.selected]);
        d.xp[d.selected] += amount;
        int newLevel = levelFromXp(d.xp[d.selected]);
        state.markDirty();
        if (newLevel > oldLevel) {
            applyAttributes(p);
            p.sendMessage(Text.translatable("message.envell.level_up", hc.displayName(), newLevel)
                    .formatted(Formatting.GOLD), false);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 1f, 1f);
        }
        sync(p);
    }

    // ---------- характеристики ----------
    public static void refresh(ServerPlayerEntity p) {
        applyAttributes(p);
        sync(p);
    }

    private static void mod(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, String name,
                            double value, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        Identifier id = Identifier.of(EnvellMod.MOD_ID, "class_" + name);
        inst.removeModifier(id);
        if (value != 0) inst.addTemporaryModifier(new EntityAttributeModifier(id, value, op));
    }

    public static void applyAttributes(ServerPlayerEntity p) {
        EnvellState.Data d = data(p);
        HeroClass hc = isInEnvell(p) ? HeroClass.byIndex(d.selected) : null; // вне Энвелла бонусов нет
        int n = hc == null ? 0 : levelFromXp(d.xp[d.selected]) - 1;

        var ADD = EntityAttributeModifier.Operation.ADD_VALUE;
        var MULT = EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        double hp = 0, atk = 0, spd = 0, armor = 0, aspd = 0, luck = 0;
        if (hc != null) {
            switch (hc) {
                case KNIGHT -> { hp = 1.0 * n; atk = 0.15 * n; armor = 0.1 * n; }
                case MAGE -> { hp = 0.4 * n; atk = 0.05 * n; luck = 0.1 * n; }
                case THIEF -> { hp = 0.4 * n; spd = 0.01 * n; atk = 0.1 * n; }
                case ENGINEER -> { hp = 0.5 * n; armor = 0.3 * n; aspd = 0.05 * n; }
                case BARD -> { hp = 0.5 * n; luck = 0.1 * n; aspd = 0.03 * n; }
            }
        }
        mod(p, EntityAttributes.GENERIC_MAX_HEALTH, "hp", hp, ADD);
        mod(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, "atk", atk, ADD);
        mod(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, "spd", spd, MULT);
        mod(p, EntityAttributes.GENERIC_ARMOR, "armor", armor, ADD);
        mod(p, EntityAttributes.GENERIC_ATTACK_SPEED, "aspd", aspd, ADD);
        mod(p, EntityAttributes.GENERIC_LUCK, "luck", luck, ADD);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ---------- синхронизация с клиентом ----------
    public static void sync(ServerPlayerEntity p) {
        EnvellState.Data d = data(p);
        HeroClass hc = HeroClass.byIndex(d.selected);
        if (hc == null || !isInEnvell(p)) {
            ServerPlayNetworking.send(p, new SyncPayload(-1, 0, 0, 0));
            return;
        }
        int xp = d.xp[d.selected];
        int lvl = levelFromXp(xp);
        int into = xp - xpForLevel(lvl);
        int need = lvl >= MAX_LEVEL ? 0 : xpForLevel(lvl + 1) - xpForLevel(lvl);
        ServerPlayNetworking.send(p, new SyncPayload(d.selected, lvl, into, need));
    }

    // ---------- способности ----------
    public static void useAbility(ServerPlayerEntity p) {
        if (!isInEnvell(p)) {
            p.sendMessage(Text.translatable("message.envell.only_envell"), true);
            return;
        }
        EnvellState.Data d = data(p);
        HeroClass hc = HeroClass.byIndex(d.selected);
        if (hc == null) {
            ServerPlayNetworking.send(p, new OpenChooserPayload());
            return;
        }
        ServerWorld w = (ServerWorld) p.getWorld();
        long now = w.getTime();
        Long until = COOLDOWNS.get(p.getUuid());
        if (until != null && now < until) {
            p.sendMessage(Text.translatable("message.envell.cooldown", (until - now) / 20 + 1), true);
            return;
        }
        int lvl = levelFromXp(d.xp[d.selected]);
        MinecraftServer server = p.getServer();

        switch (hc) {
            case KNIGHT -> { // Рывок со щитом
                Vec3d look = p.getRotationVec(1f);
                p.setVelocity(look.x * 1.8, 0.3, look.z * 1.8);
                p.velocityModified = true;
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 1));
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class,
                        p.getBoundingBox().expand(3), e -> e instanceof Monster)) {
                    e.damage(w.getDamageSources().playerAttack(p), 4 + lvl);
                }
                w.playSound(null, p.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK,
                        SoundCategory.PLAYERS, 1f, 0.8f);
            }
            case MAGE -> { // Огненная волна: конус перед игроком
                Vec3d look = p.getRotationVec(1f).normalize();
                Vec3d eye = p.getEyePos();
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class,
                        p.getBoundingBox().expand(12), e -> e instanceof Monster)) {
                    Vec3d to = e.getPos().add(0, e.getHeight() / 2, 0).subtract(eye);
                    if (to.length() < 0.1 || to.normalize().dotProduct(look) < 0.85) continue;
                    e.damage(w.getDamageSources().playerAttack(p), 6 + lvl * 0.8f);
                    e.setOnFireFor(4);
                }
                for (int i = 1; i <= 12; i++) {
                    Vec3d pt = eye.add(look.multiply(i));
                    w.spawnParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 4, 0.3, 0.3, 0.3, 0.02);
                }
                w.playSound(null, p.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE,
                        SoundCategory.PLAYERS, 1f, 1f);
            }
            case THIEF -> { // Невидимость; можно «взять за руку» союзника рядом
                int dur = 200 + lvl * 20;
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, dur, 0, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, dur, 0));
                for (PlayerEntity ally : w.getEntitiesByClass(PlayerEntity.class,
                        p.getBoundingBox().expand(2), e -> e != p)) {
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, dur / 2, 0, false, false));
                }
                w.spawnParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
                w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                        SoundCategory.PLAYERS, 0.6f, 1.6f);
            }
            case ENGINEER -> { // Турель + ремонт предмета в руке
                spawnTurret(p, w, lvl, server.getTicks());
                ItemStack held = p.getMainHandStack();
                if (held.isDamageable()) held.setDamage(Math.max(0, held.getDamage() - 30));
                w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE,
                        SoundCategory.PLAYERS, 0.8f, 1.5f);
            }
            case BARD -> { // Гипноз врагов + усиление и подзарядка союзников
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class,
                        p.getBoundingBox().expand(8), e -> e instanceof Monster)) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 3));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 1));
                    if (e instanceof MobEntity mob) mob.setTarget(null);
                    w.spawnParticles(ParticleTypes.NOTE, e.getX(), e.getY() + 2, e.getZ(), 4, 0.3, 0.3, 0.3, 1);
                }
                for (PlayerEntity ally : w.getEntitiesByClass(PlayerEntity.class,
                        p.getBoundingBox().expand(10), e -> true)) {
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 300, 0));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 300, 0));
                    if (ally != p) { // «подзарядка снаряжения»: союзникам вдвое короче перезарядка
                        Long u = COOLDOWNS.get(ally.getUuid());
                        if (u != null && u > now) COOLDOWNS.put(ally.getUuid(), now + (u - now) / 2);
                    }
                }
                w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_GUITAR.value(),
                        SoundCategory.PLAYERS, 1f, 1f);
            }
        }
        p.sendMessage(Text.translatable("ability.envell." + hc.id).formatted(Formatting.YELLOW), true);
        COOLDOWNS.put(p.getUuid(), now + Math.max(80, baseCooldown(hc) - lvl * 8L));
    }

    // ---------- турели инженера ----------
    private static void spawnTurret(ServerPlayerEntity p, ServerWorld w, int lvl, int tick) {
        // не больше двух турелей на игрока
        List<Turret> mine = new ArrayList<>();
        for (Turret t : TURRETS) if (t.owner().equals(p.getUuid())) mine.add(t);
        if (mine.size() >= 2) {
            mine.get(0).stand().discard();
            TURRETS.remove(mine.get(0));
        }
        BlockPos pos = p.getBlockPos().offset(p.getHorizontalFacing(), 2);
        ArmorStandEntity stand = EntityType.ARMOR_STAND.spawn(w, pos, SpawnReason.MOB_SUMMONED);
        if (stand == null) return;
        stand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DISPENSER));
        stand.setCustomName(Text.translatable("entity.envell.turret"));
        stand.setCustomNameVisible(true);
        stand.setInvulnerable(true);
        TURRETS.add(new Turret(stand, p.getUuid(), tick + (10 + lvl) * 20, lvl));
    }

    private static void tickTurrets(MinecraftServer server) {
        Iterator<Turret> it = TURRETS.iterator();
        while (it.hasNext()) {
            Turret t = it.next();
            ArmorStandEntity stand = t.stand();
            if (stand.isRemoved() || server.getTicks() >= t.endTick()) {
                if (!stand.isRemoved()) stand.discard();
                it.remove();
                continue;
            }
            ServerWorld w = (ServerWorld) stand.getWorld();
            LivingEntity target = null;
            double best = 144;
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class,
                    stand.getBoundingBox().expand(12), e -> e instanceof Monster)) {
                double dd = e.squaredDistanceTo(stand);
                if (dd < best) { best = dd; target = e; }
            }
            if (target == null) continue;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(t.owner());
            DamageSource src = owner != null ? w.getDamageSources().playerAttack(owner) : w.getDamageSources().magic();
            target.damage(src, 3 + t.level() * 0.4f);
            Vec3d from = stand.getPos().add(0, 1.5, 0);
            Vec3d to = target.getPos().add(0, target.getHeight() / 2, 0);
            for (int i = 0; i <= 10; i++) {
                Vec3d pt = from.lerp(to, i / 10.0);
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            }
            w.playSound(null, stand.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT,
                    SoundCategory.PLAYERS, 0.6f, 1.5f);
        }
    }
}
