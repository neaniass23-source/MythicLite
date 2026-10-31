package dev.mythiclite;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Placeholders (PlaceholderAPI), identificador "mythiclite":
 *   %mythiclite_kills_total%                  kills totales del jugador
 *   %mythiclite_kills_<mob>%                  kills del jugador de ese mob
 *   %mythiclite_position_total% / _<mob>%     posición del jugador en el top
 *   %mythiclite_top_<mob|total>_<N>_name%     nombre del puesto N
 *   %mythiclite_top_<mob|total>_<N>_value%    kills del puesto N
 *   %mythiclite_quest_<id>_progress% / _amount% / _percent% / _done%
 */
public class MythicExpansion extends PlaceholderExpansion {
    private final MythicLite plugin;

    public MythicExpansion(MythicLite plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "mythiclite"; }
    @Override public String getAuthor() { return "MythicLite"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase();
        StatsManager st = plugin.stats();

        if (p.startsWith("top_")) {
            String rest = p.substring(4);
            boolean name;
            if (rest.endsWith("_name")) { name = true; rest = rest.substring(0, rest.length() - 5); }
            else if (rest.endsWith("_value")) { name = false; rest = rest.substring(0, rest.length() - 6); }
            else return null;
            int i = rest.lastIndexOf('_');
            if (i < 0) return null;
            int pos;
            try { pos = Integer.parseInt(rest.substring(i + 1)); } catch (NumberFormatException ex) { return null; }
            StatsManager.Entry e = st.entryAt(rest.substring(0, i), pos);
            if (e == null) return name ? "---" : "0";
            return name ? e.name() : String.valueOf(e.value());
        }

        if (player == null) return "";

        if (p.startsWith("kills_")) return String.valueOf(st.kills(player.getUniqueId(), p.substring(6)));

        if (p.startsWith("position_")) {
            int pos = st.positionOf(player.getUniqueId(), p.substring(9));
            return pos > 0 ? String.valueOf(pos) : "-";
        }

        if (p.startsWith("quest_")) {
            String rest = p.substring(6);
            int i = rest.lastIndexOf('_');
            if (i < 0) return null;
            QuestManager.Quest q = plugin.quests().get(rest.substring(0, i));
            if (q == null) return "";
            StatsManager.Data d = st.data(player.getUniqueId());
            boolean done = d.done.contains(q.id);
            int prog = done ? q.amount : Math.min(d.quest.getOrDefault(q.id, 0), q.amount);
            return switch (rest.substring(i + 1)) {
                case "progress" -> String.valueOf(prog);
                case "amount" -> String.valueOf(q.amount);
                case "percent" -> String.valueOf(prog * 100 / q.amount);
                case "done" -> done ? "true" : "false";
                default -> null;
            };
        }
        return null;
    }
}
