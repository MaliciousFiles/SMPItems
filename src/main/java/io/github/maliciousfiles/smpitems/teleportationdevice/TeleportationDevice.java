package io.github.maliciousfiles.smpitems.teleportationdevice;

import io.github.maliciousfiles.smpitems.SMPItems;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeleportationDevice implements Listener {

    private static final NamespacedKey RANGE = SMPItems.key("range");
    private static final NamespacedKey CONNECTIONS = SMPItems.key("connections");
    private static final NamespacedKey ANCHORS = SMPItems.key("anchors");
    private static final NamespacedKey ITEMS = SMPItems.key("items");
    private static final NamespacedKey USE_TIME = SMPItems.key("use_time");
    private static final NamespacedKey UPGRADEABLE = SMPItems.key("upgradeable");

    private static final PersistentDataType<List<PersistentDataContainer>, List<Location>> LOC_LIST = PersistentDataType.LIST.listTypeFrom(new LocationPersistentDataType());

    private static final ItemStack BASE_DEVICE = new ItemStack(Material.ENDER_EYE);
    private static final ItemStack EVOLVED_DEVICE = new ItemStack(Material.ENDER_EYE);
    private static final ItemStack ANCHOR = new ItemStack(Material.LODESTONE);

    static {
        ItemMeta meta = BASE_DEVICE.getItemMeta();
        meta.displayName(Component.text("Basic Teleportation Device")
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD)
                .color(NamedTextColor.AQUA));
        setData((Damageable) meta, 5, 250, 1, 10, false);
        meta.setMaxStackSize(1);
        BASE_DEVICE.setItemMeta(meta);

        meta = EVOLVED_DEVICE.getItemMeta();
        meta.displayName(Component.text("Evolved Teleportation Device")
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD)
                .color(NamedTextColor.AQUA));
        setData((Damageable) meta, 20, 750, 1, 10, true);
        meta.setMaxStackSize(1);
        EVOLVED_DEVICE.setItemMeta(meta);

        meta = ANCHOR.getItemMeta();
        meta.displayName(Component.text("Teleportation Anchor")
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.AQUA));
        meta.setMaxStackSize(64);
        ANCHOR.setItemMeta(meta);
    }

    private static void updateLore(ItemStack item, int line, String text) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        lore.set(line, Component.text(text));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private static void setData(Damageable meta, Integer uses, Integer range, Integer connections, Integer useTime, Boolean upgradeable) {
        meta.setMaxDamage(uses+1);

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(RANGE, PersistentDataType.INTEGER, range);
        container.set(CONNECTIONS, PersistentDataType.INTEGER, connections);
        container.set(USE_TIME, PersistentDataType.INTEGER, useTime);
        container.set(UPGRADEABLE, PersistentDataType.BOOLEAN, upgradeable);

        meta.lore(List.of(
                Component.empty(),
                Component.text("Range: ")
                        .append(Component.text(range, NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.GRAY),
                Component.text("Use Time: ")
                        .append(Component.text(useTime, NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.GRAY),
                Component.text("Upgradeable: ")
                        .append(Component.text(upgradeable ? "yes" : "no", NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Anchors: 0/%s".formatted(connections))
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Uses: %s/%s".formatted(uses-meta.getDamage(), uses))
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.GRAY)
                ));
    }

    public static List<CraftingRecipe> getRecipes() {
        return List.of(
                new ShapedRecipe(SMPItems.key("basic_teleportation_device"), BASE_DEVICE)
                        .shape(" SD",
                               "SPS",
                               "DS ")
                        .setIngredient('S', Material.AMETHYST_SHARD)
                        .setIngredient('D', Material.DIAMOND)
                        .setIngredient('P', Material.ENDER_PEARL),
                new ShapedRecipe(SMPItems.key("evolved_teleportation_device"), EVOLVED_DEVICE)
                        .shape(" SC",
                               "SDS",
                               "CS ")
                        .setIngredient('S', Material.ECHO_SHARD)
                        .setIngredient('C', Material.POPPED_CHORUS_FRUIT)
                        .setIngredient('D', BASE_DEVICE),
                new ShapedRecipe(SMPItems.key("teleportation_anchor"), ANCHOR)
                        .shape("DPD",
                               "SLS",
                               "DSD")
                        .setIngredient('S', Material.AMETHYST_SHARD)
                        .setIngredient('P', BASE_DEVICE)
                        .setIngredient('L', Material.LODESTONE)
                        .setIngredient('D', Material.DIAMOND)
        );
    }

    private static boolean isAnchor(Block block) {
        return block.getMetadata("teleportation_anchor").stream().anyMatch(v -> v.getOwningPlugin() == SMPItems.instance);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent evt) {
        if (evt.getAction() == Action.RIGHT_CLICK_AIR || evt.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (BASE_DEVICE.equals(evt.getItem()) || EVOLVED_DEVICE.equals(evt.getItem())) {
                PersistentDataContainerView pdc = evt.getItem().getItemMeta().getPersistentDataContainer();
                if (evt.getPlayer().isSneaking() && evt.getClickedBlock() != null && isAnchor(evt.getClickedBlock())) {
                    List<Location> anchors = evt.getItem().getPersistentDataContainer().getOrDefault(ANCHORS, LOC_LIST, new ArrayList<>());

                    Location loc = evt.getClickedBlock().getLocation();
                    if (anchors.contains(loc)) {
                        anchors.remove(loc);
                        evt.getPlayer().sendActionBar(Component.text("Teleportation anchor removed", NamedTextColor.GREEN));
                    } else {
                        int maxConnections = pdc.get(CONNECTIONS, PersistentDataType.INTEGER);

                        if (anchors.size() < maxConnections) {
                            anchors.add(loc);

                            updateLore(evt.getItem(), 5, "Anchors: %s/%s".formatted(anchors.size(), maxConnections));

                            evt.getPlayer().sendActionBar(Component.text("Teleportation anchor added", NamedTextColor.GREEN));
                        } else {
                            evt.getPlayer().sendActionBar(Component.text("Teleportation device is at maximum capacity", NamedTextColor.RED));
                        }
                    }
                }


                evt.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent evt) {
        if (evt.getItemInHand().equals(ANCHOR)) {
            evt.getBlock().setMetadata("teleportation_anchor", new FixedMetadataValue(SMPItems.instance, true));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent evt) {
        evt.getPlayer().discoverRecipes(getRecipes().stream().map(CraftingRecipe::getKey).toList());
    }

    private static class LocationPersistentDataType implements PersistentDataType<PersistentDataContainer, Location> {

        private static final NamespacedKey WORLD = SMPItems.key("world");
        private static final NamespacedKey X = SMPItems.key("x");
        private static final NamespacedKey Y = SMPItems.key("y");
        private static final NamespacedKey Z = SMPItems.key("z");
        private static final NamespacedKey YAW = SMPItems.key("yaw");
        private static final NamespacedKey PITCH = SMPItems.key("pitch");

        @Override
        public @NotNull Class<PersistentDataContainer> getPrimitiveType() {
            return PersistentDataContainer.class;
        }

        @Override
        public @NotNull Class<Location> getComplexType() {
            return Location.class;
        }

        @Override
        public @NotNull PersistentDataContainer toPrimitive(@NotNull Location complex, @NotNull PersistentDataAdapterContext context) {
            PersistentDataContainer pdc = context.newPersistentDataContainer();

            pdc.set(WORLD, PersistentDataType.STRING, complex.getWorld().getUID().toString());
            pdc.set(X, PersistentDataType.DOUBLE, complex.getX());
            pdc.set(Y, PersistentDataType.DOUBLE, complex.getY());
            pdc.set(Z, PersistentDataType.DOUBLE, complex.getZ());
            pdc.set(YAW, PersistentDataType.FLOAT, complex.getYaw());
            pdc.set(PITCH, PersistentDataType.FLOAT, complex.getPitch());

            return pdc;
        }

        @Override
        public @NotNull Location fromPrimitive(@NotNull PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            return new Location(
                    Bukkit.getWorld(UUID.fromString(primitive.get(WORLD, PersistentDataType.STRING))),
                    primitive.get(X, PersistentDataType.DOUBLE),
                    primitive.get(Y, PersistentDataType.DOUBLE),
                    primitive.get(Z, PersistentDataType.DOUBLE),
                    primitive.get(YAW, PersistentDataType.FLOAT),
                    primitive.get(PITCH, PersistentDataType.FLOAT)
            );
        }
    }
}
