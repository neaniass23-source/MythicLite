package dev.mythiclite;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bosses con ciclo propio: aparecen cada 'interval' segundos, desaparecen si siguen vivos tras 'lifetime' segundos,
 * invocan mobs al bajar de ciertos porcentajes de vida y anuncian aparición / desaparición / muerte.
 * Configuración en bosses.yml; posición y próximo spawn en bosses-state.yml (se cambia con /mm boss set).
 */
public class BossManager {
    public record Threshold(int percent, String mob, int amount, double radius, String message) {}

    public static class Boss {
        public String id, mob, world;
        public double x, y, z;
        public float yaw;
        public boolean hasLoc;
        public long interval = 3600, lifetime = 1800;
        public String msgSpawn = "", msgDespawn = "", msgKill = "", unknownKiller = "alguien", soundSpawn = "", soundKill = "", announce = "all";
        public final List<Threshold> thresholds = new ArrayList<>();
        // estado
        public long nextSpawn, despawnAt;
        public UUID entity;
        public final Set<Integer> fired = new HashSet<>();
        public final List<UUID> minions = new ArrayList<>();
        public World ticketWorld;
        public int ticketX, ticketZ;
    }

    private final MythicLite plugin;
    private final Map<String, Boss> bosses = new LinkedHashMap<>();
    private final File stateFile;
    private BukkitTask task;

    public BossManager(MythicLite plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "bosses-state.yml");
    }

    public Map<String, Boss> all() { return bosses; }

    // ---------- carga / estado ----------
    public void load() {
        endAll();
        bosses.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "bosses.yml"));
        ConfigurationSection root = y.getConfigurationSection("bosses");
        if (root == null) return;
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        long now = System.currentTimeMillis();
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Boss b = new Boss();
            b.id = id;
            b.mob = s.getString("mob", "");
            b.interval = Math.max(10, s.getLong("interval", 3600));
            b.lifetime = Math.max(5, s.getLong("lifetime", 1800));
            b.announce = s.getString("announce", "all");
            b.msgSpawn = s.getString("messages.spawn", "");
            b.msgDespawn = s.getString("messages.despawn", "");
            b.msgKill = s.getString("messages.kill", "");
            b.unknownKiller = s.getString("messages.unknown-killer", "alguien");
            b.soundSpawn = s.getString("sounds.spawn", "");
            b.soundKill = s.getString("sounds.kill", "");
            for (Map<?, ?> m : s.getMapList("thresholds")) {
                Object msg = m.get("message");
                b.thresholds.add(new Threshold((int) Text.num(m.get("percent"), 0), String.valueOf(m.get("mob")),
                        (int) Text.num(m.get("amount"), 1), Text.num(m.get("radius"), 4), msg == null ? "" : String.valueOf(msg)));
            }
            ConfigurationSection ss = st.getConfigurationSection(id);
            long next = 0;
            if (ss != null) {
                b.world = ss.getString("world");
                b.x = ss.getDouble("x"); b.y = ss.getDouble("y"); b.z = ss.getDouble("z");
                b.yaw = (float) ss.getDouble("yaw", 0);
                b.hasLoc = b.world != null;
                next = ss.getLong("next", 0);
            }
            if (next <= 0) b.nextSpawn = now + b.interval * 1000L;
            else if (next < now) b.nextSpawn = now + 60_000L;
            else b.nextSpawn = next;
            bosses.put(id, b);
        }
    }

    public void saveState() {
        YamlConfiguration st = new YamlConfiguration();
        for (Boss b : bosses.values()) {
            if (b.hasLoc) {
                st.set(b.id + ".world", b.world);
                st.set(b.id + ".x", b.x); st.set(b.id + ".y", b.y); st.set(b.id + ".z", b.z);
                st.set(b.id + ".yaw", b.yaw);
            }
            st.set(b.id + ".next", b.nextSpawn);
        }
        try { st.save(stateFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40, 20);
    }

    /** Apaga todo sin anunciar nada (cierre del servidor / recarga). */
    public void shutdown() {
        if (task != null) { task.cancel(); task = null; }
        endAll();
    }

    private void endAll() {
        for (Boss b : bosses.values()) if (b.entity != null) end(b);
    }

    // ---------- comandos ----------
    public boolean setLocation(String id, Location l) {
        Boss b = bosses.get(id);
        if (b == null) return false;
        b.world = l.getWorld().getName();
        b.x = l.getX(); b.y = l.getY(); b.z = l.getZ(); b.yaw = l.getYaw();
        b.hasLoc = true;
        saveState();
        return true;
    }

    public boolean forceSpawn(String id) {
        Boss b = bosses.get(id);
        return b != null && b.entity == null && b.hasLoc && spawnBoss(b);
    }

    public boolean forceDespawn(String id) {
        Boss b = bosses.get(id);
        if (b == null || b.entity == null) return false;
        end(b);
        broadcast(b, b.msgDespawn);
        return true;
    }

    public boolean isActive(Boss b) { return b.entity != null; }

    // ---------- ciclo ----------
    private void tick() {
        long now = System.currentTimeMillis();
        for (Boss b : bosses.values()) {
            if (b.entity == null) {
                if (b.hasLoc && now >= b.nextSpawn) spawnBoss(b);
                continue;
            }
            ActiveMob am = plugin.mobs().activeOf(b.entity);
            if (am == null || !am.entity.isValid()) {   // desapareció sin pasar por la muerte normal
                end(b);
                broadcast(b, b.msgDespawn);
                continue;
            }
            if (now >= b.despawnAt) {                   // pasó el tiempo máximo
                end(b);
                broadcast(b, b.msgDespawn);
                continue;
            }
            checkThresholds(b, am);
        }
    }

    private boolean spawnBoss(Boss b) {
        long now = System.currentTimeMillis();
        MobType t = plugin.mobs().get(b.mob);
        World w = b.world == null ? null : Bukkit.getWorld(b.world);
        if (t == null || w == null) {
            plugin.getLogger().warning("Boss '" + b.id + "': mob '" + b.mob + "' o mundo '" + b.world + "' no válidos.");
            b.nextSpawn = now + 60_000L;
            return false;
        }
        Location loc = new Location(w, b.x, b.y, b.z, b.yaw, 0);
        loc.getChunk();   // fuerza la carga del chunk
        b.ticketWorld = w; b.ticketX = loc.getBlockX() >> 4; b.ticketZ = loc.getBlockZ() >> 4;
        w.addPluginChunkTicket(b.ticketX, b.ticketZ, plugin);
        ActiveMob am = plugin.mobs().spawn(t, loc, null);
        if (am == null) {
            releaseTicket(b);
            b.nextSpawn = now + 60_000L;
            return false;
        }
        b.entity = am.entity.getUniqueId();
        b.fired.clear();
        b.minions.clear();
        b.despawnAt = now + b.lifetime * 1000L;
        b.nextSpawn = now + b.interval * 1000L;
        saveState();
        broadcast(b, b.msgSpawn);
        playSound(b, b.soundSpawn);
        return true;
    }

    private void checkThresholds(Boss b, ActiveMob am) {
        LivingEntity le = am.entity;
        AttributeInstance mx = le.getAttribute(Attribute.MAX_HEALTH);
        double max = mx == null ? le.getHealth() : mx.getValue();
        double pct = le.getHealth() / Math.max(1, max) * 100.0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < b.thresholds.size(); i++) {
            Threshold th = b.thresholds.get(i);
            if (b.fired.contains(i) || pct > th.percent()) continue;
            b.fired.add(i);
            MobType mt = plugin.mobs().get(th.mob());
            if (mt != null) {
                for (int k = 0; k < th.amount(); k++) {
                    Location l = le.getLocation().add(rnd.nextDouble(-th.radius(), th.radius() + 0.0001), 0, rnd.nextDouble(-th.radius(), th.radius() + 0.0001));
                    ActiveMob m = plugin.mobs().spawn(mt, l, null);
                    if (m != null) b.minions.add(m.entity.getUniqueId());
                }
            }
            if (!th.message().isBlank()) broadcast(b, th.message());
        }
    }

    /** Lo llama MobManager cuando muere cualquier mob custom. */
    public void onMobDeath(ActiveMob am, Player killer) {
        for (Boss b : bosses.values()) {
            if (b.entity == null || !b.entity.equals(am.entity.getUniqueId())) continue;
            String name = killer != null ? killer.getName() : b.unknownKiller;
            end(b);
            broadcast(b, b.msgKill.replace("%player%", name));
            playSound(b, b.soundKill);
            return;
        }
    }

    /** Termina el combate: elimina al boss (si sigue vivo) y a sus mobs invocados. */
    private void end(Boss b) {
        for (UUID u : new ArrayList<>(b.minions)) {
            ActiveMob m = plugin.mobs().activeOf(u);
            if (m != null && m.entity.isValid()) plugin.mobs().remove(m);
        }
        b.minions.clear();
        if (b.entity != null) {
            ActiveMob m = plugin.mobs().activeOf(b.entity);
            if (m != null && m.entity.isValid()) plugin.mobs().remove(m);
        }
        b.entity = null;
        b.fired.clear();
        releaseTicket(b);
    }

    private void releaseTicket(Boss b) {
        if (b.ticketWorld != null) b.ticketWorld.removePluginChunkTicket(b.ticketX, b.ticketZ, plugin);
        b.ticketWorld = null;
    }

    // ---------- mensajes ----------
    private Collection<? extends Player> audience(Boss b) {
        if (b.announce.equalsIgnoreCase("world") && b.world != null) {
            World w = Bukkit.getWorld(b.world);
            if (w != null) return w.getPlayers();
        }
        return Bukkit.getOnlinePlayers();
    }

    private void broadcast(Boss b, String msg) {
        if (msg == null || msg.isBlank()) return;
        MobType t = plugin.mobs().get(b.mob);
        String text = msg.replace("%boss%", t == null ? b.mob : t.name);
        for (Player p : audience(b)) p.sendMessage(Text.parse(text));
    }

    private void playSound(Boss b, String sound) {
        if (sound == null || sound.isBlank()) return;
        for (Player p : audience(b)) p.playSound(p.getLocation(), sound, 1f, 1f);
    }

    public String timeLeft(Boss b) {
        long s = Math.max(0, (b.nextSpawn - System.currentTimeMillis()) / 1000);
        return (s / 60) + "m " + (s % 60) + "s";
    }
}
