package io.github.maliciousfiles.smpitems.teleportationdevice;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
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
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class TeleportationDeviceHandler implements Listener {
    public static final ItemStack ANCHOR = SMPItems.createItemStack(TeleportationDevice.initAnchor(new ItemStack(Material.LODESTONE)), meta -> {
        meta.displayName(Component.text("Teleportation Anchor")
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Shift-right click with teleporter to link")
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

    static {
        SMPItems.addRecipe(BASIC_DEVICE_RECIPE);

        SMPItems.addItem("teleportation_anchor", () -> ANCHOR);
        SMPItems.addItem("teleporter", () -> TeleportationDevice.initUUID(TeleportationDevice.BASE.asItem()));
        SMPItems.addItem("evolved_teleporter", () -> TeleportationDevice.initUUID(TeleportationDevice.BASE.evolved().asItem()));
    }

    public static final TeleportationDeviceRecipe EVOLVED_DEVICE_RECIPE = new TeleportationDeviceRecipe(
            device -> !device.isEvolved(),
            device -> !device.isEvolved(),
            device -> device.evolved().asItem(),
            new RecipeChoice.ExactChoice(TeleportationDevice.BASE.asItem()),
            SMPItems.createItemStack(TeleportationDevice.BASE.evolved().asItem(), meta -> {
                meta.lore(List.of(
                        Component.empty(),
                        Component.text("Range: ")
                                .append(Component.text("+500", NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.GRAY),
                        Component.text("Uses: ")
                                .append(Component.text("+5", NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.GRAY),
                        Component.text("Can take final upgrades")
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
            device -> !device.isEvolved() && !device.hasAnyUpgrade(),
            device -> ANCHOR.clone(),
            new RecipeChoice.ExactChoice(TeleportationDevice.BASE.asItem()),
            ANCHOR.clone(),
            SMPItems.key("teleportation_anchor"),
            List.of(
                    Pair.of(Material.DIAMOND, 1),            Pair.of(null, 0),           Pair.of(Material.DIAMOND, 1),
                    Pair.of(Material.AMETHYST_SHARD, 1), Pair.of(Material.LODESTONE, 1), Pair.of(Material.AMETHYST_SHARD, 1),
                    Pair.of(Material.DIAMOND, 1), Pair.of(Material.AMETHYST_SHARD, 1),   Pair.of(Material.DIAMOND, 1)
            ));

    private static final int MENU_TIME = 15;

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
    public void postCraft(InventoryClickEvent evt) {
        if (!(evt.getInventory() instanceof CraftingInventory inv) ||
                evt.getSlotType() != InventoryType.SlotType.RESULT ||
                !LINK_DEVICES_ITEM.equals(evt.getCurrentItem())) return;
        evt.setCancelled(true);
        inv.setResult(ItemStack.empty());

        Map<TeleportationDevice, ItemStack> devices = new HashMap<>();
        for (ItemStack item : inv.getMatrix()) {
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

    @EventHandler
    public void postCraft(CraftItemEvent evt) {
        if (evt.getRecipe() instanceof CraftingRecipe cr && cr.getKey().equals(BASIC_DEVICE_RECIPE.getKey())) {
            Bukkit.getScheduler().runTask(SMPItems.instance, () -> {
                for (ItemStack item : evt.getWhoClicked().getInventory().getContents()) {
                    if (TeleportationDevice.isUninnitedItem(item)) TeleportationDevice.initUUID(item);
                }
                if (TeleportationDevice.isUninnitedItem(evt.getWhoClicked().getItemOnCursor())) TeleportationDevice.initUUID(evt.getWhoClicked().getItemOnCursor());
            });
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent evt) {
        boolean isAnchor = TeleportationDevice.isAnchor(evt.getInventory().getFirstItem());

        TeleportationDevice device;
        if ((device = TeleportationDevice.fromItem(evt.getInventory().getFirstItem())) != null || isAnchor) {
            if (evt.getInventory().getSecondItem() != null &&
                    (isAnchor || !evt.getInventory().getSecondItem().getType().equals(Material.ENDER_PEARL) ||
                            ((Damageable) evt.getInventory().getFirstItem().getItemMeta()).getDamage() == 0)) {
                evt.setResult(null);
                return;
            }
            if (evt.getResult() == null) evt.setResult(evt.getInventory().getFirstItem().clone());

            boolean[] renaming = new boolean[1];
            evt.getResult().editMeta(meta -> {
                String renameText = evt.getInventory().getRenameText();

                if (renameText.isEmpty()) {
                    renameText = PlainTextComponentSerializer.plainText().serialize((device != null ? device.asItem() : ANCHOR).getItemMeta().displayName());
                }
                if (renameText.equals(
                        PlainTextComponentSerializer.plainText().serialize(
                                evt.getInventory().getFirstItem().getItemMeta().displayName()))) {
                    if (evt.getInventory().getSecondItem() == null) evt.setResult(null);
                    return;
                }

                renaming[0] = true;

                Component name = Component.text(renameText)
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false);
                if (!isAnchor) name = name.decorate(TextDecoration.BOLD);

                meta.displayName(name);
            });


            int[] healing = new int[1];
            if (evt.getInventory().getSecondItem() != null) {
                healing[0] = Math.min(device.getDamage(), evt.getInventory().getSecondItem().getAmount());
                if (healing[0] > 0) evt.getInventory().setRepairCostAmount(healing[0]);
            } else if (!renaming[0]) return;

            evt.getInventory().setRepairCost((renaming[0] ? 1 : 0) + healing[0]);
            if (device != null) device.damage((int) (-healing[0] * 0.2 * device.getUses())).withName(evt.getResult().getItemMeta().displayName()).updateItem(evt.getResult());
        }
    }

    // ANCHOR FUNCTIONALITY
    @EventHandler
    public void onPlace(BlockPlaceEvent evt) {
        if (TeleportationDevice.isAnchor(evt.getItemInHand())) {
            TextDisplay display = (TextDisplay) evt.getBlock().getWorld().spawnEntity(evt.getBlock().getLocation().add(0.5, 1.25, 0.5), EntityType.TEXT_DISPLAY);
            display.text(evt.getItemInHand().getItemMeta().displayName());
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.CENTER);

            TeleportationDevice.setAnchor(evt.getBlock(), display.getUniqueId());
        }
    }

    @EventHandler
    public void onAnchorExplode(EntityExplodeEvent evt) {
        evt.blockList().removeIf(TeleportationDevice::isAnchor);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent evt) {
        if (TeleportationDevice.isAnchor(evt.getBlock()) && !evt.isDropItems()) {
            TeleportationDevice.setAnchor(evt.getBlock(), null);
        }
    }

    @EventHandler
    public void onDrop(BlockDropItemEvent evt) {
        if (TeleportationDevice.isAnchor(evt.getBlock())) {
            if (!evt.getItems().isEmpty()) evt.getItems().getFirst().setItemStack(SMPItems.createItemStack(ANCHOR, meta -> {
                meta.displayName(Component.text(TeleportationDevice.getAnchorName(evt.getBlock().getLocation()))
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.AQUA));
            }));

            TeleportationDevice.setAnchor(evt.getBlock(), null);
        }
    }

    private enum ValidationAction { NONE, REMOVE_FAV, UNLINK }
    private static ValidationAction validateSelected(TeleportationDevice device, Object obj) {
        if (obj instanceof Location loc) {
            return TeleportationDevice.getAnchorName(loc).isEmpty() ? ValidationAction.REMOVE_FAV : ValidationAction.NONE;
        } if (obj instanceof UUID uuid) {
            Pair<Player, TeleportationDevice> pair = device.getAndUpdatePlayer(uuid);

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

    private static boolean checkValidDestination(TeleportationDevice device, ItemStack item, Player player, boolean checkRange, boolean showParticles) {
        ValidationAction action = validateSelected(device, device.getSelected());
        if (action != ValidationAction.NONE) {
            String msg;
            if (device.getSelected() instanceof Location loc) {
                msg = "Invalid anchor, removed from device";
                device.toggleAnchor(loc).updateItem(item);
            } else {
                if (action == ValidationAction.REMOVE_FAV) {
                    msg = "Item not being held by a player";
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

        Location destLoc = null;
        if (device.getSelected() instanceof Location loc) {
            for (int i = 1; i <= 2; i++) {
                Block block = loc.clone().add(0, i, 0).getBlock();
                if (block.getType().isSolid() || block.getType() == Material.LAVA) {
                    player.sendActionBar(Component.text("Anchor obstructed").color(NamedTextColor.RED));
                    return false;
                }
            }

            destLoc = loc.clone().add(0.5, 0, 0.5);
        } else if (device.getSelected() instanceof UUID uuid) {
            Player other = device.getAndUpdatePlayer(uuid).getFirst();

            if (other.equals(player)) {
                player.sendActionBar(Component.text("Cannot teleport to self").color(NamedTextColor.RED));
                return false;
            }

            destLoc = other.getLocation();
        }

        if (showParticles) {
            if (player.getLocation().getWorld().equals(destLoc.getWorld())) {
                Vector direction = destLoc.toVector().subtract(player.getLocation().toVector()).normalize();

                for (float dist = 0.6f; dist < 2.4f; dist += 0.3f) {
                    if (dist*dist > player.getLocation().distanceSquared(destLoc)) break;

                    player.spawnParticle(Particle.END_ROD, player.getLocation().add(0,1.3,0)
                                    .add(direction.clone().multiply(dist)),
                            1, 0, 0, 0, 0);
                }
            }
        }

        if (checkRange) return checkLocs(device, player, player.getLocation(), destLoc);

        return true;
    }

    private static void setActionBar(TeleportationDevice device, Object obj, Player player) {
        Component bar = Component.empty();
        if (obj instanceof Location loc) {
            String name = TeleportationDevice.getAnchorName(loc);
            bar = Component.text(name, NamedTextColor.AQUA);

            if (name.equals("Teleportation Anchor")) {
                bar = bar.append(Component.text(" (%s, %s, %s)"
                        .formatted(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), NamedTextColor.GRAY));
            }
        } else if (obj instanceof UUID uuid) {
            bar = Component.text(device.getAndUpdatePlayer(uuid).getFirst().getName(), NamedTextColor.AQUA);
        }

        player.sendActionBar(bar);
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
                        device.getConnections() == -1 || device.getNumAnchors() < device.getConnections() ?
                                Component.text("Teleportation anchor removed").color(NamedTextColor.YELLOW) :
                                Component.text("Teleportation device is at maximum capacity").color(NamedTextColor.RED));

                return;
            }

            evt.getPlayer().setShieldBlockingDelay(Integer.MAX_VALUE);
            evt.getPlayer().startUsingItem(evt.getHand());
            ((CraftPlayer) evt.getPlayer()).getHandle().useItemRemaining = MENU_TIME;
            return;
        }

        BukkitRunnable cancel = new BukkitRunnable() { public void run() { evt.getPlayer().clearActiveItem(); } };

        if (device.getSelected().equals(TeleportationDevice.NO_SELECTION)) {
            evt.getPlayer().sendActionBar(Component.text("No destination selected").color(NamedTextColor.RED));
            cancel.runTask(SMPItems.instance);
            return;
        }

        if (!checkValidDestination(device, evt.getItem(), evt.getPlayer(), true, true)) {
            cancel.runTask(SMPItems.instance);
            return;
        }

        if (device.isBroken()) {
            evt.getPlayer().sendActionBar(Component.text("Out of ender pearls").color(NamedTextColor.RED));
            cancel.runTask(SMPItems.instance);
            return;
        }

        setActionBar(device, device.getSelected(), evt.getPlayer());

        evt.getPlayer().setShieldBlockingDelay(Integer.MAX_VALUE);
        evt.getPlayer().startUsingItem(evt.getHand());
        ((CraftPlayer) evt.getPlayer()).getHandle().useItemRemaining = device.getUseTime() * 20;
    }

    private static final float PARTICLE_RADIUS = 0.7f;
    private static final float PARTICLE_HEIGHTS_START = 5f;
    private static final float PARTICLE_HEIGHTS_END = 8f;
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

            Location destLoc = null;
            boolean isPlayer = false;

            if (device.getSelected() instanceof Location loc) {
                destLoc = loc.clone().add(0.5, 1, 0.5);
            } else if (device.getSelected() instanceof UUID uuid) {
                destLoc = device.getAndUpdatePlayer(uuid).getFirst().getLocation();
                isPlayer = true;
            }

            Location[] locs = new Location[] { player.getLocation(), destLoc };
            for (int i = 0; i < locs.length; i++) {
                Location loc = locs[i];

                int ticks = player.getActiveItemRemainingTime();
                int useTime = device.getUseTime()*20 - PARTICLE_STARTUP_TIME;

                if (ticks % 20 == 12 && ticks > 40 || ticks == 40) {
                    loc.getWorld().spawnParticle(Particle.PORTAL,
                            loc.clone().add(0, 1, 0),
                            17, 0, 0, 0, 0.7);
                }

                boolean atPlayer = i > 0 && isPlayer;
                if (atPlayer && ticks % 7 != 0) continue;

                float rawPerc = (float) Math.pow(40, -(float) ticks / useTime);

                float speed = PARTICLE_SPEED_START + (PARTICLE_SPEED_END - PARTICLE_SPEED_START) * rawPerc;

                if (ticks > useTime) continue;
                float playerHeight = (float) player.getHeight();
                for (float tickMod = atPlayer ? 0 : -10; tickMod <= 0; tickMod += 1.3f/speed) {
                    float percent = speed - (speed*ticks+tickMod)/useTime;

                    float numHeights = PARTICLE_HEIGHTS_START + (PARTICLE_HEIGHTS_END - PARTICLE_HEIGHTS_START) * rawPerc;
                    float revolutions = PARTICLE_REVOLUTIONS_PER_HEIGHT_START + (PARTICLE_REVOLUTIONS_PER_HEIGHT_END - PARTICLE_REVOLUTIONS_PER_HEIGHT_START) * rawPerc;

                    float height = atPlayer ? (float) Math.random()*playerHeight : (numHeights*playerHeight*percent) % playerHeight;
                    float rad = atPlayer ? (float) Math.random()*2*Mth.PI : height/playerHeight * 2*Mth.PI*revolutions;
                    loc.getWorld().spawnParticle(atPlayer ? Particle.END_ROD : Particle.ELECTRIC_SPARK,
                            loc.clone().add(
                                    PARTICLE_RADIUS*Mth.sin(rad),
                                    height,
                                    PARTICLE_RADIUS*Mth.cos(rad)),
                            1, 0, 0, 0, 0);
                }
            }
        }
    }

    @EventHandler
    public void onEvolvedDestroy(EntityDamageEvent evt) {
        TeleportationDevice device;
        if (evt.getEntity() instanceof Item item && (device = TeleportationDevice.fromItem(item.getItemStack())) != null
                && device.isEvolved()) {
            evt.setCancelled(true);
        }
    }

    @EventHandler
    public void onEvolvedDespawn(ItemDespawnEvent evt) {
        TeleportationDevice device;
        if ((device = TeleportationDevice.fromItem(evt.getEntity().getItemStack())) != null && device.isEvolved()) {
            evt.setCancelled(true);
        }
    }

    @EventHandler
    public void onMiddleClick(InventoryClickEvent evt) {
        if (evt.getClick() == ClickType.MIDDLE && evt.getCursor().isEmpty() && TeleportationDevice.fromItem(evt.getCurrentItem()) != null) {
            Bukkit.getScheduler().runTask(SMPItems.instance, () -> TeleportationDevice.initUUID(evt.getCursor()));
        }
    }

    @EventHandler
    public void onMiddleClick(InventoryCreativeEvent evt) {
        TeleportationDevice device;
        if ((device = TeleportationDevice.fromItem(evt.getCursor())) != null) {
            for (ItemStack item : evt.getWhoClicked().getInventory().getContents()) {
                TeleportationDevice d2;
                if ((d2 = TeleportationDevice.fromItem(item)) != null) {
                    if (device.getId().equals(d2.getId())) {
                        TeleportationDevice.initUUID(evt.getCursor());
                        return;
                    }
                }
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

            if (!checkValidDestination(device, evt.getItem(), evt.getPlayer(), false, true)) return;
            setActionBar(device, fav, evt.getPlayer());
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

        if (!checkValidDestination(device, evt.getItem(), evt.getPlayer(), true, false)) return;

        evt.getPlayer().setCooldown(evt.getItem().getType(), 10);
        evt.getPlayer().getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                evt.getPlayer().getLocation().add(0, 1, 0),
                20, 0, 0, 0, 5);

        evt.getPlayer().setFallDistance(0);

        Location teleLoc = evt.getPlayer().getLocation();
        if (device.getSelected() instanceof Location loc) {
            teleLoc = loc.clone().add(0.5, 1, 0.5);
        } else if (device.getSelected() instanceof UUID uuid) {
            teleLoc = device.getAndUpdatePlayer(uuid).getFirst().getLocation();
        }
        evt.getPlayer().getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                teleLoc.clone().add(0, 1, 0), 20, 0, 0, 0, 5);
        evt.getPlayer().teleport(teleLoc);

        if (evt.getPlayer().getGameMode() != GameMode.CREATIVE && device.getUses() != -1) {
            ItemStack item = evt.getPlayer().getInventory().getItem(evt.getHand());

            device.damage(1).updateItem(item);
        }
    }
}
