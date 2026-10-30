package dev.mythiclite;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class HologramSettings {
    public boolean enabled = true;
    public List<String> lines = new ArrayList<>(List.of("%name%", "%bar% <white>%health%<gray>/<white>%maxhealth%"));
    public double offsetY = 0.3;
    public float scale = 1f;
    public String background = "default";
    public Display.Billboard billboard = Display.Billboard.CENTER;
    public boolean shadow = false, seeThrough = false;
    public TextDisplay.TextAlignment alignment = TextDisplay.TextAlignment.CENTER;
    public int lineWidth = 200;
    public float viewRange = 1f;
    public int brightness = -1;

    public void load(ConfigurationSection s) {
        if (s == null) return;
        enabled = s.getBoolean("enabled", true);
        if (s.isList("lines")) lines = new ArrayList<>(s.getStringList("lines"));
        offsetY = s.getDouble("offset-y", 0.3);
        scale = (float) s.getDouble("scale", 1.0);
        background = s.getString("background", "default");
        billboard = enumOf(Display.Billboard.class, s.getString("billboard", "CENTER"), Display.Billboard.CENTER);
        shadow = s.getBoolean("shadow", false);
        seeThrough = s.getBoolean("see-through", false);
        alignment = enumOf(TextDisplay.TextAlignment.class, s.getString("alignment", "CENTER"), TextDisplay.TextAlignment.CENTER);
        lineWidth = s.getInt("line-width", 200);
        viewRange = (float) s.getDouble("view-range", 1.0);
        brightness = s.getInt("brightness", -1);
    }

    public void save(ConfigurationSection s) {
        s.set("enabled", enabled);
        s.set("lines", lines);
        s.set("offset-y", offsetY);
        s.set("scale", scale);
        s.set("background", background);
        s.set("billboard", billboard.name());
        s.set("shadow", shadow);
        s.set("see-through", seeThrough);
        s.set("alignment", alignment.name());
        s.set("line-width", lineWidth);
        s.set("view-range", viewRange);
        s.set("brightness", brightness);
    }

    public void apply(TextDisplay d) {
        d.setBillboard(billboard);
        d.setShadowed(shadow);
        d.setSeeThrough(seeThrough);
        d.setAlignment(alignment);
        d.setLineWidth(lineWidth);
        d.setViewRange(viewRange);
        d.setBrightness(brightness >= 0 ? new Display.Brightness(Math.min(15, brightness), Math.min(15, brightness)) : null);
        if (background.equalsIgnoreCase("default")) {
            d.setDefaultBackground(true);
        } else {
            d.setDefaultBackground(false);
            d.setBackgroundColor(background.equalsIgnoreCase("none") ? Color.fromARGB(0, 0, 0, 0) : parseColor(background));
        }
        d.setTransformation(new Transformation(new Vector3f(0, (float) offsetY, 0), new AxisAngle4f(),
                new Vector3f(scale, scale, scale), new AxisAngle4f()));
    }

    public static Color parseColor(String hex) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() == 6) return Color.fromARGB(0x66, Integer.parseInt(h.substring(0, 2), 16),
                    Integer.parseInt(h.substring(2, 4), 16), Integer.parseInt(h.substring(4, 6), 16));
            if (h.length() == 8) return Color.fromARGB(Integer.parseInt(h.substring(0, 2), 16), Integer.parseInt(h.substring(2, 4), 16),
                    Integer.parseInt(h.substring(4, 6), 16), Integer.parseInt(h.substring(6, 8), 16));
        } catch (Exception ignored) {}
        return Color.fromARGB(0x40, 0, 0, 0);
    }

    public static <E extends Enum<E>> E enumOf(Class<E> c, String v, E def) {
        try { return Enum.valueOf(c, v.toUpperCase()); } catch (Exception e) { return def; }
    }
}
