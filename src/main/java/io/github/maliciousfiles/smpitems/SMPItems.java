package io.github.maliciousfiles.smpitems;

import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDevice;
import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDeviceHandler;
import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationMenuHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public final class SMPItems extends JavaPlugin implements Listener {

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

    private static final Map<NamespacedKey, ItemStack> customItems = new HashMap<>();
    public static void addItem(String id, ItemStack item) {
        customItems.put(key(id), item);
    }

    @Override
    public void onEnable() {
        instance = this;

        Bukkit.getResourcePack();

        TeleportationDevice.UpgradeType.initAll();
        Bukkit.getPluginManager().registerEvents(new TeleportationDeviceHandler(), this);
        Bukkit.getPluginManager().registerEvents(new TeleportationMenuHandler(), this);
        Bukkit.updateRecipes();

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        TeleportationMenuHandler.disable();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("recipes")) {
            sender.sendMessage(Component.text("Click to open recipes in browser")
                    .decorate(TextDecoration.UNDERLINED)
                    .color(NamedTextColor.BLUE)
                    .clickEvent(ClickEvent.openUrl("https://github.com/MaliciousFiles/SMPItems/blob/main/README.md")));
        } else if (command.getName().equalsIgnoreCase("smpitem")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Command can only be used by players", NamedTextColor.RED));
            } else if (args.length == 0) {
                sender.sendMessage(Component.text("Invalid item", NamedTextColor.RED));
            } else {
                String input = args[0].toLowerCase();
                ItemStack item = customItems.get(input.startsWith("smpitems:") ? key(input.substring(9)) : key(input));
                if (item == null) {
                    sender.sendMessage(Component.text("Invalid item", NamedTextColor.RED));
                } else {
                    player.getInventory().addItem(item);
                }
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("smpitem")) return List.of();

        return customItems.keySet().stream()
                .map(NamespacedKey::toString)
                .filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase()) || id.substring(9).toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
    }

    // TODO: not working
    private static final UUID resourcePackID = UUID.fromString("7a6a0940-0a72-40c0-86da-ccd63079d31a");
    private static final byte[] resourcePackHash = HexFormat.of().parseHex("6a9216647479c1b462efbe52f8e0c0513e9e682d");
    @EventHandler
    public void onJoin(PlayerJoinEvent evt) {
        evt.getPlayer().addResourcePack(resourcePackID,
                "https://github.com/MaliciousFiles/SMPItems/raw/main/SMPItems%20Resource%20Pack.zip",
                resourcePackHash,
                "Resource pack to render custom items",
                true);
    }
}
