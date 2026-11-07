package ru.envell;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class GreedChestRenderer extends MobEntityRenderer<GreedChestEntity, GreedChestModel> {
    private static final Identifier TEX_CHEST = Identifier.of(EnvellMod.MOD_ID, "textures/entity/greed_chest_1.png");
    private static final Identifier TEX_MONSTER = Identifier.of(EnvellMod.MOD_ID, "textures/entity/greed_chest_2.png");

    public GreedChestRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GreedChestModel(ctx.getPart(GreedChestModel.LAYER)), 0.6f);
    }

    @Override
    public Identifier getTexture(GreedChestEntity entity) {
        return entity.getStage() == 1 ? TEX_CHEST : TEX_MONSTER;
    }

    @Override
    protected void scale(GreedChestEntity entity, MatrixStack matrices, float amount) {
        float s = switch (entity.getStage()) {
            case 2 -> 0.9f;
            case 3 -> 1.9f;
            default -> 1.0f;
        };
        matrices.scale(s, s, s);
        this.shadowRadius = 0.5f * s;
    }
}
