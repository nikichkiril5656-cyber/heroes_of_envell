package ru.envell;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/** Моргарт, главный злодей Энвелла. Сильный зомби-подобный босс с полосой здоровья. */
public class MorgartEntity extends ZombieEntity {
    private final ServerBossBar bossBar = new ServerBossBar(
            Text.translatable("entity.envell.morgart"),
            BossBar.Color.PURPLE,
            BossBar.Style.NOTCHED_10);

    public MorgartEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 50;
    }

    public static DefaultAttributeContainer.Builder createMorgartAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 150.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 9.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_ARMOR, 10.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected boolean isAffectedByDaylight() {
        return false; // злодей не боится солнца
    }

    @Override
    public boolean isBaby() {
        return false;
    }

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
        bossBar.setPercent(this.getHealth() / this.getMaxHealth());
    }
}
