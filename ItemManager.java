package dev.mythiclite;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

public class ItemManager implements Listener {
    private final MythicLite plugin;
    private final Map<String, ItemStack> items = new LinkedHashMap<>();
    private final Map<String, List<String>> commands = new HashMap<>();
    private final Map<String, Double> cooldownSec = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final NamespacedKey key;

    public ItemManager(MythicLite plugin) { this.plugin = plugin; this.key = new NamespacedKey(plugin, "item_id"); }

    public Set<String> ids() { return items.keySet(); }

    public void load() {
        items.clear(); commands.clear(); cooldownSec.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "items.yml"));
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try { items.put(id, build(id, s)); } catch (Exception e) { plugin.getLogger().warning("Item '" + id + "' inválido: " + e.getMessage()); }
            if (s.isList("commands")) commands.put(id, s.getStringList("commands"));
            cooldownSec.put(id, s.getDouble("cooldown", 0));
        }
    }

    private ItemStack build(String id, ConfigurationSection s) {
        Material m = Material.matchMaterial(s.getString("material", "STONE"));
        if (m == null) m = Material.STONE;
        ItemStack is = new ItemStack(m);
        ItemMeta meta = is.getItemMeta();
        if (s.contains("name")) meta.displayName(Text.item(s.getString("name")));
        if (s.isList("lore")) meta.lore(s.getStringList("lore").stream().map(Text::item).toList());
        if (s.contains("custom-model-data")) meta.setCustomModelData(s.getInt("custom-model-data"));
        if (s.contains("item-model")) { NamespacedKey k = NamespacedKey.fromString(s.getString("item-model")); if (k != null) meta.setItemModel(k); }
        ConfigurationSection en = s.getConfigurationSection("enchants");
        if (en != null) for (String k : en.getKeys(false)) {
            Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(k.toLowerCase()));
            if (e != null) meta.addEnchant(e, en.getInt(k), true);
        }
        if (s.getBoolean("unbreakable", false)) meta.setUnbreakable(true);
        if (s.getBoolean("glow", false)) meta.setEnchantmentGlintOverride(true);
        if (s.getBoolean("hide-flags", false)) meta.addItemFlags(ItemFlag.values());
        if (meta instanceof LeatherArmorMeta lm && s.contains("color")) {
            try { lm.setColor(Color.fromRGB(Integer.parseInt(s.getString("color").replace("#", ""), 16))); } catch (Exception ignored) {}
        }
        if (meta instanceof SkullMeta sm && s.contains("head-texture")) applyTexture(sm, s.getString("head-texture"));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        is.setItemMeta(meta);
        return is;
    }

    public static void applyTexture(SkullMeta sm, String base64) {
        PlayerProfile p = Bukkit.createProfile(UUID.randomUUID(), null);
        p.setProperty(new ProfileProperty("textures", base64));
        sm.setPlayerProfile(p);
    }

    public ItemStack get(String id, int amount) {
        ItemStack is = items.get(id);
        if (is == null) return null;
        ItemStack c = is.clone(); c.setAmount(Math.max(1, amount)); return c;
    }

    /** ID de item custom o nombre de Material. */
    public ItemStack resolve(String s, int amount) {
        if (s != null && s.startsWith("b64:")) {
            try {
                ItemStack b = ItemStack.deserializeBytes(Base64.getDecoder().decode(s.substring(4)));
                b.setAmount(Math.max(1, amount));
                return b;
            } catch (Exception e) { return null; }
        }
        ItemStack is = get(s, amount);
        if (is != null) return is;
        Material m = Material.matchMaterial(s);
        return m == null ? null : new ItemStack(m, Math.max(1, amount));
    }

    /** Convierte un item en una referencia guardable en un yml: ID custom, nombre de Material o b64 (item completo). */
    public String encode(ItemStack is) {
        if (is.hasItemMeta()) {
            String id = is.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (id != null && items.containsKey(id)) return id;
            ItemStack one = is.clone(); one.setAmount(1);
            return "b64:" + Base64.getEncoder().encodeToString(one.serializeAsBytes());
        }
        return is.getType().name();
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack is = e.getItem();
        if (is == null || !is.hasItemMeta()) return;
        String id = is.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return;
        List<String> cmds = commands.get(id);
        if (cmds == null || cmds.isEmpty()) return;
        long now = System.currentTimeMillis();
        String ck = e.getPlayer().getUniqueId() + id;
        Long until = cooldowns.get(ck);
        if (until != null && until > now) return;
        cooldowns.put(ck, now + (long) (cooldownSec.getOrDefault(id, 0.0) * 1000));
        for (String c : cmds) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", e.getPlayer().getName()));
    }
}
