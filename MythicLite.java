package dev.mythiclite;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MythicLite extends JavaPlugin {
    private ItemManager items;
    private MobManager mobs;
    private SpawnerManager spawners;
    private BossManager bosses;
    private MenuManager menu;
    private StatsManager stats;
    private QuestManager quests;
    private SkillExecutor skills;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String f : new String[]{"items.yml", "spawners.yml", "bosses.yml", "quests.yml", "mobs/ejemplo.yml", "mobs/dungeon1.yml"})
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);

        items = new ItemManager(this);
        skills = new SkillExecutor(this);
        mobs = new MobManager(this);
        spawners = new SpawnerManager(this);
        bosses = new BossManager(this);
        menu = new MenuManager(this);
        stats = new StatsManager(this);
        quests = new QuestManager(this);
        stats.load();

        getServer().getPluginManager().registerEvents(items, this);
        getServer().getPluginManager().registerEvents(mobs, this);
        getServer().getPluginManager().registerEvents(menu, this);

        MMCommand cmd = new MMCommand(this);
        getCommand("mm").setExecutor(cmd);
        getCommand("mm").setTabCompleter(cmd);

        StatsCommand sc = new StatsCommand(this);
        for (String name : new String[]{"mtop", "misiones"}) {
            getCommand(name).setExecutor(sc);
            getCommand(name).setTabCompleter(sc);
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) hookPlaceholders();

        reloadAll();
        stats.start();
        mobs.start();
        spawners.start();
        bosses.start();
        getLogger().info("MythicLite activado: " + mobs.all().size() + " mobs, " + items.ids().size() + " items.");
    }

    @Override
    public void onDisable() {
        if (bosses != null) bosses.shutdown();
        if (mobs != null) mobs.shutdown();
        if (spawners != null) spawners.save();
        if (stats != null) stats.shutdown();
    }

    public void reloadAll() {
        reloadConfig();
        items.load();
        mobs.load();
        spawners.load();
        bosses.load();
        quests.load();
    }

    private void hookPlaceholders() {
        new MythicExpansion(this).register();
        getLogger().info("PlaceholderAPI detectado: placeholders %mythiclite_...% registrados.");
    }

    public Economy economy() {
        if (economy == null && getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    public ItemManager items() { return items; }
    public MobManager mobs() { return mobs; }
    public SpawnerManager spawners() { return spawners; }
    public BossManager bosses() { return bosses; }
    public MenuManager menu() { return menu; }
    public SkillExecutor skills() { return skills; }
    public StatsManager stats() { return stats; }
    public QuestManager quests() { return quests; }
}
