package dev.mythiclite;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/** /mtop [mob]  y  /misiones */
public class StatsCommand implements CommandExecutor, TabCompleter {
    private static final String[] COLORS = {"#ffd700", "#c0c0c0", "#cd7f32"};
    private final MythicLite plugin;

    public StatsCommand(MythicLite plugin) { this.plugin = plugin; }

    private void msg(CommandSender s, String m) {
        s.sendMessage(Text.parse("<gradient:#ff5555:#ffaa00>MythicLite</gradient> <dark_gray>» <gray>" + m));
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("misiones")) quests(s);
        else top(s, a);
        return true;
    }

    private MobType findMob(String id) {
        for (Map.Entry<String, MobType> e : plugin.mobs().all().entrySet())
            if (e.getKey().equalsIgnoreCase(id)) return e.getValue();
        return null;
    }

    private void top(CommandSender s, String[] a) {
        String key = StatsManager.TOTAL;
        String title = "Total";
        if (a.length > 0 && !a[0].equalsIgnoreCase("total")) {
            MobType t = findMob(a[0]);
            if (t == null) { msg(s, "<red>Ese mob no existe."); return; }
            key = StatsManager.norm(t.id);
            title = (t.name == null || t.name.isEmpty()) ? t.id : t.name;
        }
        List<StatsManager.Entry> list = plugin.stats().ranking(key);
        s.sendMessage(Text.parse("<gradient:#ff5555:#ffaa00><bold>TOP KILLS</bold></gradient> <dark_gray>» <white>" + title));
        if (list.isEmpty()) {
            s.sendMessage(Text.parse("<gray>Todavía nadie ha matado nada."));
        } else {
            for (int i = 0; i < Math.min(10, list.size()); i++) {
                StatsManager.Entry e = list.get(i);
                String col = i < COLORS.length ? COLORS[i] : "#aaaaaa";
                s.sendMessage(Text.parse("<" + col + ">" + (i + 1) + ". <white>" + e.name()
                        + " <dark_gray>- <yellow>" + e.value()));
            }
        }
        if (s instanceof Player p) {
            int pos = plugin.stats().positionOf(p.getUniqueId(), key);
            int mine = plugin.stats().kills(p.getUniqueId(), key);
            if (pos > 0) s.sendMessage(Text.parse("<gray>Tu posición: <white>#" + pos + " <dark_gray>(<yellow>" + mine + "<dark_gray>)"));
            else s.sendMessage(Text.parse("<gray>Aún no apareces en este top."));
        }
    }

    private void quests(CommandSender s) {
        if (!(s instanceof Player p)) { msg(s, "Solo jugadores."); return; }
        Collection<QuestManager.Quest> all = plugin.quests().all();
        if (all.isEmpty()) { msg(s, "No hay misiones disponibles."); return; }
        s.sendMessage(Text.parse("<gradient:#ff5555:#ffaa00><bold>MISIONES</bold></gradient>"));
        for (QuestManager.Quest q : all) {
            boolean done = plugin.quests().isDone(p, q);
            int prog = done ? q.amount : Math.min(plugin.quests().progress(p, q), q.amount);
            int filled = (int) Math.round(10.0 * prog / q.amount);
            String bar = "<green>" + "█".repeat(filled) + "<dark_gray>" + "█".repeat(10 - filled);
            s.sendMessage(Text.parse("<dark_gray> • <white>" + q.name + " " + bar + " <white>" + prog + "/" + q.amount
                    + (done ? " <green>✔" : "")));
            if (q.description != null && !q.description.isEmpty())
                s.sendMessage(Text.parse("<dark_gray>    <gray>" + q.description));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (c.getName().equalsIgnoreCase("mtop") && a.length == 1) {
            String pre = a[0].toLowerCase(Locale.ROOT);
            if ("total".startsWith(pre)) out.add("total");
            for (String id : plugin.mobs().all().keySet()) if (id.toLowerCase(Locale.ROOT).startsWith(pre)) out.add(id);
        }
        return out;
    }
}
