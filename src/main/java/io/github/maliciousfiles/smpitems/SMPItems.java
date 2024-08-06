package io.github.maliciousfiles.smpitems;

import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDevice;
import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDeviceHandler;
import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationMenuHandler;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public final class SMPItems extends JavaPlugin {

    public static SMPItems instance;

    public static ItemStack createItemStack(ItemStack item, Consumer<ItemMeta> consumer) {
        item.editMeta(consumer);
        return item;
    }

    public static NamespacedKey key(String id) {
        return NamespacedKey.fromString(id, instance);
    }

    public static void addRecipe(CraftingRecipe recipe) {
        Bukkit.addRecipe(recipe);
    }

    @Override
    public void onEnable() {
        instance = this;

        Bukkit.getResourcePack();

        TeleportationDevice.UpgradeType.initAll();
        Bukkit.getPluginManager().registerEvents(new TeleportationDeviceHandler(), this);
        Bukkit.getPluginManager().registerEvents(new TeleportationMenuHandler(), this);
        Bukkit.updateRecipes();
    }

    @Override
    public void onDisable() {
        TeleportationMenuHandler.disable();
    }
}
