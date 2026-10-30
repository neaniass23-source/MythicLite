package dev.mythiclite;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SkillExecutor {
    private final MythicLite plugin;
    public boolean busy = false;   // evita bucles ON_ATTACK -> DAMAGE -> ON_ATTACK
    private int depth = 0;

    public SkillExecutor(MythicLite plugin) { this.plugin = plugin; }

    private List<LivingEntity> targets(ActiveMob am, SkillDef s, LivingEntity ctx) {
        LivingEntity self = am.entity;
        LivingEntity tgt = self instanceof Mob mb ? mb.getTarget() : null;
        String m = s.str("targets", "AUTO").toUpperCase();
        if (m.equals("AUTO")) m = ctx != null ? "CONTEXT" : (tgt != null ? "TARGET" : "NEARBY");
        List<LivingEntity> out = new ArrayList<>();
        switch (m) {
            case "SELF" -> out.add(self);
            case "TARGET" -> { if (tgt != null) out.add(tgt); }
            case "CONTEXT" -> { if (ctx != null) out.add(ctx); }
            default -> {
                for (Player p : self.getWorld().getNearbyPlayers(self.getLocation(), s.dbl("range", 12)))
                    if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) out.add(p);
            }
        }
        return out;
    }

    public void execute(ActiveMob am, SkillDef s, LivingEntity ctx) {
        LivingEntity self = am.entity;
        if (!self.isValid() && !s.trigger.equals("ON_DEATH")) return;
        List<LivingEntity> ts = targets(am, s, ctx);
        World w = self.getWorld();
        switch (s.type) {
            case "MESSAGE" -> {
                String txt = s.str("text", "").replace("%mob%", am.type.id);
                for (LivingEntity t : ts) if (t instanceof Player p) p.sendMessage(Text.parse(txt.replace("%player%", p.getName())));
            }
            case "POTION" -> {
                PotionEffectType pt = Registry.EFFECT.get(NamespacedKey.minecraft(s.str("effect", "slowness").toLowerCase()));
                if (pt != null) for (LivingEntity t : ts)
                    t.addPotionEffect(new PotionEffect(pt, (int) s.dbl("duration", 100), (int) s.dbl("amplifier", 0)));
            }
            case "LIGHTNING" -> {
                for (LivingEntity t : ts) {
                    if (s.bool("effect-only", false)) w.strikeLightningEffect(t.getLocation()); else w.strikeLightning(t.getLocation());
                }
            }
            case "FIREBALL" -> {
                if (!ts.isEmpty()) {
                    Vector dir = ts.get(0).getEyeLocation().toVector().subtract(self.getEyeLocation().toVector()).normalize();
                    Fireball fb = self.launchProjectile(Fireball.class, dir);
                    fb.setYield((float) s.dbl("yield", 1));
                    fb.setIsIncendiary(s.bool("fire", false));
                }
            }
            case "KNOCKBACK" -> {
                for (LivingEntity t : ts) {
                    Vector v = t.getLocation().toVector().subtract(self.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() < 1e-4) continue;
                    t.setVelocity(v.normalize().multiply(s.dbl("strength", 1)).setY(s.dbl("up", 0.4)));
                }
            }
            case "DASH" -> {
                if (!ts.isEmpty()) {
                    Vector v = ts.get(0).getLocation().toVector().subtract(self.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() > 1e-4) self.setVelocity(v.normalize().multiply(s.dbl("power", 1.5)).setY(0.3));
                }
            }
            case "TELEPORT" -> { if (!ts.isEmpty()) self.teleport(ts.get(0).getLocation()); }
            case "HEAL" -> {
                AttributeInstance mx = self.getAttribute(Attribute.MAX_HEALTH);
                double max = mx == null ? self.getHealth() : mx.getValue();
                self.setHealth(Math.min(max, self.getHealth() + s.dbl("amount", 10)));
            }
            case "DAMAGE" -> {
                busy = true;
                try { for (LivingEntity t : ts) t.damage(s.dbl("amount", 4), self); } finally { busy = false; }
            }
            case "SUMMON" -> {
                MobType mt = plugin.mobs().get(s.str("mob", ""));
                if (mt != null && depth < 2) {
                    depth++;
                    try {
                        double r = s.dbl("radius", 3);
                        for (int i = 0; i < (int) s.dbl("amount", 1); i++) {
                            ThreadLocalRandom rnd = ThreadLocalRandom.current();
                            plugin.mobs().spawn(mt, self.getLocation().add(rnd.nextDouble(-r, r), 0, rnd.nextDouble(-r, r)), null);
                        }
                    } finally { depth--; }
                }
            }
            case "COMMAND" -> {
                List<String> cmds = new ArrayList<>();
                if (s.data.get("commands") instanceof List<?> l) l.forEach(o -> cmds.add(String.valueOf(o)));
                if (s.data.get("command") != null) cmds.add(s.str("command", ""));
                Location l = self.getLocation();
                List<LivingEntity> list = ts.isEmpty() ? new ArrayList<>(List.of(self)) : ts;
                for (LivingEntity t : list) for (String c : cmds)
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", t.getName()).replace("%mob%", am.type.id)
                            .replace("%x%", String.valueOf(l.getBlockX())).replace("%y%", String.valueOf(l.getBlockY())).replace("%z%", String.valueOf(l.getBlockZ())));
            }
            case "SOUND" -> w.playSound(self.getLocation(), s.str("sound", "entity.wither.spawn"), (float) s.dbl("volume", 1), (float) s.dbl("pitch", 1));
            case "PARTICLE" -> {
                Particle p = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(s.str("particle", "flame").toLowerCase()));
                double sp = s.dbl("spread", 0.5);
                if (p != null) try { w.spawnParticle(p, self.getLocation().add(0, 1, 0), (int) s.dbl("count", 20), sp, sp, sp, 0); } catch (Exception ignored) {}
            }
            default -> plugin.getLogger().warning("Skill desconocida: " + s.type);
        }
    }
}
