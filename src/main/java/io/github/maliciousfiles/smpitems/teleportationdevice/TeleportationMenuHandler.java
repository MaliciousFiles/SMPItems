package io.github.maliciousfiles.smpitems.teleportationdevice;

import com.mojang.datafixers.util.Pair;
import io.github.maliciousfiles.smpitems.SMPItems;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TeleportationMenuHandler implements Listener {

    private static final ItemStack EMPTY = SMPItems.createItemStack(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), meta -> {
        meta.displayName(Component.text(""));
    });
    private static final ItemStack FILLER = SMPItems.createItemStack(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), meta -> {
        meta.displayName(Component.text(""));
    });
    private static final ItemStack PREV_PAGE = SMPItems.createItemStack(new ItemStack(Material.ARROW), meta -> {
        meta.displayName(Component.text("Previous Page")
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.WHITE));
    });
    private static final ItemStack NEXT_PAGE = SMPItems.createItemStack(new ItemStack(Material.ARROW), meta -> {
        meta.displayName(Component.text("Next Page")
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.WHITE));
    });
    private static final ItemStack CANCEL = SMPItems.createItemStack(new ItemStack(Material.BARRIER), meta -> {
        meta.displayName(Component.text("Cancel")
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.WHITE));
    });

    private static final Map<Inventory, MenuInstance> inventories = new HashMap<>();

    public static void disable() {
        inventories.keySet().forEach(Inventory::close);
    }

    public static void openMenu(TeleportationDevice device, ItemStack stack, Player player) {
        MenuInstance menu = generateItems(device, stack, 0, true);

        Inventory inv = Bukkit.createInventory(player, 36, Component.text("Teleportation Menu"));
        inventories.put(inv, menu);
        fillPage(inv, 0, menu.items);

        player.openInventory(inv);
        device.updateItem(stack);
    }

    private static MenuInstance generateItems(TeleportationDevice device, ItemStack deviceItem, int page, boolean moveFavs) {
        List<ItemStack> items = new ArrayList<>();

        BiFunction<Boolean, Boolean, List<Component>> lore = (isFav, isSelected) -> {
            List<Component> l = new ArrayList<>();

            l.add(Component.empty());
            if (!isSelected) {
                l.add(Component.text("Left click to select")
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.GRAY));
            }
            l.add(Component.text("Right click to %s".formatted(isFav ? "unfavorite" : "favorite"))
                    .decoration(TextDecoration.ITALIC, false)
                    .color(NamedTextColor.GRAY));
            l.add(Component.text("Shift-right click to unlink")
                    .decoration(TextDecoration.ITALIC, false)
                    .color(NamedTextColor.GRAY));

            return l;
        };

        Map<Integer, Object> objMap = new HashMap<>();

        List<Object> links = new ArrayList<>();
        links.addAll(device.getItems());
        links.addAll(device.getAnchors());
        if (moveFavs) links.sort((o1, o2) -> device.getFavorites().contains(o1) ? -1 : device.getFavorites().contains(o2) ? 1 : 0);

        for (Object obj : links) {
            if (obj instanceof UUID uuid) {
                Pair<Player, TeleportationDevice> p = device.getAndUpdatePlayer(uuid);

                ItemStack item;
                if (p != null) {
                    item = new ItemStack(Material.PLAYER_HEAD);
                    item.editMeta(SkullMeta.class, meta -> {
                        boolean isSelected = device.getSelected().equals(uuid);
                        boolean isFav = device.getFavorites().contains(uuid);

                        meta.displayName(Component.text(p.getFirst().getName())
                                .append(Component.text(isSelected ? " ✪" : "")
                                        .decoration(TextDecoration.UNDERLINED, false))
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.AQUA)
                                .decoration(TextDecoration.UNDERLINED, isFav));
                        meta.setOwningPlayer(p.getFirst());
                        meta.lore(lore.apply(isFav, isSelected));
                    });
                } else {
                    OfflinePlayer last = device.getLastKnownHolder(uuid);

                    item = new ItemStack(Material.BARRIER);
                    item.editMeta(meta -> {
                        boolean isSelected = device.getSelected().equals(uuid);
                        boolean isFav = device.getFavorites().contains(uuid);

                        meta.displayName(Component.text("Player not found")
                                .append(Component.text(isSelected ? " ✪" : "")
                                        .decoration(TextDecoration.UNDERLINED, false))
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.AQUA)
                                .decoration(TextDecoration.UNDERLINED, isFav));

                        List<Component> loreList = new ArrayList<>();
                        if (last != null) loreList.add(Component.text("Last held by ")
                                .append(Component.text(last.getName())
                                        .color(NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.GRAY));
                        loreList.addAll(lore.apply(isFav, isSelected));
                        meta.lore(loreList);
                    });
                }

                objMap.put(items.size(), uuid);
                items.add(item);
            } else if (obj instanceof Location loc) {
                String name = TeleportationDevice.getAnchorName(loc);

                if (name.isEmpty()) {
                    device.toggleAnchor(loc);
                    continue;
                }

                ItemStack item = new ItemStack(Material.LODESTONE);
                item.editMeta(meta -> {
                    boolean isSelected = device.getSelected().equals(loc);
                    boolean isFav = device.getFavorites().contains(loc);

                    meta.displayName(Component.text(name)
                            .color(NamedTextColor.AQUA)
                            .append(Component.text(" (%s, %s, %s)".formatted(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()))
                                    .color(NamedTextColor.GRAY)
                                    .decoration(TextDecoration.UNDERLINED, false))
                            .append(Component.text(isSelected ? " ✪" : "")
                                    .decoration(TextDecoration.UNDERLINED, false))
                            .decoration(TextDecoration.ITALIC, false)
                            .decoration(TextDecoration.UNDERLINED, isFav));
                    meta.lore(lore.apply(isFav, isSelected));
                });

                objMap.put(items.size(), loc);
                items.add(item);
            }
        }

        return new MenuInstance(deviceItem, device, page, items, objMap::get);
    }

    private static void fillPage(Inventory inv, int page, List<ItemStack> items) {
        ItemStack[] contents = new ItemStack[36];

        for (int i = 0; i < 18; i++) {
            int idx = page * 18 + i;
            contents[i] = idx < items.size() ? items.get(idx) : EMPTY;
        }
        for (int i = 18; i < 27; i++) contents[i] = FILLER;
        for (int i = 27; i < 36; i++) contents[i] = EMPTY;

        if (page > 0) contents[27] = PREV_PAGE;
        contents[31] = CANCEL;
        if (page < (items.size()-1)/18) contents[35] = NEXT_PAGE;

        inv.setContents(contents);
    }

    @EventHandler
    public void onClick(InventoryClickEvent evt) {
        Inventory inv = evt.getClickedInventory();
        if (inv == null || !inventories.containsKey(inv)) return;

        evt.setCancelled(true);

        MenuInstance menu = inventories.get(inv);

        if (FILLER.equals(evt.getCurrentItem()) || EMPTY.equals(evt.getCurrentItem())) {

        } else if (NEXT_PAGE.equals(evt.getCurrentItem())) {
            menu.page++;
            fillPage(inv, menu.page, menu.items);
        } else if (PREV_PAGE.equals(evt.getCurrentItem())) {
            menu.page--;
            fillPage(inv, menu.page, menu.items);
        } else if (CANCEL.equals(evt.getCurrentItem())) {
            inv.close();
            inventories.remove(inv);

            if (evt.getHotbarButton() != -1) {
                PlayerInventory playerInv = evt.getWhoClicked().getInventory();
                playerInv.setItem(evt.getHotbarButton(), playerInv.getItem(evt.getHotbarButton()));
            } else if (evt.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                PlayerInventory playerInv = evt.getWhoClicked().getInventory();
                for (int i = 0; i < playerInv.getContents().length; i++) {
                    if (playerInv.getItem(i) == null) playerInv.setItem(i, null);
                }
            }
        } else {
            boolean reload = true;

            if (menu.confirms.contains(evt.getRawSlot())) {
                menu.confirms.remove((Integer) evt.getRawSlot());

                Object obj = menu.getObj(evt.getRawSlot());
                if (obj instanceof Location loc) menu.device.toggleAnchor(loc);
                else if (obj instanceof UUID uuid) menu.device.toggleItemLink(uuid);

                menu.device.updateItem(menu.deviceItem);
            } else {
                if (evt.isRightClick()) {
                    if (!evt.isShiftClick()) {
                        Object obj = menu.getObj(evt.getRawSlot());

                        if (menu.device.getFavorites().contains(obj)) {
                            menu.device.removeFavorite(obj);
                            menu.device.updateItem(menu.deviceItem);
                        } else if (menu.device.getFavorites().size() < TeleportationDevice.MAX_FAV){
                            menu.device.addFavorite(obj);
                            menu.device.updateItem(menu.deviceItem);
                        } else {
                            reload = false;

                            ItemStack item = evt.getCurrentItem();
                            ItemMeta meta = item.getItemMeta();

                            Component displayName = meta.displayName();
                            List<Component> lore = meta.lore();

                            meta.displayName(Component.text("Favorites list is full")
                                    .decoration(TextDecoration.ITALIC, false)
                                    .color(NamedTextColor.RED));
                            meta.lore(List.of(
                                    Component.text("Try unfavoriting another destination")
                                            .decoration(TextDecoration.ITALIC, false)
                                            .color(NamedTextColor.GRAY)
                            ));

                            item.setItemMeta(meta);

                            Bukkit.getScheduler().runTaskLater(SMPItems.instance, () -> item.editMeta(m -> {
                                m.displayName(displayName);
                                m.lore(lore);
                            }), 35);
                        }
                    } else {
                        reload = false;

                        ItemStack item = evt.getCurrentItem();
                        ItemMeta meta = item.getItemMeta();

                        Component displayName = meta.displayName();
                        List<Component> lore = meta.lore();

                        meta.displayName(Component.text("Are you sure?")
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.RED));
                        meta.lore(List.of(
                                Component.text("Click again to unlink")
                                        .decoration(TextDecoration.ITALIC, false)
                                        .color(NamedTextColor.GRAY)
                        ));

                        item.setItemMeta(meta);

                        Bukkit.getScheduler().runTaskLater(SMPItems.instance, () -> item.editMeta(m -> {
                            m.displayName(displayName);
                            m.lore(lore);

                            menu.confirms.remove((Integer) evt.getRawSlot());
                        }), 35);

                        menu.confirms.add(evt.getRawSlot());
                    }
                } else if (evt.isLeftClick()) {
                    menu.device.select(menu.getObj(evt.getRawSlot()));
                    menu.device.updateItem(menu.deviceItem);
                }
            }

            if (reload) {
                MenuInstance newMenu = generateItems(menu.device, menu.deviceItem, menu.page, false);
                fillPage(inv, newMenu.page, newMenu.items);

                inventories.put(inv, newMenu);
            }
        }
    }

    private static class MenuInstance {
        public ItemStack deviceItem;
        public TeleportationDevice device;
        public int page;
        public List<ItemStack> items;
        public List<Integer> confirms = new ArrayList<>();
        public Function<Integer, Object> transformer;

        public MenuInstance(ItemStack deviceItem, TeleportationDevice device, int page, List<ItemStack> items, Function<Integer, Object> transformer) {
            this.deviceItem = deviceItem;
            this.device = device;
            this.page = page;
            this.items = items;
            this.transformer = transformer;
        }

        public Object getObj(int idx) {
            return transformer.apply(page*18+idx);
        }
    }
}
