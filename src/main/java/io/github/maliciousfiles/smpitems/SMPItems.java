package io.github.maliciousfiles.smpitems;

import com.mojang.datafixers.util.Pair;
import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDevice;
import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDeviceHandler;
import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationDeviceLinkHandler;
import io.github.maliciousfiles.smpitems.teleportationdevice.TeleportationMenuHandler;
import io.github.maliciousfiles.smpitems.wand.WandHandler;
import io.papermc.paper.plugin.entrypoint.Entrypoint;
import io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler;
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
import java.util.function.Supplier;
import java.util.logging.Level;

public final class SMPItems extends JavaPlugin implements Listener {

    public static final int MODEL_DATA_BASE = 9123487;

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

    public interface ItemSupplier { ItemStack get(String... args); }
    private static final Map<NamespacedKey, Pair<ItemSupplier, List<String>[]>> customItems = new HashMap<>();
    public static void addItem(String id, ItemSupplier supplier, List<String>... suggestions) {
        customItems.put(key(id), Pair.of(supplier, suggestions));
    }

    @Override
    public void onEnable() {
        instance = this;

        Bukkit.getResourcePack();

        TeleportationDevice.UpgradeType.initAll();
        TeleportationDeviceLinkHandler.init();
        Bukkit.getPluginManager().registerEvents(new TeleportationDeviceHandler(), this);
        Bukkit.getPluginManager().registerEvents(new TeleportationMenuHandler(), this);
        Bukkit.updateRecipes();

        Bukkit.getPluginManager().registerEvents(new WandHandler(), this);

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        Bukkit.broadcastMessage("disable");
        TeleportationMenuHandler.disable();
        WandHandler.disable();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("recipes")) {
            sender.sendMessage(Component.text("Click to open recipes in browser")
                    .decorate(TextDecoration.UNDERLINED)
                    .color(NamedTextColor.BLUE)
                    .clickEvent(ClickEvent.openUrl("https://github.com/MaliciousFiles/SMPItems/blob/main/README.md")));
            sender.sendMessage(Component.text("All recipes are in the in-game recipe book (though don't show as craftable)")
                    .color(NamedTextColor.GRAY));
        } else if (command.getName().equalsIgnoreCase("smpitem")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Command can only be used by players", NamedTextColor.RED));
            } else if (args.length == 0) {
                sender.sendMessage(Component.text("Invalid item", NamedTextColor.RED));
            } else {
                String input = args[0].toLowerCase();
                Pair<ItemSupplier, List<String>[]> info = customItems.get(input.startsWith("smpitems:") ? key(input.substring(9)) : key(input));
                if (info == null) {
                    sender.sendMessage(Component.text("Invalid item", NamedTextColor.RED));
                } else {
                    if (args.length != info.getSecond().length+1) {
                        sender.sendMessage(Component.text("Incorrect number of arguments", NamedTextColor.RED));
                    } else {
                        for (int i = 1; i < args.length; i++) {
                            String arg = args[i];
                            if (info.getSecond()[i-1].stream().noneMatch(s -> s.equalsIgnoreCase(arg))) {
                                sender.sendMessage(Component.text("Invalid argument (%s)".formatted(args[i]), NamedTextColor.RED));
                                return true;
                            }
                        }

                        player.getInventory().addItem(info.getFirst().get(Arrays.stream(args, 1, args.length)
                                .map(String::toLowerCase).toArray(String[]::new)));
                    }
                }
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("smpitem")) return List.of();

        if (args.length == 1) {
            return customItems.keySet().stream()
                    .map(NamespacedKey::toString)
                    .filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase()) || id.substring(9).toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        } else {
            Pair<ItemSupplier, List<String>[]> info = customItems.get(key(args[0].startsWith("smpitems:") ? args[0].substring(9) : args[0]));
            if (info == null) return List.of();

            int itemArgsIdx = args.length-2;
            if (itemArgsIdx >= info.getSecond().length) return List.of();

            return info.getSecond()[itemArgsIdx].stream()
                    .filter(s -> s.toLowerCase().startsWith(args[itemArgsIdx+1].toLowerCase()))
                    .toList();
        }
    }

    private static final UUID resourcePackID = UUID.fromString("cbaee74b-93e4-4f13-946e-65024985a6b4");
    private static final byte[] resourcePackHash = HexFormat.of().parseHex("87d3b8e6a41a12af7091d349e0fd931ec6e14734");
    @EventHandler
    public void onJoin(PlayerJoinEvent evt) {
        evt.getPlayer().addResourcePack(resourcePackID,
                "https://github.com/MaliciousFiles/SMPItems/raw/main/SMPItems%20Resource%20Pack.zip",
                resourcePackHash,
                "Resource pack to render custom items",
                true);
    }
}
