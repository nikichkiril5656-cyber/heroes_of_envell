package ru.envell;

import net.minecraft.text.Text;

/** Пять классов игры «Герои Энвелла». */
public enum HeroClass {
    KNIGHT("knight"),     // ближний бой
    MAGE("mage"),         // магия
    THIEF("thief"),       // дальний бой и скрытность
    ENGINEER("engineer"), // поддержка: механизмы и турели
    BARD("bard");         // поддержка: усиление союзников, ослабление врагов

    public final String id;

    HeroClass(String id) {
        this.id = id;
    }

    public static HeroClass byId(String s) {
        for (HeroClass c : values()) if (c.id.equalsIgnoreCase(s)) return c;
        return null;
    }

    public static HeroClass byIndex(int i) {
        return (i >= 0 && i < values().length) ? values()[i] : null;
    }

    public Text displayName() {
        return Text.translatable("class.envell." + id);
    }
}
