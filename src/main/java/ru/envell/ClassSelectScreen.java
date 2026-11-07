package ru.envell;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Выбор класса. Выбор окончательный, поэтому нужен второй клик для подтверждения. */
public class ClassSelectScreen extends Screen {
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private HeroClass pending;

    public ClassSelectScreen() {
        super(Text.translatable("screen.envell.choose"));
    }

    @Override
    protected void init() {
        buttons.clear();
        int w = 200, h = 20;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 55;
        for (HeroClass c : HeroClass.values()) {
            ButtonWidget btn = ButtonWidget.builder(c.displayName(), b -> onClick(c))
                    .dimensions(x, y, w, h)
                    .tooltip(Tooltip.of(Text.translatable("class.envell." + c.id + ".desc")))
                    .build();
            buttons.add(addDrawableChild(btn));
            y += 24;
        }
    }

    private void onClick(HeroClass c) {
        if (pending == c) {
            ClientPlayNetworking.send(new ChooseClassPayload(c.ordinal()));
            this.close();
            return;
        }
        pending = c;
        for (HeroClass hc : HeroClass.values()) {
            ButtonWidget b = buttons.get(hc.ordinal());
            b.setMessage(hc == c
                    ? Text.translatable("screen.envell.confirm", hc.displayName()).formatted(Formatting.GOLD)
                    : hc.displayName());
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 85, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("screen.envell.warning"),
                this.width / 2, this.height / 2 - 72, 0xFF8888);
    }
}
