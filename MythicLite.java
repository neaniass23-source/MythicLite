package dev.mythiclite;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MythicLite extends JavaPlugin {
    private ItemManager items;
    private MobManager mobs;
    private SpawnerManager spawners;
    private MenuManager menu;
    private SkillExecutor skills;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String f : new String[]{"items.yml", "spawners.yml", "mobs/ejemplo.yml"})
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);

        items = new ItemManager(this);
        skills = new SkillExecutor(this);
        mobs = new MobManager(this);
        spawners = new SpawnerManager(this);
        menu = new MenuManager(this);

        getServer().getPluginManager().registerEvents(items, this);
        getServer().getPluginManager().registerEvents(mobs, this);
        getServer().getPluginManager().registerEvents(menu, this);

        MMCommand cmd = new MMCommand(this);
        getCommand("mm").setExecutor(cmd);
        getCommand("mm").setTabCompleter(cmd);

        reloadAll();
        mobs.start();
        spawners.start();
        getLogger().info("MythicLite activado: " + mobs.all().size() + " mobs, " + items.ids().size() + " items.");
    }

    @Override
    public void onDisable() {
        if (mobs != null) mobs.shutdown();
        if (spawners != null) spawners.save();
    }

    public void reloadAll() {
        reloadConfig();
        items.load();
        mobs.load();
        spawners.load();
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
    public MenuManager menu() { return menu; }
    public SkillExecutor skills() { return skills; }
}
