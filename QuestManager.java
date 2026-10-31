package dev.mythiclite;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/** Misiones de tipo "mata N mobs". Se definen en quests.yml y se cuentan solas. */
public class QuestManager {
    public static class Quest {
        public String id;
        public String name;
        public String description;
        public String mob;          // id del mob en minúsculas, o "*" para cualquiera
        public int amount;
        public boolean repeatable;
        public List<String> rewards = new ArrayList<>();
    }

    private final MythicLite plugin;
    private final Map<String, Quest> quests = new LinkedHashMap<>();
    private String msgComplete = "<green>¡Misión completada! <white>%quest%";
    private String msgBroadcast = "";

    public QuestManager(MythicLite plugin) { this.plugin = plugin; }

    public Collection<Quest> all() { return quests.values(); }
    public Quest get(String id) { return quests.get(StatsManager.norm(id)); }

    public void load() {
        quests.clear();
        File f = new File(plugin.getDataFolder(), "quests.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        msgComplete = y.getString("messages.complete", msgComplete);
        msgBroadcast = y.getString("messages.broadcast", "");
        ConfigurationSection s = y.getConfigurationSection("quests");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection q = s.getConfigurationSection(id);
            if (q == null) continue;
            Quest quest = new Quest();
            quest.id = StatsManager.norm(id);
            quest.name = q.getString("name", id);
            quest.description = q.getString("description", "");
            quest.mob = StatsManager.norm(q.getString("mob", "*"));
            quest.amount = Math.max(1, q.getInt("amount", 1));
            quest.repeatable = q.getBoolean("repeatable", false);
            quest.rewards = new ArrayList<>(q.getStringList("rewards"));
            quests.put(quest.id, quest);
        }
    }

    public boolean isDone(Player p, Quest q) {
        return plugin.stats().data(p.getUniqueId()).done.contains(q.id);
    }

    public int progress(Player p, Quest q) {
        return plugin.stats().data(p.getUniqueId()).quest.getOrDefault(q.id, 0);
    }

    /** Se llama cada vez que un jugador mata un mob de MythicLite. */
    public void onKill(Player p, String mobId) {
        if (quests.isEmpty()) return;
        String mob = StatsManager.norm(mobId);
        StatsManager st = plugin.stats();
        StatsManager.Data d = st.data(p.getUniqueId());
        for (Quest q : quests.values()) {
            if (!q.mob.equals("*") && !q.mob.equals(mob)) continue;
            if (d.done.contains(q.id)) continue;
            int prog = d.quest.merge(q.id, 1, Integer::sum);
            st.markDirty();
            if (prog >= q.amount) complete(p, d, q);
        }
    }

    private void complete(Player p, StatsManager.Data d, Quest q) {
        if (q.repeatable) d.quest.put(q.id, 0);
        else d.done.add(q.id);
        for (String r : q.rewards) {
            String cmd = r.startsWith("/") ? r.substring(1) : r;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
        p.sendMessage(Text.parse(msgComplete.replace("%quest%", q.name).replace("%player%", p.getName())));
        p.playSound(p.getLocation(), "entity.player.levelup", 1f, 1f);
        if (msgBroadcast != null && !msgBroadcast.isBlank())
            Bukkit.broadcast(Text.parse(msgBroadcast.replace("%quest%", q.name).replace("%player%", p.getName())));
    }
}
