package ru.envell;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class EnvellClient implements ClientModInitializer {
    private static KeyBinding abilityKey;
    private static int classIndex = -1, level, xpInLevel, xpToNext;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.MORGART, ZombieEntityRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(GreedChestModel.LAYER, GreedChestModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.GREED_CHEST, GreedChestRenderer::new);

        abilityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.envell.ability", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.envell"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (abilityKey.wasPressed()) {
                ClientPlayNetworking.send(new AbilityPayload());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, ctx) -> {
            classIndex = payload.classIndex();
            level = payload.level();
            xpInLevel = payload.xpInLevel();
            xpToNext = payload.xpToNext();
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenChooserPayload.ID, (payload, ctx) ->
                MinecraftClient.getInstance().setScreen(new ClassSelectScreen()));

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            HeroClass hc = HeroClass.byIndex(classIndex);
            if (hc == null) return; // вне Энвелла сервер присылает -1, HUD скрыт
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            Text progress = xpToNext == 0
                    ? Text.translatable("hud.envell.max")
                    : Text.literal(xpInLevel + "/" + xpToNext);
            Text line = Text.empty().append(hc.displayName())
                    .append(Text.literal("  " + Text.translatable("hud.envell.level").getString() + " " + level + "  "))
                    .append(progress);
            drawContext.drawTextWithShadow(mc.textRenderer, line, 6, 6, 0xFFD966);
        });
    }
}
