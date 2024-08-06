package io.github.maliciousfiles.smpitems.teleportationdevice;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.mojang.datafixers.util.Pair;
import io.github.maliciousfiles.smpitems.SMPItems;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.util.Mth;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class TeleportationDeviceHandler implements Listener {
    public static final ItemStack ANCHOR = SMPItems.createItemStack(new ItemStack(Material.LODESTONE), meta -> {
        meta.displayName(Component.text("Teleportation Anchor")
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Shift-right click with device to link")
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Set name in an anvil")
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.GRAY)
        ));
        meta.setMaxStackSize(64);
    });

    public static final ShapedRecipe BASIC_DEVICE_RECIPE = new ShapedRecipe(SMPItems.key("basic_teleportation_device"), TeleportationDevice.BASE.asItem())
            .shape(" SD",
                   "SPS",
                   "DS ")
            .setIngredient('S', Material.AMETHYST_SHARD)
            .setIngredient('D', Material.DIAMOND)
            .setIngredient('P', Material.ENDER_PEARL);

    static { SMPItems.addRecipe(BASIC_DEVICE_RECIPE); }

    public static final TeleportationDeviceRecipe EVOLVED_DEVICE_RECIPE = new TeleportationDeviceRecipe(
            device -> !device.isEvolved(),
            device -> !device.isEvolved(),
            device -> device.evolved().asItem(),
            TeleportationDevice.BASE.asItem(),
            SMPItems.createItemStack(TeleportationDevice.BASE.evolved().asItem(), meta -> {
                meta.lore(List.of(
                        Component.empty(),
                        Component.text("Can Take Final Upgrades: ")
                                .append(Component.text("yes", NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.GRAY),
                        Component.text("Range: ")
                                .append(Component.text("+500", NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.GRAY),
                        Component.text("Uses: ")
                                .append(Component.text("+5", NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.GRAY)
                ));
            }),
            SMPItems.key("evolved_teleportation_device"),
            List.of(
                    Pair.of(Material.AIR, 0),        Pair.of(Material.ECHO_SHARD, 1), Pair.of(Material.POPPED_CHORUS_FRUIT, 1),
                    Pair.of(Material.ECHO_SHARD, 1),        Pair.of(null, 0),         Pair.of(Material.ECHO_SHARD, 1),
                    Pair.of(Material.POPPED_CHORUS_FRUIT, 1), Pair.of(Material.ECHO_SHARD, 1), Pair.of(Material.AIR, 0)
            ));

    public static final TeleportationDeviceRecipe ANCHOR_RECIPE = new TeleportationDeviceRecipe(
            device -> !device.isEvolved(),
            device -> !device.isEvolved(),
            device -> ANCHOR.clone(),
            TeleportationDevice.BASE.asItem(),
            ANCHOR.clone(),
            SMPItems.key("teleportation_anchor"),
            List.of(
                    Pair.of(Material.DIAMOND, 1),            Pair.of(null, 0),           Pair.of(Material.DIAMOND, 1),
                    Pair.of(Material.AMETHYST_SHARD, 1), Pair.of(Material.LODESTONE, 1), Pair.of(Material.AMETHYST_SHARD, 1),
                    Pair.of(Material.DIAMOND, 1), Pair.of(Material.AMETHYST_SHARD, 1),   Pair.of(Material.DIAMOND, 1)
            ));

    private static final int MENU_TIME = 25;

    private static final ItemStack LINK_DEVICES_ITEM = SMPItems.createItemStack(TeleportationDevice.BASE.withName(
            Component.text("Link Devices")
                    .decoration(TextDecoration.ITALIC, false)
                    .color(NamedTextColor.AQUA)).asItem(), meta -> meta.lore(List.of()));

    @EventHandler
    public void getPearl(PlayerInventorySlotChangeEvent evt) {
        if (evt.getNewItemStack().getType().equals(Material.ENDER_PEARL)) evt.getPlayer().discoverRecipe(BASIC_DEVICE_RECIPE.getKey());
    }

    @EventHandler
    public void preCraft(PrepareItemCraftEvent evt) {
        if (Arrays.stream(evt.getInventory().getMatrix()).allMatch(i -> i == null || TeleportationDevice.fromItem(i) != null) &&
                Arrays.stream(evt.getInventory().getMatrix()).filter(i -> TeleportationDevice.fromItem(i) != null).count() > 1) {
            evt.getInventory().setResult(LINK_DEVICES_ITEM);
        }
    }

    @EventHandler
    public void postCraft(CraftItemEvent evt) {
        if (evt.getInventory().getResult().equals(LINK_DEVICES_ITEM)) {
            evt.setCurrentItem(ItemStack.empty());

            Map<TeleportationDevice, ItemStack> devices = new HashMap<>();
            for (ItemStack item : evt.getInventory().getMatrix()) {
                TeleportationDevice device = TeleportationDevice.fromItem(item);
                if (device != null) devices.put(device, item);
            }

            for (TeleportationDevice d1 : devices.keySet()) {
                for (TeleportationDevice d2 : devices.keySet()) {
                    if (d1 == d2) continue;

                    d1.setLinked(d2.getId());
                }

                d1.updateItem(devices.get(d1));
            }
        }

        if (evt.getRecipe() instanceof CraftingRecipe cr && cr.getKey().equals(BASIC_DEVICE_RECIPE.getKey())) {
            TeleportationDevice.initUUID(evt.getCurrentItem());
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent evt) {
        boolean isAnchor = ANCHOR.equals(evt.getInventory().getFirstItem());

        TeleportationDevice device;
        if ((device = TeleportationDevice.fromItem(evt.getInventory().getFirstItem())) != null || isAnchor) {
            if (evt.getInventory().getSecondItem() != null && (isAnchor || !evt.getInventory().getSecondItem().getType().equals(Material.ENDER_PEARL))) {
                evt.setResult(null);
                return;
            }
            if (evt.getResult() == null) evt.setResult(evt.getInventory().getFirstItem().clone());

            boolean[] renaming = new boolean[1];
            evt.getResult().editMeta(meta -> {
                String renameText = evt.getInventory().getRenameText();

                if (renameText.equals(
                        PlainTextComponentSerializer.plainText().serialize(
                                evt.getInventory().getFirstItem().getItemMeta().displayName()))) {
                    if (evt.getInventory().getSecondItem() == null) evt.setResult(null);
                    return;
                }

                renaming[0] = true;
                if (renameText.isEmpty()) {
                    renameText = PlainTextComponentSerializer.plainText().serialize(device.asItem().getItemMeta().displayName());
                }

                Component name = Component.text(renameText)
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false);
                if (!isAnchor) name = name.decorate(TextDecoration.BOLD);

                meta.displayName(name);
            });

            int[] healing = new int[1];
            if (evt.getInventory().getSecondItem() != null) {
                evt.getResult().editMeta(Damageable.class, meta -> {
                    healing[0] = Math.min(meta.getDamage(), evt.getInventory().getSecondItem().getAmount());
                    meta.setDamage(meta.getDamage()-healing[0]);
                    evt.getInventory().setRepairCostAmount(healing[0]);
                });
            }

            evt.getInventory().setRepairCost((renaming[0] ? 1 : 0) + healing[0]);
            device.withName(evt.getResult().getItemMeta().displayName()).updateItem(evt.getResult());
        }
    }

    // ANCHOR FUNCTIONALITY
    @EventHandler
    public void onPlace(BlockPlaceEvent evt) {
        if (evt.getItemInHand().isSimilar(ANCHOR)) {
            TeleportationDevice.setAnchor(evt.getBlock(), PlainTextComponentSerializer.plainText()
                    .serialize(evt.getItemInHand().getItemMeta().displayName()));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent evt) {
        if (TeleportationDevice.isAnchor(evt.getBlock())) TeleportationDevice.setAnchor(evt.getBlock(), null);
    }

    private enum ValidationAction { NONE, REMOVE_FAV, UNLINK }
    private static ValidationAction validateSelected(TeleportationDevice device, Object obj) {
        if (obj instanceof Location loc) {
            return TeleportationDevice.getAnchorName(loc).isEmpty() ? ValidationAction.REMOVE_FAV : ValidationAction.NONE;
        } if (obj instanceof UUID uuid) {
            Pair<Player, TeleportationDevice> pair = TeleportationDevice.getPlayerWithItem(uuid);

            return pair == null ? ValidationAction.REMOVE_FAV : !pair.getSecond().isLinked(device.getId()) ? ValidationAction.UNLINK : ValidationAction.NONE;
        }

        return ValidationAction.NONE;
    }

    private static boolean checkLocs(TeleportationDevice device, Player player, Location loc1, Location loc2) {
        if (device.getRange() == -1) return true;

        if (loc1.getWorld() != loc2.getWorld() || loc1.distanceSquared(loc2) > device.getRange() * device.getRange()) {
            player.sendActionBar(Component.text("Destination out of range").color(NamedTextColor.RED));
            return false;
        }

        return true;

    }

    private static boolean checkValidDestination(TeleportationDevice device, ItemStack item, Player player, boolean checkRange) {
        ValidationAction action = validateSelected(device, device.getSelected());
        if (action != ValidationAction.NONE) {
            String msg;
            if (device.getSelected() instanceof Location loc) {
                msg = "Invalid anchor, removed from device";
                device.toggleAnchor(loc).updateItem(item);
            } else {
                if (action == ValidationAction.REMOVE_FAV) {
                    msg = "Item not being held, removed from favorites";
                    device.removeFavorite(device.getSelected()).updateItem(item);
                } else {
                    msg = "Other device unlinked, removed from device";
                    device.toggleItemLink((UUID) device.getSelected()).updateItem(item);
                }
            }

            player.sendActionBar(Component.text(msg).color(NamedTextColor.RED));
            return false;
        }
        if (device.getSelected().equals(TeleportationDevice.NO_SELECTION)) {
            player.sendActionBar(Component.text("No destination selected").color(NamedTextColor.RED));
            return false;
        }
        if (checkRange) {
            if (device.getSelected() instanceof Location loc) {
                return checkLocs(device, player, player.getLocation(), loc);
            } else if (device.getSelected() instanceof UUID uuid) {
                return checkLocs(device, player, player.getLocation(), TeleportationDevice.getPlayerWithItem(uuid).getFirst().getLocation());
            }
        }

        return true;
    }

    private static void setActionBar(Object selected, Player player) {
        player.sendActionBar(selected instanceof Location loc ?
                Component.text(TeleportationDevice.getAnchorName(loc))
                        .color(NamedTextColor.AQUA)
                        .append(Component.text(" (%s, %s, %s)"
                                        .formatted(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()))
                                .color(NamedTextColor.GRAY)) :
                selected instanceof UUID uuid ?
                        Component.text(TeleportationDevice.getPlayerWithItem(uuid).getFirst().getName())
                                .color(NamedTextColor.AQUA) :
                        Component.empty());
    }

    // DEVICE FUNCTIONALITY
    @EventHandler
    public void onInteract(PlayerInteractEvent evt) {
        if (!evt.getAction().isRightClick()) return;

        TeleportationDevice device = TeleportationDevice.fromItem(evt.getItem());
        if (device == null) return;

        if (evt.getPlayer().getCooldown(evt.getItem().getType()) > 0) return;

        if (evt.getPlayer().isSneaking()) {
            evt.getPlayer().swingHand(evt.getHand());
            evt.setCancelled(true);

            if (TeleportationDevice.isAnchor(evt.getClickedBlock())) {
                Location loc = evt.getClickedBlock().getLocation();
                device.toggleAnchor(loc).updateItem(evt.getItem());

                evt.getPlayer().sendActionBar(device.hasAnchor(loc) ?
                        Component.text("Teleportation anchor added").color(NamedTextColor.GREEN) :
                        device.getNumAnchors() < device.getConnections() ?
                                Component.text("Teleportation anchor removed").color(NamedTextColor.YELLOW) :
                                Component.text("Teleportation device is at maximum capacity").color(NamedTextColor.RED));

                return;
            }

            evt.getPlayer().setShieldBlockingDelay(Integer.MAX_VALUE);
            evt.getPlayer().startUsingItem(evt.getHand());
            ((CraftPlayer) evt.getPlayer()).getHandle().useItemRemaining = MENU_TIME;
            return;
        }

        if (device.getSelected().equals(TeleportationDevice.NO_SELECTION)) {
            evt.getPlayer().sendActionBar(Component.text("No destination selected").color(NamedTextColor.RED));
            evt.setCancelled(true);
            return;
        }

        if (!checkValidDestination(device, evt.getItem(), evt.getPlayer(), true)) {
            evt.setCancelled(true);
            return;
        }

        Damageable meta = (Damageable) evt.getItem().getItemMeta();
        if (meta.getDamage() >= meta.getMaxDamage()) {
            evt.setCancelled(true);
            return;
        }

        setActionBar(device.getSelected(), evt.getPlayer());

        evt.getPlayer().setShieldBlockingDelay(Integer.MAX_VALUE);
        evt.getPlayer().startUsingItem(evt.getHand());
        ((CraftPlayer) evt.getPlayer()).getHandle().useItemRemaining = device.getUseTime() * 20;
    }

    private static final float PARTICLE_RADIUS = 0.7f;
    private static final float PARTICLE_HEIGHTS_START = 5f;
    private static final float PARTICLE_HEIGHTS_END = 8f;
    private static final float PARTICLE_HEIGHT_BUFFER = 0.15f;
    private static final float PARTICLE_REVOLUTIONS_PER_HEIGHT_START = 2;
    private static final float PARTICLE_REVOLUTIONS_PER_HEIGHT_END = 7;
    private static final int PARTICLE_STARTUP_TIME = 8;
    private static final float PARTICLE_SPEED_START = 1.3f;
    private static final float PARTICLE_SPEED_END = 2f;

    @EventHandler
    public void onTick(ServerTickStartEvent evt) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isSneaking()) continue;

            TeleportationDevice device;
            if ((device = TeleportationDevice.fromItem(player.getActiveItem())) == null) continue;

            int ticks = player.getActiveItemRemainingTime();
            int useTime = device.getUseTime()*20 - PARTICLE_STARTUP_TIME;

            if (ticks % 20 == 12 && ticks > 40 || ticks == 40) {
                player.spawnParticle(Particle.PORTAL,
                        player.getLocation().add(0, 1, 0),
                        17, 0, 0, 0, 0.7);
            }

            float rawPerc = (float) Math.pow(40, -(float) ticks / useTime);

            float speed = PARTICLE_SPEED_START + (PARTICLE_SPEED_END - PARTICLE_SPEED_START) * rawPerc;

            if (ticks > useTime) continue;
            float playerHeight = (float) player.getHeight() + PARTICLE_HEIGHT_BUFFER*2;
            for (float tickMod = -10; tickMod <= 0; tickMod += 1.3f/speed) {
                float percent = speed - (speed*ticks+tickMod)/useTime;

                float numHeights = PARTICLE_HEIGHTS_START + (PARTICLE_HEIGHTS_END - PARTICLE_HEIGHTS_START) * rawPerc;
                float revolutions = PARTICLE_REVOLUTIONS_PER_HEIGHT_START + (PARTICLE_REVOLUTIONS_PER_HEIGHT_END - PARTICLE_REVOLUTIONS_PER_HEIGHT_START) * rawPerc;

                float height = (numHeights*playerHeight*percent) % playerHeight;
                float rad = height/playerHeight * 2*Mth.PI*revolutions;
                player.spawnParticle(Particle.ELECTRIC_SPARK,
                        player.getLocation().add(
                                PARTICLE_RADIUS*Mth.sin(rad),
                                -PARTICLE_HEIGHT_BUFFER+height,
                                PARTICLE_RADIUS*Mth.cos(rad)),
                        1, 0, 0, 0, 0);
            }
        }
    }

    @EventHandler
    public void onOpenInv(InventoryOpenEvent evt) {
        evt.getPlayer().clearActiveItem();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent evt) {
        TeleportationDevice device;
        if (evt.getEntity() instanceof Player player &&
                (device = TeleportationDevice.fromItem(player.getActiveItem())) != null &&
                !device.hasUpgrade(TeleportationDevice.UpgradeType.FINAL_USE_TIME)) {
            player.clearActiveItem();
        }
    }

    @EventHandler
    public void onStopUsingItem(PlayerStopUsingItemEvent evt) {
        TeleportationDevice device;
        if ((device =TeleportationDevice.fromItem(evt.getItem())) == null) return;

        evt.getPlayer().setShieldBlockingDelay(((CraftWorld) evt.getPlayer().getWorld()).getHandle().paperConfig().misc.shieldBlockingDelay);

        if (evt.getPlayer().isSneaking()) {
            if (device.getFavorites().isEmpty()) {
                evt.getPlayer().sendActionBar(Component.text("No favorites set").color(NamedTextColor.RED));
                return;
            }

            List<Object> favorites = device.getFavorites();
            Object fav = favorites.get((favorites.indexOf(device.getSelected()) + 1) % favorites.size());

            device.select(fav).updateItem(evt.getItem());

            if (!checkValidDestination(device, evt.getItem(), evt.getPlayer(), false)) return;
            setActionBar(fav, evt.getPlayer());
        }
    }

    @EventHandler
    public void onToggleSneaking(PlayerToggleSneakEvent evt) {
        if (TeleportationDevice.fromItem(evt.getPlayer().getActiveItem()) != null) evt.getPlayer().clearActiveItem();
    }

    @EventHandler
    public void consumeItemEvent(PlayerItemConsumeEvent evt) {
        TeleportationDevice device;
        if ((device = TeleportationDevice.fromItem(evt.getItem())) == null) return;

        if (evt.getPlayer().isSneaking()) {
            ItemStack item = evt.getPlayer().getInventory().getItem(evt.getHand());
            TeleportationMenuHandler.openMenu(device,
                    item, evt.getPlayer());
            return;
        }

        if (!checkValidDestination(device, evt.getItem(), evt.getPlayer(), true)) return;

        evt.getPlayer().setCooldown(evt.getItem().getType(), 10);
        evt.getPlayer().spawnParticle(Particle.REVERSE_PORTAL,
                evt.getPlayer().getLocation().add(0, 1, 0),
                20, 0, 0, 0, 5);

        evt.getPlayer().setFallDistance(0);
        if (device.getSelected() instanceof Location loc) {
            evt.getPlayer().teleport(loc.clone().add(0.5, 1, 0.5));
        } else if (device.getSelected() instanceof UUID uuid) {
            evt.getPlayer().teleport(TeleportationDevice.getPlayerWithItem(uuid).getFirst());
        }

        if (evt.getPlayer().getGameMode() != GameMode.CREATIVE) {
            ItemStack item = evt.getPlayer().getInventory().getItem(evt.getHand());

            item.editMeta(Damageable.class, meta -> meta.setDamage(meta.getDamage() + 1));
            device.updateItem(item);
        }
    }
}
