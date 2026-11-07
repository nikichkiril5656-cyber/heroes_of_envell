package ru.envell;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Одна модель на все три стадии: части включаются/выключаются по стадии.
 * Стадия 1: шкатулка на ножках с самоцветами.
 * Стадия 2: шкатулка поднята на 6 «ногах», три глаза.
 * Стадия 3: то же, но крышка раскрыта, видны зубы.
 */
public class GreedChestModel extends EntityModel<GreedChestEntity> {
    public static final EntityModelLayer LAYER =
            new EntityModelLayer(Identifier.of(EnvellMod.MOD_ID, "greed_chest"), "main");

    private final ModelPart root, lid, band, gemBig, gemSmall;
    private final ModelPart[] feet = new ModelPart[4];
    private final ModelPart[] eyes = new ModelPart[3];
    private final ModelPart[] teethU = new ModelPart[5];
    private final ModelPart[] teethL = new ModelPart[4];
    private final ModelPart[] legs = new ModelPart[6];

    public GreedChestModel(ModelPart root) {
        this.root = root;
        this.lid = root.getChild("lid");
        this.band = root.getChild("band");
        this.gemBig = root.getChild("gem_big");
        this.gemSmall = root.getChild("gem_small");
        for (int i = 0; i < 4; i++) feet[i] = root.getChild("foot" + i);
        for (int i = 0; i < 3; i++) eyes[i] = lid.getChild("eye" + i);
        for (int i = 0; i < 5; i++) teethU[i] = lid.getChild("tu" + i);
        for (int i = 0; i < 4; i++) teethL[i] = root.getChild("tl" + i);
        for (int i = 0; i < 6; i++) legs[i] = root.getChild("leg" + i);
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData md = new ModelData();
        ModelPartData r = md.getRoot();

        r.addChild("base", ModelPartBuilder.create().uv(0, 0).cuboid(-7, 0, -5, 14, 8, 10),
                ModelTransform.pivot(0, 13, 0));
        ModelPartData lid = r.addChild("lid", ModelPartBuilder.create().uv(0, 20).cuboid(-7, -5, -10, 14, 5, 10),
                ModelTransform.pivot(0, 13, 5));
        r.addChild("band", ModelPartBuilder.create().uv(50, 0).cuboid(-7.5f, -1, -5.5f, 15, 2, 11),
                ModelTransform.pivot(0, 13, 0));
        r.addChild("gem_big", ModelPartBuilder.create().uv(14, 40).cuboid(-1.5f, -1.5f, -1, 3, 3, 1),
                ModelTransform.pivot(0, 16, -5));
        r.addChild("gem_small", ModelPartBuilder.create().uv(24, 40).cuboid(-1, -1, -1, 2, 2, 1),
                ModelTransform.pivot(0, 19.5f, -5));

        int[][] footPos = {{-5, -3}, {5, -3}, {-5, 3}, {5, 3}};
        for (int i = 0; i < 4; i++) {
            r.addChild("foot" + i, ModelPartBuilder.create().uv(0, 40).cuboid(-1.5f, 0, -1.5f, 3, 3, 3),
                    ModelTransform.pivot(footPos[i][0], 21, footPos[i][1]));
        }
        // глаза и верхние зубы: дочерние части крышки (pivot крышки = 0,13,5)
        float[][] eyePos = {{-3, -3}, {0, -4}, {3, -3}};
        for (int i = 0; i < 3; i++) {
            lid.addChild("eye" + i, ModelPartBuilder.create().uv(32, 40).cuboid(-1, -1, -1, 2, 2, 1),
                    ModelTransform.pivot(eyePos[i][0], eyePos[i][1], -10));
        }
        for (int i = 0; i < 5; i++) {
            lid.addChild("tu" + i, ModelPartBuilder.create().uv(40, 40).cuboid(-0.5f, 0, -0.5f, 1, 2, 1),
                    ModelTransform.pivot(-5 + i * 2.5f, 0, -9.5f));
        }
        for (int i = 0; i < 4; i++) {
            r.addChild("tl" + i, ModelPartBuilder.create().uv(40, 40).cuboid(-0.5f, -2, -0.5f, 1, 2, 1),
                    ModelTransform.pivot(-4 + i * 2.67f, 13, -4.5f));
        }
        // 6 щупалец/ног: по три с каждой стороны
        for (int i = 0; i < 6; i++) {
            float x = i < 3 ? 7 : -7;
            float z = -3 + (i % 3) * 3;
            r.addChild("leg" + i, ModelPartBuilder.create().uv(52, 20).cuboid(-0.75f, 0, -0.75f, 1.5f, 14, 1.5f),
                    ModelTransform.pivot(x, 20, z));
        }
        return TexturedModelData.of(md, 128, 64);
    }

    @Override
    public void setAngles(GreedChestEntity e, float limbAngle, float limbDistance,
                          float anim, float headYaw, float headPitch) {
        int s = e.getStage();
        boolean big = s >= 2;
        root.pivotY = big ? -8 : 0; // монстр поднят на ногах
        for (ModelPart p : feet) p.visible = s == 1;
        gemBig.visible = s == 1;
        gemSmall.visible = s == 1;
        for (ModelPart p : eyes) p.visible = big;
        for (ModelPart p : legs) p.visible = big;
        for (ModelPart p : teethU) p.visible = s == 3;
        for (ModelPart p : teethL) p.visible = s == 3;

        lid.pitch = s == 3 ? -0.75f - 0.08f * (float) Math.sin(anim * 0.2f) : -e.getOpenProgress() * 1.1f;

        for (int i = 0; i < 6; i++) {
            float side = i < 3 ? -0.5f : 0.5f;
            legs[i].roll = side + (float) Math.sin(anim * 0.4f + i * 1.1f) * 0.1f * (0.4f + limbDistance * 2f);
            legs[i].pitch = ((i % 3) - 1) * 0.3f + (float) Math.cos(anim * 0.4f + i) * 0.15f * limbDistance;
        }
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
