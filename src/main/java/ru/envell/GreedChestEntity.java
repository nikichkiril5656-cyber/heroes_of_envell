package ru.envell;

import net.minecraft.damage.DamageSource;
import net.minecraft.damage.DamageTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.Set;

/**
 * Шкатулка жадности (мини-босс островов).
 * Стадия 1: спокойная шкатулка. ПКМ: достать золотую монету.
 * Стадия 2 (взято больше 20 монет): мини-монстр, нападает, но монеты всё ещё можно брать.
 * Стадия 3 (взято 100 монет): большой монстр, монеты больше не даёт.
 * Слабости: золотые инструменты и оружие (x2), кислота и магический урон (x2.5).
 */
public class GreedChestEntity extends PathAwareEntity implements Monster {
    private static final TrackedData<Integer> STAGE =
            DataTracker.registerData(GreedChestEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OPEN =
            DataTracker.registerData(GreedChestEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final int COINS_FOR_STAGE_2 = 20; // строго больше
    public static final int COINS_FOR_STAGE_3 = 100;

    private static final Set<Item> GOLD_TOOLS = Set.of(
            Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_PICKAXE,
            Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE);

    private int coinsTaken;
    private int cooldown;
    private int stolenCoins;   // монеты, украденные у игроков: выпадут после смерти
    private int slamCooldown = 100;

    private final ServerBossBar bossBar = new ServerBossBar(
            Text.translatable("entity.envell.greed_chest"), BossBar.Color.YELLOW, BossBar.Style.NOTCHED_6);

    public GreedChestEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_ARMOR, 2)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STAGE, 1);
        builder.add(OPEN, 0);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(1, new SwimGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // ---------- состояние ----------
    public int getStage() {
        return dataTracker.get(STAGE);
    }

    public float getOpenProgress() {
        return dataTracker.get(OPEN) / 10f;
    }

    @Override
    protected boolean isImmobile() {
        return getStage() == 1 || super.isImmobile(); // шкатулка стоит на месте
    }

    @Override
    public void setTarget(LivingEntity target) {
        if (getStage() == 1 && target != null) return; // спокойная шкатулка не нападает
        super.setTarget(target);
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return switch (getStage()) {
            case 2 -> EntityDimensions.fixed(1.0f, 1.2f);
            case 3 -> EntityDimensions.fixed(1.9f, 2.4f);
            default -> EntityDimensions.fixed(0.9f, 0.8f);
        };
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (STAGE.equals(data)) calculateDimensions();
    }

    private void setAttr(RegistryEntry<EntityAttribute> attr, double v) {
        var inst = getAttributeInstance(attr);
        if (inst != null) inst.setBaseValue(v);
    }

    private void applyStageStats(int stage, boolean heal) {
        switch (stage) {
            case 1 -> { setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 30); setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0);
                setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25); setAttr(EntityAttributes.GENERIC_ARMOR, 2);
                setAttr(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0); experiencePoints = 5; }
            case 2 -> { setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 70); setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6);
                setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3); setAttr(EntityAttributes.GENERIC_ARMOR, 4);
                setAttr(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.2); experiencePoints = 20; }
            default -> { setAttr(EntityAttributes.GENERIC_MAX_HEALTH, 250); setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 14);
                setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26); setAttr(EntityAttributes.GENERIC_ARMOR, 8);
                setAttr(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8); experiencePoints = 100; }
        }
        if (heal) setHealth(getMaxHealth());
        else if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }

    private void evolve(int stage) {
        dataTracker.set(STAGE, stage);
        applyStageStats(stage, true);
        calculateDimensions();
        if (getWorld() instanceof ServerWorld w) {
            playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.5f, 0.6f);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.6, 0.6, 0.6, 0.05);
            for (PlayerEntity p : w.getEntitiesByClass(PlayerEntity.class, getBoundingBox().expand(24), x -> true)) {
                p.sendMessage(Text.translatable("message.envell.greed_stage" + stage)
                        .formatted(Formatting.DARK_PURPLE), true);
            }
        }
    }

    // ---------- взаимодействие: достать монету ----------
    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getStage() >= 3) return super.interactMob(player, hand);
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        ItemStack held = player.getMainHandStack();
        if (held.isOf(ModItems.ACID_FLASK) || GOLD_TOOLS.contains(held.getItem())) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (cooldown > 0) return ActionResult.CONSUME;

        ItemStack coin = new ItemStack(ModItems.ENVELL_COIN);
        if (!player.getInventory().insertStack(coin)) player.dropItem(coin, false);
        coinsTaken++;
        cooldown = 8;
        dataTracker.set(OPEN, 10);
        getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.NEUTRAL, 0.6f, 1.4f);
        getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.NEUTRAL, 0.7f, 1.2f);
        player.sendMessage(Text.translatable("message.envell.coins_taken", coinsTaken), true);

        if (coinsTaken >= COINS_FOR_STAGE_3 && getStage() < 3) evolve(3);
        else if (coinsTaken > COINS_FOR_STAGE_2 && getStage() < 2) evolve(2);
        if (getStage() >= 2) setTarget(player);
        return ActionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient) {
            if (cooldown > 0) cooldown--;
            int o = dataTracker.get(OPEN);
            if (o > 0) dataTracker.set(OPEN, o - 1);
        }
    }


    // ---------- полоса здоровья (только стадия 3) ----------
    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bossBar.removePlayer(player);
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        bossBar.setVisible(getStage() >= 3);
        bossBar.setPercent(getHealth() / getMaxHealth());

        // Стадия 3: удар по земле раз в ~8 секунд, когда игрок рядом
        if (getStage() >= 3) {
            if (slamCooldown > 0) slamCooldown--;
            LivingEntity t = getTarget();
            if (slamCooldown <= 0 && t != null && squaredDistanceTo(t) < 36 && getWorld() instanceof ServerWorld w) {
                slamCooldown = 160;
                w.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 6, 1.5, 0.1, 1.5, 0.0);
                w.spawnParticles(ParticleTypes.CRIT, getX(), getY() + 0.5, getZ(), 40, 2.5, 0.2, 2.5, 0.3);
                playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.5f);
                for (PlayerEntity p : w.getEntitiesByClass(PlayerEntity.class, getBoundingBox().expand(5), x -> true)) {
                    p.damage(getDamageSources().mobAttack(this), 10.0f);
                    p.takeKnockback(1.3, getX() - p.getX(), getZ() - p.getZ());
                }
            }
        }
    }

    // ---------- жадность: удар крадёт монеты ----------
    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit && getStage() >= 2 && target instanceof PlayerEntity p) stealCoins(p);
        return hit;
    }

    private void stealCoins(PlayerEntity p) {
        int want = getStage() >= 3 ? 6 : 2;
        int taken = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size() && taken < want; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(ModItems.ENVELL_COIN)) {
                int n = Math.min(want - taken, st.getCount());
                st.decrement(n);
                taken += n;
            }
        }
        if (taken > 0) {
            stolenCoins += taken;
            heal(taken * 2f);
            playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0f, 0.6f);
            p.sendMessage(Text.translatable("message.envell.coins_stolen", taken).formatted(Formatting.RED), true);
        }
    }

    // ---------- слабости ----------
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!getWorld().isClient && getWorld() instanceof ServerWorld w) {
            Entity attacker = source.getAttacker();
            if (attacker instanceof LivingEntity le && GOLD_TOOLS.contains(le.getMainHandStack().getItem())) {
                amount *= 2.0f; // слабое место: золото
                w.spawnParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getHeight() / 2, getZ(),
                        8, 0.3, 0.3, 0.3, 0.1);
            }
            if (source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC)) {
                amount *= 2.5f; // слабость к кислоте
                w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getHeight() / 2, getZ(),
                        10, 0.4, 0.4, 0.4, 0.05);
            }
        }
        return super.damage(source, amount);
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        int stage = getStage();
        int coins = stage == 1 ? 2 + random.nextInt(4) : stage == 2 ? 8 + random.nextInt(8) : 30 + random.nextInt(20);
        coins += stolenCoins; // украденное возвращается
        while (coins > 0) {
            int n = Math.min(coins, 64);
            dropStack(new ItemStack(ModItems.ENVELL_COIN, n));
            coins -= n;
        }
        if (stage >= 2) {
            dropStack(new ItemStack(Items.GOLD_INGOT, stage == 2 ? 2 + random.nextInt(3) : 8 + random.nextInt(9)));
        }
    }

    // ---------- сохранение ----------
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Coins", coinsTaken);
        nbt.putInt("Stage", getStage());
        nbt.putInt("Stolen", stolenCoins);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        coinsTaken = nbt.getInt("Coins");
        stolenCoins = nbt.getInt("Stolen");
        int s = nbt.getInt("Stage");
        if (s >= 1 && s <= 3) {
            dataTracker.set(STAGE, s);
            applyStageStats(s, false);
        }
    }
}
