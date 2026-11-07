package ru.envell;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/** Колба с кислотой: ПКМ по существу обливает его. Шкатулка жадности к ней особенно уязвима. */
public class AcidFlaskItem extends Item {
    public AcidFlaskItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity target, Hand hand) {
        if (user.getWorld() instanceof ServerWorld w) {
            target.damage(w.getDamageSources().indirectMagic(user, user), 6.0f);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 0));
            w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getHeight() / 2,
                    target.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
            w.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SPLASH_POTION_BREAK,
                    SoundCategory.PLAYERS, 0.8f, 1.2f);
            stack.decrementUnlessCreative(1, user);
        }
        return ActionResult.success(user.getWorld().isClient);
    }
}
