package dev.mythiclite;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;

public class ActiveMob {
    public final LivingEntity entity;
    public MobType type;
    public final String spawnerId;
    public TextDisplay holo;
    public ItemDisplay model;
    public long[] nextSkill;
    public double lastHealth = -1;
    public float lastYaw = Float.NaN;

    public ActiveMob(LivingEntity entity, MobType type, String spawnerId) {
        this.entity = entity; this.type = type; this.spawnerId = spawnerId;
        this.nextSkill = new long[type.skills.size()];
    }
}
