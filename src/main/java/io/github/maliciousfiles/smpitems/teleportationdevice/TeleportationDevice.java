package io.github.maliciousfiles.smpitems.teleportationdevice;

import io.github.maliciousfiles.smpitems.SMPItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class TeleportationDevice {

    private static final ItemStack BASE_DEVICE = new ItemStack(Material.ENDER_EYE);

    static {
        ItemMeta meta = BASE_DEVICE.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(1);
            damageable.setMaxDamage(5);
        }
        meta.displayName(Component.text("Basic Teleportation Device")
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD)
                .color(NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.empty(),
                Component.text("  Uses    Range    Use Time    Connections")
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.GRAY),
                Component.text("   5       250       10s    1 player + 1 anchor")
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.GRAY)
        ));
//        meta.lore(List.of(
//                Component.empty(),
//                Component.text("Uses:            ")
//                        .append(Component.text("5", NamedTextColor.WHITE))
//                        .decoration(TextDecoration.ITALIC, false)
//                        .color(NamedTextColor.GRAY),
//                Component.text("Range:           ")
//                        .append(Component.text("250", NamedTextColor.WHITE))
//                        .append(Component.text(" blocks"))
//                        .decoration(TextDecoration.ITALIC, false)
//                        .color(NamedTextColor.GRAY),
//                Component.text("Connections:    ")
//                        .append(Component.text("1", NamedTextColor.WHITE))
//                        .append(Component.text(" player + "))
//                        .append(Component.text("1", NamedTextColor.WHITE))
//                        .append(Component.text(" anchor"))
//                        .decoration(TextDecoration.ITALIC, false)
//                        .color(NamedTextColor.GRAY),
//                Component.text("Use Time:        ")
//                        .append(Component.text("1", NamedTextColor.WHITE))
//                        .append(Component.text("s"))
//                        .decoration(TextDecoration.ITALIC, false)
//                        .color(NamedTextColor.GRAY)
//                ));
        BASE_DEVICE.setItemMeta(meta);
    }

    public static List<Recipe> getRecipes() {
        return List.of(
                new ShapedRecipe(SMPItems.key("basic_teleportation_device"), BASE_DEVICE)
                        .shape(" SD",
                               "SPS",
                               "DS ")
                        .setIngredient('S', Material.AMETHYST_SHARD)
                        .setIngredient('D', Material.DIAMOND)
                        .setIngredient('P', Material.ENDER_PEARL)
        );
    }
}
