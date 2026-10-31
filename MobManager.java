package dev.mythiclite;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MobManager implements Listener {
    private final MythicLite plugin;
    private final Map<String, MobType> types = new LinkedHashMap<>();
    private final Map<UUID, ActiveMob> active = new HashMap<>();
    private final Map<String, Integer> spawnerCounts = new HashMap<>();
    public final NamespacedKey KEY_MOB, KEY_EGG;
    private BukkitTask task;
    private long gameTicks = 0;

    public MobManager(MythicLite plugin) {
        this.plugin = plugin;
        KEY_MOB = new NamespacedKey(plugin, "mob_id");
        KEY_EGG = new NamespacedKey(plugin, "mob_egg");
    }

    public MobType get(String id) { return types.get(id); }
    public Map<String, MobType> all() { return types; }
    public int activeCount() { return active.size(); }
    public ActiveMob activeOf(UUID id) { return active.get(id); }
    public void remove(ActiveMob am) { removeMob(am); }
    public int countBySpawner(String id) { return spawnerCounts.getOrDefault(id, 0); }

    // ---------- carga ----------
    public void load() {
        types.clear();
        File dir = new File(plugin.getDataFolder(), "mobs");
        dir.mkdirs();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            for (String id : y.getKeys(false)) {
                if (!y.isConfigurationSection(id)) continue;
                try { types.put(id, new MobType(id, f, y)); }
                catch (Exception e) { plugin.getLogger().warning("Mob '" + id + "' (" + f.getName() + "): " + e.getMessage()); }
            }
        }
        for (ActiveMob am : new ArrayList<>(active.values())) {
            MobType nt = types.get(am.type.id);
            if (nt == null) { removeMob(am); continue; }
            am.type = nt;
            am.nextSkill = new long[nt.skills.size()];
            buildVisuals(am);
        }
    }

    public void start() {
        int period = Math.max(1, plugin.getConfig().getInt("update-ticks", 4));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, period, period);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (ActiveMob am : new ArrayList<>(active.values())) removeMob(am);
    }

    private void removeMob(ActiveMob am) {
        cleanup(am);
        am.entity.remove();
    }

    private void cleanup(ActiveMob am) {
        if (active.remove(am.entity.getUniqueId()) == null) return;
        removeVisuals(am);
        if (am.spawnerId != null) spawnerCounts.merge(am.spawnerId, -1, Integer::sum);
    }

    // ---------- spawn ----------
    public ActiveMob spawn(MobType t, Location loc, String spawnerId) {
        Entity ent = loc.getWorld().spawnEntity(loc, t.entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!(ent instanceof LivingEntity le)) { ent.remove(); return null; }
        le.getPersistentDataContainer().set(KEY_MOB, PersistentDataType.STRING, t.id);
        le.setPersistent(false);  // no se guarda con el chunk: evita mobs huérfanos sin holograma
        le.setRemoveWhenFarAway(false);  // el boss/mobs de dungeon no deben despawnear solos por distancia
        applyStats(le, t);
        ActiveMob am = new ActiveMob(le, t, spawnerId);
        long now = gameTicks;
        for (int i = 0; i < t.skills.size(); i++) am.nextSkill[i] = now + Math.max(1, t.skills.get(i).interval);
        active.put(le.getUniqueId(), am);
        if (spawnerId != null) spawnerCounts.merge(spawnerId, 1, Integer::sum);
        buildVisuals(am);
        runSkills(am, "ON_SPAWN", null);
        return am;
    }

    private void attr(LivingEntity le, Attribute a, double v) {
        if (v < 0) return;
        AttributeInstance ai = le.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    private void applyStats(LivingEntity le, MobType t) {
        attr(le, Attribute.MAX_HEALTH, t.health);
        attr(le, Attribute.ATTACK_DAMAGE, t.damage);
        attr(le, Attribute.MOVEMENT_SPEED, t.speed);
        attr(le, Attribute.ARMOR, t.armor);
        attr(le, Attribute.KNOCKBACK_RESISTANCE, t.kbRes);
        attr(le, Attribute.FOLLOW_RANGE, t.followRange);
        attr(le, Attribute.SCALE, t.size);
        AttributeInstance mx = le.getAttribute(Attribute.MAX_HEALTH);
        if (mx != null) le.setHealth(mx.getValue());
        le.customName(Text.parse(t.name));
        le.setGlowing(t.glowing);
        le.setSilent(t.silent);
        le.setCanPickupItems(false);
        EntityEquipment eq = le.getEquipment();
        if (eq != null && !(t.model.enabled && t.model.hideBase)) {
            for (Map.Entry<String, String> en : t.equipment.entrySet()) {
                ItemStack is = plugin.items().resolve(en.getValue(), 1);
                if (is == null) continue;
                switch (en.getKey()) {
                    case "helmet" -> { eq.setHelmet(is); eq.setHelmetDropChance(0f); }
                    case "chestplate" -> { eq.setChestplate(is); eq.setChestplateDropChance(0f); }
                    case "leggings" -> { eq.setLeggings(is); eq.setLeggingsDropChance(0f); }
                    case "boots" -> { eq.setBoots(is); eq.setBootsDropChance(0f); }
                    case "mainhand" -> { eq.setItemInMainHand(is); eq.setItemInMainHandDropChance(0f); }
                    case "offhand" -> { eq.setItemInOffHand(is); eq.setItemInOffHandDropChance(0f); }
                    default -> {}
                }
            }
        }
    }

    // ---------- visuales (modelo + holograma) ----------
    public void buildVisuals(ActiveMob am) {
        removeVisuals(am);
        LivingEntity le = am.entity;
        MobType t = am.type;
        ModelSettings m = t.model;
        le.setInvisible(m.enabled && m.hideBase);
        attr(le, Attribute.SCALE, t.size);

        if (m.enabled) {
            ItemStack item = m.buildItem();
            am.model = le.getWorld().spawn(le.getLocation(), ItemDisplay.class, d -> {
                d.setItemStack(item);
                d.setItemDisplayTransform(m.transform());
                d.setTransformation(new Transformation(new Vector3f(0, (float) m.offsetY, 0), new AxisAngle4f(),
                        new Vector3f(m.scale, m.scale, m.scale), new AxisAngle4f()));
                d.setPersistent(false);
            });
            le.addPassenger(am.model);
            am.lastYaw = Float.NaN;
        }
        if (t.holo.enabled) {
            am.holo = le.getWorld().spawn(le.getLocation(), TextDisplay.class, d -> {
                t.holo.apply(d);
                d.setPersistent(false);
            });
            le.addPassenger(am.holo);
            am.lastHealth = -1;
            updateHolo(am);
        }
        le.setCustomNameVisible(!t.holo.enabled);
    }

    private void removeVisuals(ActiveMob am) {
        if (am.holo != null) { am.holo.remove(); am.holo = null; }
        if (am.model != null) { am.model.remove(); am.model = null; }
    }

    /** Reconstruye visuales de todos los mobs vivos de ese tipo (tras editar holograma/modelo por comando). */
    public void refreshType(MobType t) {
        for (ActiveMob am : new ArrayList<>(active.values())) if (am.type == t) buildVisuals(am);
    }

    private String bar(double hp, double max) {
        int f = (int) Math.round(Math.max(0, Math.min(1, hp / max)) * 10);
        return "<green>" + "■".repeat(f) + "<dark_gray>" + "■".repeat(10 - f);
    }

    public void updateHolo(ActiveMob am) {
        if (am.holo == null || !am.holo.isValid()) return;
        LivingEntity le = am.entity;
        AttributeInstance mx = le.getAttribute(Attribute.MAX_HEALTH);
        double hp = le.getHealth(), max = mx == null ? hp : mx.getValue();
        List<Component> comps = new ArrayList<>();
        for (String line : am.type.holo.lines) {
            comps.add(Text.parse(line.replace("%name%", am.type.name).replace("%bar%", bar(hp, max))
                    .replace("%health%", String.valueOf((int) Math.ceil(hp))).replace("%maxhealth%", String.valueOf((int) Math.ceil(max)))
                    .replace("%percent%", String.valueOf((int) (hp / Math.max(1, max) * 100))).replace("%id%", am.type.id)));
        }
        am.holo.text(Component.join(JoinConfiguration.newlines(), comps));
    }

    // ---------- tick global optimizado ----------
    private void tickAll() {
        int period = Math.max(1, plugin.getConfig().getInt("update-ticks", 4));
        gameTicks += period;
        if (active.isEmpty()) return;
        double range = plugin.getConfig().getDouble("active-range", 64), r2 = range * range;
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        for (ActiveMob am : new ArrayList<>(active.values())) {
            LivingEntity le = am.entity;
            if (!le.isValid()) { cleanup(am); continue; }
            boolean near = false;
            Location l = le.getLocation();
            for (Player p : players) if (p.getWorld() == l.getWorld() && p.getLocation().distanceSquared(l) <= r2) { near = true; break; }
            if (!near) continue;

            double hp = le.getHealth();
            if (hp != am.lastHealth) { am.lastHealth = hp; updateHolo(am); }

            if (am.model != null && am.type.model.followYaw) {
                float yaw = l.getYaw();
                if (Float.isNaN(am.lastYaw) || Math.abs(yaw - am.lastYaw) > 1f) { am.model.setRotation(yaw, 0); am.lastYaw = yaw; }
            }
            LivingEntity target = le instanceof Mob mb ? mb.getTarget() : null;
            for (int i = 0; i < am.type.skills.size(); i++) {
                SkillDef s = am.type.skills.get(i);
                if (!s.trigger.equals("TIMER") || gameTicks < am.nextSkill[i]) continue;
                am.nextSkill[i] = gameTicks + Math.max(period, s.interval);
                if (s.bool("combat", true) && target == null) continue;
                if (ThreadLocalRandom.current().nextDouble() * 100 >= s.chance) continue;
                plugin.skills().execute(am, s, null);
            }
        }
    }

    public void runSkills(ActiveMob am, String trigger, LivingEntity ctx) {
        if (plugin.skills().busy) return;
        for (SkillDef s : am.type.skills) {
            if (!s.trigger.equals(trigger)) continue;
            if (ThreadLocalRandom.current().nextDouble() * 100 >= s.chance) continue;
            plugin.skills().execute(am, s, ctx);
        }
    }

    // ---------- eventos ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        ActiveMob victim = active.get(e.getEntity().getUniqueId());
        if (victim != null && e.getDamager() instanceof Player p) runSkills(victim, "ON_DAMAGED", p);
        else if (victim != null && e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof LivingEntity sh) runSkills(victim, "ON_DAMAGED", sh);
        ActiveMob attacker = active.get(e.getDamager().getUniqueId());
        if (attacker != null && e.getEntity() instanceof LivingEntity target) runSkills(attacker, "ON_ATTACK", target);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        ActiveMob am = active.get(e.getEntity().getUniqueId());
        if (am == null) return;
        MobType t = am.type;
        if (!t.vanillaDrops) e.getDrops().clear();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (MobType.DropEntry d : t.drops) {
            if (rnd.nextDouble() * 100 >= d.chance()) continue;
            ItemStack is = plugin.items().resolve(d.item(), d.roll());
            if (is != null) e.getDrops().add(is);
        }
        if (t.exp >= 0) e.setDroppedExp(t.exp);
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            plugin.stats().addKill(killer, t.id);
            plugin.quests().onKill(killer, t.id);
        }
        if (killer != null && t.moneyMax > 0 && rnd.nextDouble() * 100 < t.moneyChance) {
            double amt = Math.round((t.moneyMin + rnd.nextDouble() * (t.moneyMax - t.moneyMin)) * 100.0) / 100.0;
            Economy eco = plugin.economy();
            if (eco != null) {
                eco.depositPlayer(killer, amt);
                killer.sendActionBar(Text.parse(plugin.getConfig().getString("messages.money-received", "+%amount%")
                        .replace("%amount%", eco.format(amt)).replace("%mob%", t.name)));
            }
        }
        if (killer != null && t.pointsMax > 0 && rnd.nextDouble() * 100 < t.pointsChance) {
            int pts = (int) Math.round(t.pointsMin + rnd.nextDouble() * (t.pointsMax - t.pointsMin));
            if (pts > 0) {
                String cmd = plugin.getConfig().getString("points-command", "playerpoints:bananas give %player% %amount%")
                        .replace("%player%", killer.getName()).replace("%amount%", String.valueOf(pts));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                killer.sendActionBar(Text.parse(plugin.getConfig().getString("messages.points-received", "+%amount%")
                        .replace("%amount%", String.valueOf(pts)).replace("%mob%", t.name)));
            }
        }
        if (am.spawnerId != null) plugin.spawners().onMobDeath(am.spawnerId);
        plugin.bosses().onMobDeath(am, killer);
        runSkills(am, "ON_DEATH", killer);
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent e) {
        ActiveMob am = active.get(e.getEntity().getUniqueId());
        if (am != null) cleanup(am);
    }

    // ---------- huevos de spawn custom ----------
    public ItemStack egg(MobType t) {
        Material m = Material.matchMaterial(t.entity.getKey().getKey().toUpperCase() + "_SPAWN_EGG");
        if (m == null) m = Material.ZOMBIE_SPAWN_EGG;
        ItemStack is = new ItemStack(m);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Text.item(t.name));
        meta.lore(List.of(Text.item("<gray>Mob: <white>" + t.id), Text.item("<gray>Click derecho en un bloque para spawnear")));
        meta.getPersistentDataContainer().set(KEY_EGG, PersistentDataType.STRING, t.id);
        is.setItemMeta(meta);
        return is;
    }

    @EventHandler
    public void onEgg(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack is = e.getItem();
        if (is == null || !is.hasItemMeta()) return;
        String id = is.getItemMeta().getPersistentDataContainer().get(KEY_EGG, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        MobType t = types.get(id);
        if (t == null || !e.getPlayer().hasPermission("mythiclite.admin")) return;
        spawn(t, e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5), null);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) is.setAmount(is.getAmount() - 1);
    }

    public int killAll(String id) {
        int n = 0;
        for (ActiveMob am : new ArrayList<>(active.values()))
            if (id == null || am.type.id.equalsIgnoreCase(id)) { removeMob(am); n++; }
        return n;
    }
}
