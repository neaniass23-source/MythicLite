package dev.mythiclite;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnerManager {
    public static class Data {
        public String id, mob, world;
        public double x, y, z;
        public int interval = 30, max = 5, amount = 1, radius = 4, range = 40;
        public boolean afterDeath = false;
        public long next = 0;
        public Location loc() { World w = Bukkit.getWorld(world); return w == null ? null : new Location(w, x, y, z); }
    }

    private final MythicLite plugin;
    private final Map<String, Data> spawners = new LinkedHashMap<>();
    private BukkitTask task;
    private final File file;

    public SpawnerManager(MythicLite plugin) { this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "spawners.yml"); }

    public Map<String, Data> all() { return spawners; }

    public void load() {
        spawners.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("spawners");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            Data d = new Data();
            d.id = id; d.mob = s.getString("mob"); d.world = s.getString("world");
            d.x = s.getDouble("x"); d.y = s.getDouble("y"); d.z = s.getDouble("z");
            d.interval = s.getInt("interval", 30); d.max = s.getInt("max", 5); d.amount = s.getInt("amount", 1);
            d.radius = s.getInt("radius", 4); d.range = s.getInt("range", 40);
            d.afterDeath = s.getBoolean("after-death", false);
            spawners.put(id, d);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Data d : spawners.values()) {
            String p = "spawners." + d.id + ".";
            y.set(p + "mob", d.mob); y.set(p + "world", d.world);
            y.set(p + "x", d.x); y.set(p + "y", d.y); y.set(p + "z", d.z);
            y.set(p + "interval", d.interval); y.set(p + "max", d.max); y.set(p + "amount", d.amount);
            y.set(p + "radius", d.radius); y.set(p + "range", d.range);
            y.set(p + "after-death", d.afterDeath);
        }
        try { y.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public Data create(String id, String mob, Location l) {
        Data d = new Data();
        d.id = id; d.mob = mob; d.world = l.getWorld().getName();
        d.x = l.getBlockX() + 0.5; d.y = l.getY(); d.z = l.getBlockZ() + 0.5;
        spawners.put(id, d); save();
        return d;
    }

    /** Con after-death: el siguiente spawn tarda 'interval' segundos desde que muere el mob. */
    public void onMobDeath(String id) {
        Data d = spawners.get(id);
        if (d != null && d.afterDeath) d.next = System.currentTimeMillis() + d.interval * 1000L;
    }

    public boolean remove(String id) { boolean r = spawners.remove(id) != null; if (r) save(); return r; }

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Data d : spawners.values()) {
            if (now < d.next) continue;
            Location base = d.loc();
            if (base == null || !base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) continue;
            if (base.getWorld().getNearbyPlayers(base, d.range).isEmpty()) continue;
            MobType t = plugin.mobs().get(d.mob);
            if (t == null) continue;
            if (!d.afterDeath) d.next = now + d.interval * 1000L;
            for (int i = 0; i < d.amount && plugin.mobs().countBySpawner(d.id) < d.max; i++)
                plugin.mobs().spawn(t, randomSpot(base, d.radius), d.id);
        }
    }

    private Location randomSpot(Location base, int r) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            Location l = base.clone().add(rnd.nextDouble(-r, r), 0, rnd.nextDouble(-r, r));
            for (int dy = 2; dy >= -2; dy--) {
                Location c = l.clone().add(0, dy, 0);
                if (c.getBlock().isPassable() && c.clone().add(0, 1, 0).getBlock().isPassable() && c.clone().add(0, -1, 0).getBlock().isSolid()) return c;
            }
        }
        return base.clone();
    }
}
