package dev.mythiclite;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Guarda cuántos mobs ha matado cada jugador (por tipo) y el progreso de misiones. Archivo: stats.yml */
public class StatsManager {
    public static final String TOTAL = "total";

    public static class Data {
        public volatile String name = "?";
        public final Map<String, Integer> kills = new ConcurrentHashMap<>();
        public final Map<String, Integer> quest = new ConcurrentHashMap<>();
        public final Set<String> done = ConcurrentHashMap.newKeySet();
    }

    public record Entry(UUID uuid, String name, int value) {}

    private record Cached(long time, List<Entry> list) {}

    private final MythicLite plugin;
    private final File file;
    private final Map<UUID, Data> players = new ConcurrentHashMap<>();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private volatile boolean dirty;
    private BukkitTask task;

    public StatsManager(MythicLite plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    /** Minúsculas y sin puntos, para usar de clave. */
    public static String norm(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    // ---------- carga / guardado ----------
    public void load() {
        players.clear();
        cache.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps == null) return;
        for (String key : ps.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException ex) { continue; }
            ConfigurationSection s = ps.getConfigurationSection(key);
            if (s == null) continue;
            Data d = new Data();
            d.name = s.getString("name", "?");
            ConfigurationSection k = s.getConfigurationSection("kills");
            if (k != null) for (String m : k.getKeys(false)) d.kills.put(m, k.getInt(m));
            ConfigurationSection q = s.getConfigurationSection("quest");
            if (q != null) for (String m : q.getKeys(false)) d.quest.put(m, q.getInt(m));
            d.done.addAll(s.getStringList("done"));
            players.put(id, d);
        }
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) saveAsync(); }, 6000L, 6000L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        write(snapshot());
    }

    private String snapshot() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Data> e : players.entrySet()) {
            Data d = e.getValue();
            String base = "players." + e.getKey();
            y.set(base + ".name", d.name);
            for (Map.Entry<String, Integer> k : d.kills.entrySet()) y.set(base + ".kills." + k.getKey(), k.getValue());
            for (Map.Entry<String, Integer> q : d.quest.entrySet()) y.set(base + ".quest." + q.getKey(), q.getValue());
            if (!d.done.isEmpty()) y.set(base + ".done", new ArrayList<>(d.done));
        }
        return y.saveToString();
    }

    private void saveAsync() {
        dirty = false;
        String data = snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    private synchronized void write(String data) {
        try {
            plugin.getDataFolder().mkdirs();
            File tmp = new File(plugin.getDataFolder(), "stats.yml.tmp");
            Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar stats.yml: " + ex.getMessage());
        }
    }

    // ---------- datos ----------
    public Data data(UUID id) { return players.computeIfAbsent(id, k -> new Data()); }
    public void markDirty() { dirty = true; }

    public void addKill(Player p, String mobId) {
        Data d = data(p.getUniqueId());
        d.name = p.getName();
        d.kills.merge(norm(mobId), 1, Integer::sum);
        dirty = true;
    }

    private int value(Data d, String mob) {
        String m = norm(mob);
        if (m.isEmpty() || m.equals(TOTAL)) {
            int sum = 0;
            for (int v : d.kills.values()) sum += v;
            return sum;
        }
        return d.kills.getOrDefault(m, 0);
    }

    /** Kills de un jugador. mob = null o "total" para la suma de todos. */
    public int kills(UUID id, String mob) {
        Data d = players.get(id);
        return d == null ? 0 : value(d, mob);
    }

    // ---------- tops ----------
    /** Ranking completo (se guarda 5 segundos en caché para no recalcular en cada placeholder). */
    public List<Entry> ranking(String mob) {
        String key = (mob == null || mob.isEmpty()) ? TOTAL : norm(mob);
        long now = System.currentTimeMillis();
        Cached c = cache.get(key);
        if (c != null && now - c.time() < 5000) return c.list();
        List<Entry> list = new ArrayList<>();
        for (Map.Entry<UUID, Data> e : players.entrySet()) {
            int v = value(e.getValue(), key);
            if (v > 0) list.add(new Entry(e.getKey(), e.getValue().name, v));
        }
        list.sort((x, y) -> x.value() != y.value()
                ? Integer.compare(y.value(), x.value())
                : x.name().compareToIgnoreCase(y.name()));
        List<Entry> res = Collections.unmodifiableList(list);
        cache.put(key, new Cached(now, res));
        return res;
    }

    /** Entrada en la posición pos (1 = primero) o null si no existe. */
    public Entry entryAt(String mob, int pos) {
        List<Entry> l = ranking(mob);
        return (pos >= 1 && pos <= l.size()) ? l.get(pos - 1) : null;
    }

    /** Posición del jugador en el top, o -1 si no aparece. */
    public int positionOf(UUID id, String mob) {
        List<Entry> l = ranking(mob);
        for (int i = 0; i < l.size(); i++) if (l.get(i).uuid().equals(id)) return i + 1;
        return -1;
    }
}
