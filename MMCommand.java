package dev.mythiclite;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class MMCommand implements CommandExecutor, TabCompleter {
    private final MythicLite plugin;
    public MMCommand(MythicLite plugin) { this.plugin = plugin; }

    private void msg(CommandSender s, String m) { s.sendMessage(Text.parse("<gradient:#ff5555:#ffaa00>MythicLite</gradient> <dark_gray>» <gray>" + m)); }

    private void help(CommandSender s) {
        msg(s, "Comandos:");
        for (String l : new String[]{
                "/mm menu", "/mm reload", "/mm list", "/mm spawn <mob> [cantidad]", "/mm give <jugador> <item> [cantidad]", "/mm killall [mob]",
                "/mm spawner <create|remove|list|tp|set> ...", "/mm holo <mob> <sub> ...  (info, enable, disable, addline, insertline, setline, removeline, offset, scale, background, billboard, shadow, seethrough, alignment, linewidth, viewrange, brightness)",
                "/mm model <mob> <enable|disable|material|itemmodel|cmd|texture|scale|offset|hidebase|followyaw|transform|size> [valor]"})
            s.sendMessage(Text.parse("<dark_gray> - <yellow>" + l));
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("mythiclite.admin")) { msg(s, "<red>No tienes permiso."); return true; }
        if (a.length == 0) { if (s instanceof Player p) plugin.menu().openMain(p); else help(s); return true; }
        switch (a[0].toLowerCase()) {
            case "menu" -> { if (s instanceof Player p) plugin.menu().openMain(p); else msg(s, "Solo jugadores."); }
            case "reload" -> { plugin.reloadAll(); msg(s, "<green>Recargado: " + plugin.mobs().all().size() + " mobs, " + plugin.items().ids().size() + " items."); }
            case "list" -> msg(s, "Mobs: <white>" + String.join(", ", plugin.mobs().all().keySet()) + " <gray>| Items: <white>" + String.join(", ", plugin.items().ids()));
            case "spawn" -> {
                if (!(s instanceof Player p) || a.length < 2) { msg(s, "Uso: /mm spawn <mob> [cantidad]"); return true; }
                MobType t = plugin.mobs().get(a[1]);
                if (t == null) { msg(s, "<red>Mob no existe."); return true; }
                int n = a.length > 2 ? parseInt(a[2], 1) : 1;
                for (int i = 0; i < Math.min(n, 50); i++) plugin.mobs().spawn(t, p.getLocation(), null);
                msg(s, "Spawneado <white>" + n + "x " + t.id);
            }
            case "give" -> {
                if (a.length < 3) { msg(s, "Uso: /mm give <jugador> <item> [cantidad]"); return true; }
                Player p = Bukkit.getPlayerExact(a[1]);
                ItemStack is = plugin.items().get(a[2], a.length > 3 ? parseInt(a[3], 1) : 1);
                if (p == null || is == null) { msg(s, "<red>Jugador o item inválido."); return true; }
                p.getInventory().addItem(is);
                msg(s, "Entregado.");
            }
            case "killall" -> msg(s, "Eliminados: <white>" + plugin.mobs().killAll(a.length > 1 ? a[1] : null));
            case "spawner" -> spawner(s, a);
            case "holo" -> holo(s, a);
            case "model" -> model(s, a);
            default -> help(s);
        }
        return true;
    }

    // ---------- spawners ----------
    private void spawner(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "Uso: /mm spawner <create <id> <mob>|remove <id>|list|tp <id>|set <id> <interval|max|amount|radius|range> <n>>"); return; }
        SpawnerManager sm = plugin.spawners();
        switch (a[1].toLowerCase()) {
            case "create" -> {
                if (!(s instanceof Player p) || a.length < 4) { msg(s, "Uso: /mm spawner create <id> <mob>"); return; }
                if (plugin.mobs().get(a[3]) == null) { msg(s, "<red>Mob no existe."); return; }
                sm.create(a[2], a[3], p.getLocation());
                msg(s, "Spawner <white>" + a[2] + "<gray> creado. Ajusta con /mm spawner set.");
            }
            case "remove" -> msg(s, a.length > 2 && sm.remove(a[2]) ? "Eliminado." : "<red>No existe.");
            case "list" -> msg(s, "Spawners: <white>" + String.join(", ", sm.all().keySet()));
            case "tp" -> {
                SpawnerManager.Data d = a.length > 2 ? sm.all().get(a[2]) : null;
                if (d == null || !(s instanceof Player p)) { msg(s, "<red>No existe."); return; }
                Location l = d.loc(); if (l != null) p.teleport(l);
            }
            case "set" -> {
                SpawnerManager.Data d = a.length > 4 ? sm.all().get(a[2]) : null;
                if (d == null) { msg(s, "Uso: /mm spawner set <id> <interval|max|amount|radius|range> <n>"); return; }
                int v = parseInt(a[4], 1);
                switch (a[3].toLowerCase()) {
                    case "interval" -> d.interval = Math.max(1, v);
                    case "max" -> d.max = Math.max(1, v);
                    case "amount" -> d.amount = Math.max(1, v);
                    case "radius" -> d.radius = Math.max(0, v);
                    case "range" -> d.range = Math.max(1, v);
                    default -> { msg(s, "<red>Opción inválida."); return; }
                }
                sm.save(); msg(s, "Actualizado.");
            }
            default -> msg(s, "<red>Subcomando inválido.");
        }
    }

    // ---------- hologramas (estilo FancyHolograms) ----------
    private void holo(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "Uso: /mm holo <mob> <sub> ... (ver /mm)"); return; }
        MobType t = plugin.mobs().get(a[1]);
        if (t == null) { msg(s, "<red>Mob no existe."); return; }
        HologramSettings h = t.holo;
        String sub = a[2].toLowerCase();
        String rest = a.length > 3 ? String.join(" ", Arrays.copyOfRange(a, 3, a.length)) : "";
        try {
            switch (sub) {
                case "info" -> {
                    msg(s, "Holograma de <white>" + t.id + "<gray> (" + (h.enabled ? "activo" : "inactivo") + ")");
                    for (int i = 0; i < h.lines.size(); i++) s.sendMessage(net.kyori.adventure.text.Component.text(" " + (i + 1) + ". " + h.lines.get(i)));
                    msg(s, "offset=" + h.offsetY + " scale=" + h.scale + " bg=" + h.background + " billboard=" + h.billboard + " shadow=" + h.shadow
                            + " seethrough=" + h.seeThrough + " align=" + h.alignment + " width=" + h.lineWidth + " view=" + h.viewRange + " brightness=" + h.brightness);
                    return;
                }
                case "enable" -> h.enabled = true;
                case "disable" -> h.enabled = false;
                case "addline" -> h.lines.add(rest);
                case "insertline" -> h.lines.add(Math.max(0, Math.min(h.lines.size(), Integer.parseInt(a[3]) - 1)), String.join(" ", Arrays.copyOfRange(a, 4, a.length)));
                case "setline" -> h.lines.set(Integer.parseInt(a[3]) - 1, String.join(" ", Arrays.copyOfRange(a, 4, a.length)));
                case "removeline" -> h.lines.remove(Integer.parseInt(a[3]) - 1);
                case "offset" -> h.offsetY = Double.parseDouble(a[3]);
                case "scale" -> h.scale = Float.parseFloat(a[3]);
                case "background" -> h.background = a[3];
                case "billboard" -> h.billboard = Display.Billboard.valueOf(a[3].toUpperCase());
                case "shadow" -> h.shadow = Boolean.parseBoolean(a[3]);
                case "seethrough" -> h.seeThrough = Boolean.parseBoolean(a[3]);
                case "alignment" -> h.alignment = TextDisplay.TextAlignment.valueOf(a[3].toUpperCase());
                case "linewidth" -> h.lineWidth = Integer.parseInt(a[3]);
                case "viewrange" -> h.viewRange = Float.parseFloat(a[3]);
                case "brightness" -> h.brightness = a[3].equalsIgnoreCase("off") ? -1 : Integer.parseInt(a[3]);
                default -> { msg(s, "<red>Subcomando inválido."); return; }
            }
        } catch (Exception e) { msg(s, "<red>Argumentos inválidos."); return; }
        t.save();
        plugin.mobs().refreshType(t);
        msg(s, "Holograma actualizado.");
    }

    // ---------- modelo / skin ----------
    private void model(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "Uso: /mm model <mob> <opción> [valor]"); return; }
        MobType t = plugin.mobs().get(a[1]);
        if (t == null) { msg(s, "<red>Mob no existe."); return; }
        ModelSettings m = t.model;
        String v = a.length > 3 ? a[3] : "";
        try {
            switch (a[2].toLowerCase()) {
                case "enable" -> m.enabled = true;
                case "disable" -> m.enabled = false;
                case "material" -> { m.material = v.toUpperCase(); m.headTexture = null; }
                case "itemmodel" -> m.itemModel = v.equalsIgnoreCase("off") ? null : v;
                case "cmd" -> m.customModelData = Integer.parseInt(v);
                case "texture" -> { m.headTexture = v.equalsIgnoreCase("off") ? null : v; m.transform = "HEAD"; }
                case "scale" -> m.scale = Float.parseFloat(v);
                case "offset" -> m.offsetY = Double.parseDouble(v);
                case "hidebase" -> m.hideBase = Boolean.parseBoolean(v);
                case "followyaw" -> m.followYaw = Boolean.parseBoolean(v);
                case "transform" -> m.transform = v.toUpperCase();
                case "size" -> t.size = Double.parseDouble(v);
                default -> { msg(s, "<red>Opción inválida."); return; }
            }
        } catch (Exception e) { msg(s, "<red>Valor inválido."); return; }
        t.save();
        plugin.mobs().refreshType(t);
        msg(s, "Modelo actualizado.");
    }

    private int parseInt(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("mythiclite.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("menu", "reload", "list", "spawn", "give", "killall", "spawner", "holo", "model"));
        else if (a.length == 2) {
            switch (a[0].toLowerCase()) {
                case "spawn", "killall", "holo", "model" -> out.addAll(plugin.mobs().all().keySet());
                case "give" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                case "spawner" -> out.addAll(List.of("create", "remove", "list", "tp", "set"));
                default -> {}
            }
        } else if (a.length == 3) {
            switch (a[0].toLowerCase()) {
                case "give" -> out.addAll(plugin.items().ids());
                case "holo" -> out.addAll(List.of("info", "enable", "disable", "addline", "insertline", "setline", "removeline", "offset", "scale", "background", "billboard", "shadow", "seethrough", "alignment", "linewidth", "viewrange", "brightness"));
                case "model" -> out.addAll(List.of("enable", "disable", "material", "itemmodel", "cmd", "texture", "scale", "offset", "hidebase", "followyaw", "transform", "size"));
                case "spawner" -> { if (List.of("remove", "tp", "set").contains(a[1].toLowerCase())) out.addAll(plugin.spawners().all().keySet()); else if (a[1].equalsIgnoreCase("create")) out.add("<id>"); }
                default -> {}
            }
        } else if (a.length == 4) {
            if (a[0].equalsIgnoreCase("holo")) switch (a[2].toLowerCase()) {
                case "billboard" -> out.addAll(List.of("CENTER", "FIXED", "VERTICAL", "HORIZONTAL"));
                case "alignment" -> out.addAll(List.of("CENTER", "LEFT", "RIGHT"));
                case "background" -> out.addAll(List.of("default", "none", "#000000"));
                case "shadow", "seethrough" -> out.addAll(List.of("true", "false"));
                default -> {}
            }
            else if (a[0].equalsIgnoreCase("spawner") && a[1].equalsIgnoreCase("create")) out.addAll(plugin.mobs().all().keySet());
            else if (a[0].equalsIgnoreCase("spawner") && a[1].equalsIgnoreCase("set")) out.addAll(List.of("interval", "max", "amount", "radius", "range"));
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
