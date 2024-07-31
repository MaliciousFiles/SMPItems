package io.github.maliciousfiles.smpitems;

import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDevice;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.plugin.java.JavaPlugin;

public final class SMPItems extends JavaPlugin {

    public static SMPItems instance;

    public static NamespacedKey key(String id) {
        return NamespacedKey.fromString(id, instance);
    }

    @Override
    public void onEnable() {
        instance = this;

        TeleportationDevice.getRecipes().forEach(r -> {
            Bukkit.addRecipe(r);
            Bukkit.getOnlinePlayers().forEach(p -> p.discoverRecipe(r.getKey()));
        });
        Bukkit.updateRecipes();

        Bukkit.getPluginManager().registerEvents(new TeleportationDevice(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
