package dev.mythiclite;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private Text() {}

    /** MiniMessage (<red>, <gradient:..>, <#ff0000>) o códigos & si no hay etiquetas. */
    public static Component parse(String s) {
        if (s == null) return Component.empty();
        if (s.indexOf('&') >= 0 && s.indexOf('<') < 0) return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
        return MM.deserialize(s);
    }

    /** Para nombres/lore de items: sin cursiva por defecto. */
    public static Component item(String s) {
        return parse(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try { return o == null ? def : Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }
}
