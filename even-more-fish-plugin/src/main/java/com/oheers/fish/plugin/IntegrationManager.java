package com.oheers.fish.plugin;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.addons.InternalAddonLoader;
import com.oheers.fish.api.addons.AddonManager;
import com.oheers.fish.fishing.processors.LavaFishingProcessor;
import com.oheers.fish.fishing.processors.VoidFishingProcessor;
import org.bukkit.Bukkit;
import org.evenmorefish.dimensionfishing.DimensionFishing;

public class IntegrationManager {
    private final EvenMoreFish plugin;
    private AddonManager addonManager;

    public IntegrationManager(EvenMoreFish plugin) {
        this.plugin = plugin;
    }

    public void loadAddons() {
        this.addonManager = new AddonManager(plugin);

        // Load external addons
        this.addonManager.load();

        // Load internal addons
        new InternalAddonLoader(plugin).load();
    }

    public void unloadAddons() {
        this.addonManager.unload();
    }

    public void setupDimensionFishing() {
        Bukkit.getPluginManager().registerEvents(new LavaFishingProcessor(), plugin);
        Bukkit.getPluginManager().registerEvents(new VoidFishingProcessor(), plugin);
        DimensionFishing.getInstance().reload(null);
    }

    public AddonManager getAddonManager() {
        return addonManager;
    }
}
