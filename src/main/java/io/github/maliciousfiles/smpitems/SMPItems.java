package io.github.maliciousfiles.smpitems;

import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDevice;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class SMPItems extends JavaPlugin {

    public static SMPItems instance;

    public static NamespacedKey key(String id) {
        return NamespacedKey.fromString(id, instance);
    }

    @Override
    public void onEnable() {
        instance = this;

        TeleportationDevice.getRecipes().forEach(Bukkit::addRecipe);
        Bukkit.updateRecipes();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
