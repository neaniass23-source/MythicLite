package dev.mythiclite;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MobType {
    public record DropEntry(String item, int min, int max, double chance) {
        public int roll() { return min >= max ? min : min + new Random().nextInt(max - min + 1); }
    }

    public final String id;
    private final File file;
    private final YamlConfiguration cfg;

    public EntityType entity;
    public String name;
    public double health = 20, damage = -1, speed = -1, armor = -1, kbRes = -1, followRange = -1, size = -1;
    public boolean glowing, silent, vanillaDrops;
    public int exp = -1;
    public Map<String, String> equipment = new LinkedHashMap<>();
    public List<DropEntry> drops = new ArrayList<>();
    public double moneyChance = 100, moneyMin = 0, moneyMax = 0;
    public double pointsChance = 100, pointsMin = 0, pointsMax = 0;
    public List<SkillDef> skills = new ArrayList<>();
    public HologramSettings holo = new HologramSettings();
    public ModelSettings model = new ModelSettings();

    public MobType(String id, File file, YamlConfiguration cfg) {
        this.id = id; this.file = file; this.cfg = cfg;
        ConfigurationSection s = cfg.getConfigurationSection(id);
        String t = s.getString("type", "ZOMBIE").toLowerCase();
        EntityType et = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(t));
        if (et == null || et.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(et.getEntityClass()))
            throw new IllegalArgumentException("Tipo de entidad inválido: " + t);
        entity = et;
        name = s.getString("name", id);
        health = s.getDouble("health", 20);
        damage = s.getDouble("damage", -1);
        speed = s.getDouble("speed", -1);
        armor = s.getDouble("armor", -1);
        kbRes = s.getDouble("knockback-resistance", -1);
        followRange = s.getDouble("follow-range", -1);
        size = s.getDouble("size", -1);
        glowing = s.getBoolean("glowing", false);
        silent = s.getBoolean("silent", false);
        vanillaDrops = s.getBoolean("vanilla-drops", false);
        exp = s.getInt("exp", -1);

        ConfigurationSection eq = s.getConfigurationSection("equipment");
        if (eq != null) for (String k : eq.getKeys(false)) equipment.put(k.toLowerCase(), eq.getString(k));

        for (Map<?, ?> m : s.getMapList("drops")) {
            String item = String.valueOf(m.get("item"));
            String amt = String.valueOf(m.get("amount") == null ? "1" : m.get("amount"));
            int min = 1, max = 1;
            try {
                if (amt.contains("-")) { String[] p = amt.split("-"); min = Integer.parseInt(p[0].trim()); max = Integer.parseInt(p[1].trim()); }
                else min = max = Integer.parseInt(amt.trim());
            } catch (Exception ignored) {}
            drops.add(new DropEntry(item, min, max, Text.num(m.get("chance"), 100)));
        }

        ConfigurationSection mo = s.getConfigurationSection("money");
        if (mo != null) { moneyMin = mo.getDouble("min", 0); moneyMax = Math.max(moneyMin, mo.getDouble("max", moneyMin)); moneyChance = mo.getDouble("chance", 100); }

        ConfigurationSection po = s.getConfigurationSection("points");
        if (po != null) { pointsMin = po.getDouble("min", 0); pointsMax = Math.max(pointsMin, po.getDouble("max", pointsMin)); pointsChance = po.getDouble("chance", 100); }

        for (Map<?, ?> m : s.getMapList("skills")) skills.add(new SkillDef(m));
        holo.load(s.getConfigurationSection("hologram"));
        model.load(s.getConfigurationSection("model"));
    }

    /** Guarda hologram/model editados por comando en el archivo yml. */
    public void save() {
        ConfigurationSection s = cfg.getConfigurationSection(id);
        holo.save(s.createSection("hologram"));
        model.save(s.createSection("model"));
        List<Map<String, Object>> dl = new ArrayList<>();
        for (DropEntry d : drops) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item", d.item());
            m.put("amount", d.min() == d.max() ? String.valueOf(d.min()) : d.min() + "-" + d.max());
            m.put("chance", d.chance());
            dl.add(m);
        }
        s.set("drops", dl);
        ConfigurationSection mo = s.createSection("money");
        mo.set("min", moneyMin); mo.set("max", moneyMax); mo.set("chance", moneyChance);
        ConfigurationSection po = s.createSection("points");
        po.set("min", pointsMin); po.set("max", pointsMax); po.set("chance", pointsChance);
        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }
}
