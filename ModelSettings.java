package dev.mythiclite;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Forma/skin personalizada: un ItemDisplay (con modelo del resource pack o cabeza con textura) montado sobre el mob. */
public class ModelSettings {
    public boolean enabled = false;
    public String material = "PAPER";
    public String itemModel = null;
    public int customModelData = -1;
    public String headTexture = null;
    public float scale = 1f;
    public double offsetY = 0;
    public boolean hideBase = true;
    public boolean followYaw = true;
    public String transform = "NONE";

    public void load(ConfigurationSection s) {
        if (s == null) return;
        enabled = s.getBoolean("enabled", false);
        material = s.getString("material", "PAPER");
        itemModel = s.getString("item-model", null);
        customModelData = s.getInt("custom-model-data", -1);
        headTexture = s.getString("head-texture", null);
        scale = (float) s.getDouble("scale", 1.0);
        offsetY = s.getDouble("offset-y", 0);
        hideBase = s.getBoolean("hide-base", true);
        followYaw = s.getBoolean("follow-yaw", true);
        transform = s.getString("transform", headTexture != null ? "HEAD" : "NONE");
    }

    public void save(ConfigurationSection s) {
        s.set("enabled", enabled);
        s.set("material", material);
        s.set("item-model", itemModel);
        s.set("custom-model-data", customModelData < 0 ? null : customModelData);
        s.set("head-texture", headTexture);
        s.set("scale", scale);
        s.set("offset-y", offsetY);
        s.set("hide-base", hideBase);
        s.set("follow-yaw", followYaw);
        s.set("transform", transform);
    }

    public ItemDisplay.ItemDisplayTransform transform() {
        return HologramSettings.enumOf(ItemDisplay.ItemDisplayTransform.class, transform, ItemDisplay.ItemDisplayTransform.NONE);
    }

    public ItemStack buildItem() {
        Material m = headTexture != null && !headTexture.isBlank() ? Material.PLAYER_HEAD : Material.matchMaterial(material);
        if (m == null) m = Material.PAPER;
        ItemStack is = new ItemStack(m);
        ItemMeta meta = is.getItemMeta();
        if (itemModel != null && !itemModel.isBlank()) {
            NamespacedKey k = NamespacedKey.fromString(itemModel);
            if (k != null) meta.setItemModel(k);
        }
        if (customModelData >= 0) meta.setCustomModelData(customModelData);
        if (meta instanceof SkullMeta sm && headTexture != null && !headTexture.isBlank()) ItemManager.applyTexture(sm, headTexture);
        is.setItemMeta(meta);
        return is;
    }
}
