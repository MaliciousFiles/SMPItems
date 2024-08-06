package io.github.maliciousfiles.smpitems;

import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDevice;
import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDeviceHandler;
import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationMenuHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public final class SMPItems extends JavaPlugin implements CommandExecutor, TabCompleter {

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

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("recipes")) {
            sender.sendMessage(Component.text("Click to open recipes in browser")
                    .decorate(TextDecoration.UNDERLINED)
                    .color(NamedTextColor.BLUE)
                    .clickEvent(ClickEvent.openUrl("https://github.com/MaliciousFiles/SMPItems/blob/main/README.md")));
        } else if (label.equalsIgnoreCase("textures")) {
            sender.sendMessage(Component.text("Click to download resource pack")
                    .decorate(TextDecoration.UNDERLINED)
                    .color(NamedTextColor.BLUE)
                    .clickEvent(ClickEvent.openUrl("https://github.com/MaliciousFiles/SMPItems/SMPItems%20Resource%20Pack.zip")));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
