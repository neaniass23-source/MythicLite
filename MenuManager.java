package dev.mythiclite;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MenuManager implements Listener {
    enum Kind { MAIN, MOBS, ITEMS, SPAWNERS }

    private static class Holder implements InventoryHolder {
        final Kind kind; final int page; final List<String> ids = new ArrayList<>(); Inventory inv;
        Holder(Kind k, int p) { kind = k; page = p; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final MythicLite plugin;
    public MenuManager(MythicLite plugin) { this.plugin = plugin; }

    private ItemStack icon(Material m, String name, String... lore) {
        ItemStack is = new ItemStack(m);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Text.item(name));
        meta.lore(java.util.Arrays.stream(lore).map(Text::item).toList());
        is.setItemMeta(meta);
        return is;
    }

    public void openMain(Player p) {
        Holder h = new Holder(Kind.MAIN, 0);
        h.inv = Bukkit.createInventory(h, 27, Text.parse("<dark_gray>MythicLite"));
        h.inv.setItem(10, icon(Material.ZOMBIE_HEAD, "<green>Mobs", "<gray>" + plugin.mobs().all().size() + " cargados", "<yellow>Click para abrir"));
        h.inv.setItem(12, icon(Material.DIAMOND_SWORD, "<aqua>Items", "<gray>" + plugin.items().ids().size() + " cargados", "<yellow>Click para abrir"));
        h.inv.setItem(14, icon(Material.SPAWNER, "<gold>Spawners", "<gray>" + plugin.spawners().all().size() + " creados", "<yellow>Click para abrir"));
        h.inv.setItem(16, icon(Material.REDSTONE, "<red>Recargar", "<gray>Recarga config, mobs, items y spawners"));
        h.inv.setItem(22, icon(Material.PAPER, "<white>Info", "<gray>Mobs activos: <white>" + plugin.mobs().activeCount()));
        p.openInventory(h.inv);
    }

    public void open(Player p, Kind k, int page) {
        List<String> ids = switch (k) {
            case MOBS -> new ArrayList<>(plugin.mobs().all().keySet());
            case ITEMS -> new ArrayList<>(plugin.items().ids());
            default -> new ArrayList<>(plugin.spawners().all().keySet());
        };
        int maxPage = Math.max(0, (ids.size() - 1) / 45);
        page = Math.max(0, Math.min(page, maxPage));
        Holder h = new Holder(k, page);
        h.inv = Bukkit.createInventory(h, 54, Text.parse("<dark_gray>MythicLite » " + k.name().toLowerCase() + " (" + (page + 1) + "/" + (maxPage + 1) + ")"));
        for (int i = 0; i < 45 && page * 45 + i < ids.size(); i++) {
            String id = ids.get(page * 45 + i);
            h.ids.add(id);
            h.inv.setItem(i, iconFor(k, id));
        }
        h.inv.setItem(45, icon(Material.ARROW, "<yellow>« Anterior"));
        h.inv.setItem(49, icon(Material.BARRIER, "<red>Volver"));
        h.inv.setItem(53, icon(Material.ARROW, "<yellow>Siguiente »"));
        p.openInventory(h.inv);
    }

    private ItemStack iconFor(Kind k, String id) {
        switch (k) {
            case MOBS -> {
                MobType t = plugin.mobs().get(id);
                ItemStack is = plugin.mobs().egg(t);
                ItemMeta m = is.getItemMeta();
                m.lore(List.of(Text.item("<gray>ID: <white>" + id), Text.item("<gray>Vida: <red>" + t.health + " <gray>Tipo: <white>" + t.entity.getKey().getKey()),
                        Text.item("<gray>Drops: <white>" + t.drops.size() + " <gray>Skills: <white>" + t.skills.size()),
                        Text.item(""), Text.item("<yellow>Click izq: <white>spawnear aquí"), Text.item("<yellow>Click der: <white>obtener huevo")));
                is.setItemMeta(m);
                return is;
            }
            case ITEMS -> { return plugin.items().get(id, 1); }
            default -> {
                SpawnerManager.Data d = plugin.spawners().all().get(id);
                return icon(Material.SPAWNER, "<gold>" + id, "<gray>Mob: <white>" + d.mob, "<gray>Mundo: <white>" + d.world + " " + (int) d.x + "," + (int) d.y + "," + (int) d.z,
                        "<gray>Intervalo: <white>" + d.interval + "s <gray>Máx: <white>" + d.max, "", "<yellow>Click izq: <white>teletransportar", "<red>Shift+click der: <white>borrar");
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!p.hasPermission("mythiclite.admin")) return;
        int slot = e.getRawSlot();

        if (h.kind == Kind.MAIN) {
            switch (slot) {
                case 10 -> open(p, Kind.MOBS, 0);
                case 12 -> open(p, Kind.ITEMS, 0);
                case 14 -> open(p, Kind.SPAWNERS, 0);
                case 16 -> { plugin.reloadAll(); p.sendMessage(Text.parse("<green>MythicLite recargado.")); openMain(p); }
                default -> {}
            }
            return;
        }
        if (slot == 45) { open(p, h.kind, h.page - 1); return; }
        if (slot == 49) { openMain(p); return; }
        if (slot == 53) { open(p, h.kind, h.page + 1); return; }
        if (slot < 0 || slot >= h.ids.size()) return;
        String id = h.ids.get(slot);
        ClickType c = e.getClick();

        switch (h.kind) {
            case MOBS -> {
                MobType t = plugin.mobs().get(id);
                if (t == null) return;
                if (c.isLeftClick()) { plugin.mobs().spawn(t, p.getLocation(), null); p.sendMessage(Text.parse("<green>Spawneado " + id)); }
                else if (c.isRightClick()) p.getInventory().addItem(plugin.mobs().egg(t));
            }
            case ITEMS -> p.getInventory().addItem(plugin.items().get(id, c.isShiftClick() ? 64 : 1));
            case SPAWNERS -> {
                SpawnerManager.Data d = plugin.spawners().all().get(id);
                if (d == null) return;
                if (c == ClickType.SHIFT_RIGHT) { plugin.spawners().remove(id); open(p, h.kind, h.page); }
                else if (c.isLeftClick()) { Location l = d.loc(); if (l != null) p.teleport(l); }
            }
            default -> {}
        }
    }
}
