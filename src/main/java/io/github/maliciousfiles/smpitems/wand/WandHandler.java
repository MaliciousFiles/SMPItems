package io.github.maliciousfiles.smpitems.wand;

import com.mojang.datafixers.util.Pair;
import io.github.maliciousfiles.smpitems.SMPItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WandHandler implements Listener {

    private static final ItemStack WAND_ITEM = SMPItems.createItemStack(WandItemHelper.initWand(new ItemStack(Material.RECOVERY_COMPASS)),
            meta -> {
                meta.setEnchantmentGlintOverride(true);
                meta.displayName(Component.text("Wand", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)
                        .decorate(TextDecoration.BOLD));
                meta.setCustomModelData(SMPItems.MODEL_DATA_BASE);
                meta.setMaxStackSize(1);
            });
    private static final ShapedRecipe WAND_RECIPE = new ShapedRecipe(SMPItems.key("wand"), WAND_ITEM)
            .shape("  N",
                   " L ",
                   "B  ")
            .setIngredient('B', Material.BREEZE_ROD)
            .setIngredient('L', Material.BLAZE_ROD)
            .setIngredient('N', Material.NETHER_STAR);

    static {
        WandItemHelper.setSelected(WAND_ITEM, 0); // init name, etc.

        SMPItems.addRecipe(WAND_RECIPE);

        SMPItems.addItem("wand", args -> WAND_ITEM);
        SMPItems.addItem("spell", args -> WandItemHelper.Spell.valueOf(args[0].toUpperCase()).getItem(),
                Arrays.stream(WandItemHelper.Spell.values()).map(Enum::name).toList());
    }

    // cast + open menu
    @EventHandler
    public void onInteract(PlayerInteractEvent evt) {
        if (WandItemHelper.isWand(evt.getItem()) && evt.getAction().isRightClick() && !evt.getPlayer().isSneaking() && !evt.getPlayer().hasCooldown(Material.RECOVERY_COMPASS)) {
            WandItemHelper.getSelected(evt.getItem()).handler.castSpell(evt.getPlayer());
            evt.getPlayer().setCooldown(Material.RECOVERY_COMPASS, 30);
        } else if (hotbars.containsKey(evt.getPlayer()) && evt.getAction().isRightClick()) {
            tryResetHotbar(evt.getPlayer());
            ItemStack wand = evt.getPlayer().getInventory().getItemInMainHand();

            Inventory menu = Bukkit.createInventory(null, 9, Component.text("Wand Spells"));
            menu.setContents(Arrays.stream(WandItemHelper.getSpells(wand))
                    .map(s -> s == WandItemHelper.Spell.EMPTY ? null : s.getItem()).toArray(ItemStack[]::new));

            menus.put(menu, Pair.of(wand, wand.clone()));
            evt.getPlayer().openInventory(menu);
        }
    }

    // handle menu
    private static final Map<Inventory, Pair<ItemStack, ItemStack>> menus = new HashMap<>();

    private static boolean isLegalSpell(ItemStack item, ItemStack[] spells) {
        WandItemHelper.Spell spell = WandItemHelper.Spell.fromItem(item);

        return item == null || spell != WandItemHelper.Spell.EMPTY && Arrays.stream(spells)
                .noneMatch(i -> WandItemHelper.Spell.fromItem(i).equals(spell));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent evt) {
        if (!menus.containsKey(evt.getInventory())) return;

        ItemStack[] spells = evt.getInventory().getContents();

        PlayerInventory playerInv = evt.getWhoClicked().getInventory();
        if (playerInv.equals(evt.getClickedInventory()) || evt.getClickedInventory() == null) { // is player inv or outside
            ItemStack item = null;

            switch (evt.getAction()) {
                case InventoryAction.MOVE_TO_OTHER_INVENTORY:
                    if (!isLegalSpell(evt.getCurrentItem(), spells)) {
                        evt.setCancelled(true);
                        playerInv.setItem(evt.getSlot(), evt.getCurrentItem());
                        Bukkit.getScheduler().runTask(SMPItems.instance, () -> {
                            menus.put(evt.getInventory(), Pair.of(evt.getCurrentItem(), evt.getCurrentItem().clone()));
                        });
                    }
                    break;
                case InventoryAction.DROP_ALL_CURSOR:
                case InventoryAction.DROP_ONE_CURSOR:
                    item = evt.getCursor();
                case InventoryAction.DROP_ALL_SLOT:
                case InventoryAction.DROP_ONE_SLOT:
                    if (item == null) item = evt.getCurrentItem();

                    if (menus.get(evt.getInventory()).getSecond().equals(item)) {
                        trySaveInventory(evt.getInventory());
                        Bukkit.getScheduler().runTask(SMPItems.instance, evt.getInventory()::close);
                    }
                    break;
                case InventoryAction.PICKUP_ALL:
                case InventoryAction.PICKUP_HALF:
                case InventoryAction.PICKUP_ONE:
                case InventoryAction.PICKUP_SOME:
                    if (menus.get(evt.getInventory()).getSecond().equals(evt.getCurrentItem())) {
                        Bukkit.getScheduler().runTask(SMPItems.instance, () -> {
                            menus.put(evt.getInventory(), Pair.of(evt.getCursor(), evt.getCursor().clone()));
                        });
                    }
                    break;
                case InventoryAction.PLACE_ALL:
                case InventoryAction.PLACE_ONE:
                case InventoryAction.PLACE_SOME:
                case InventoryAction.SWAP_WITH_CURSOR:
                    if (menus.get(evt.getInventory()).getSecond().equals(evt.getCursor())) {
                        Bukkit.getScheduler().runTask(SMPItems.instance, () -> {
                            menus.put(evt.getInventory(), Pair.of(evt.getCurrentItem(), evt.getCurrentItem().clone()));
                        });
                    }
                    break;
                case InventoryAction.HOTBAR_SWAP:
                    ItemStack hotbarItem = playerInv.getItem(evt.getHotbarButton());
                    if (menus.get(evt.getInventory()).getSecond().equals(hotbarItem)) {
                        Bukkit.getScheduler().runTask(SMPItems.instance, () -> {
                            menus.put(evt.getInventory(), Pair.of(evt.getCurrentItem(), evt.getCurrentItem().clone()));
                        });
                    }
                    break;
            }
        } else { // is wand inv
            switch (evt.getAction()) {
                case PLACE_ALL:
                case PLACE_SOME:
                case PLACE_ONE:
                case SWAP_WITH_CURSOR:
                    if (!isLegalSpell(evt.getCursor(), spells)) {
                        evt.setCancelled(true);
                        evt.getView().setCursor(evt.getCursor());

                        if (menus.get(evt.getInventory()).getSecond().equals(evt.getCursor())) {
                            Bukkit.getScheduler().runTask(SMPItems.instance, () -> {
                                menus.put(evt.getInventory(), Pair.of(evt.getCursor(), evt.getCursor().clone()));
                            });
                        }
                    }
                    break;
                case HOTBAR_SWAP:
                    ItemStack item = playerInv.getItem(evt.getHotbarButton());
                    if (!isLegalSpell(item, spells)) {
                        evt.setCancelled(true);
                        playerInv.setItem(evt.getHotbarButton(), item);

                        if (menus.get(evt.getInventory()).getSecond().equals(item)) {
                            Bukkit.getScheduler().runTask(SMPItems.instance, () -> {
                                menus.put(evt.getInventory(), Pair.of(playerInv.getItem(evt.getHotbarButton()), item.clone()));
                            });
                        }
                    }
                    break;
            }
        }
    }

    @EventHandler
    private static void onDrag(InventoryDragEvent evt) {
        if (menus.containsKey(evt.getInventory()) && !isLegalSpell(evt.getOldCursor(), evt.getInventory().getContents())) {
            evt.setCancelled(true);
            evt.getView().setCursor(evt.getOldCursor());
        }
    }

    private static void trySaveInventory(Inventory inv) {
        Pair<ItemStack, ItemStack> pair;
        if ((pair = menus.remove(inv)) != null) {
            WandItemHelper.setSpells(pair.getFirst(),
                    Arrays.stream(inv.getContents())
                            .map(WandItemHelper.Spell::fromItem).toArray(WandItemHelper.Spell[]::new));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent evt) {
        trySaveInventory(evt.getInventory());
    }

    // cancel all
    @EventHandler
    public void onDrop(PlayerDropItemEvent evt) {
        if (hotbars.containsKey(evt.getPlayer())) {
            evt.setCancelled(true);

            for (WandItemHelper.Spell spell : WandItemHelper.Spell.values()) {
                if (spell.handler instanceof ToggleableSpellHandler toggleable && toggleable.isActive(evt.getPlayer())) {
                    toggleable.deactivate(evt.getPlayer());
                }
            }
        }

        evt.getPlayer().getInventory().setItemInMainHand(evt.getPlayer().getInventory().getItemInMainHand());
    }

    // select spell
    private static final Map<Player, Pair<Integer, ItemStack[]>> hotbars = new HashMap<>();

    @EventHandler
    public void onSelectedItemChange(PlayerItemHeldEvent evt) {
        Pair<Integer, ItemStack[]> hotbar = hotbars.get(evt.getPlayer());
        if (hotbar != null) {
            ItemStack wand = hotbar.getSecond()[hotbar.getFirst()];
            WandItemHelper.setSelected(wand, evt.getNewSlot());
        }
    }

    private static void tryResetHotbar(Player player) {
        Pair<Integer, ItemStack[]> hotbar = hotbars.remove(player);
        if (hotbar == null) return;

        ItemStack wand = hotbar.getSecond()[hotbar.getFirst()];
        WandItemHelper.setSelected(wand, player.getInventory().getHeldItemSlot());

        player.getInventory().setHeldItemSlot(hotbar.getFirst());

        ItemStack[] contents = player.getInventory().getContents();
        System.arraycopy(hotbar.getSecond(), 0, contents, 0, 9);
        player.getInventory().setContents(contents);
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent evt) {
        if (evt.isSneaking()) {
            ItemStack wand = evt.getPlayer().getInventory().getItemInMainHand();
            if (!WandItemHelper.isWand(wand)) return;

            hotbars.put(evt.getPlayer(), Pair.of(evt.getPlayer().getInventory().getHeldItemSlot(),
                    Arrays.copyOfRange(evt.getPlayer().getInventory().getContents(), 0, 9)));

            WandItemHelper.Spell[] spells = WandItemHelper.getSpells(wand);
            ItemStack[] spellItems = new ItemStack[spells.length];
            for (int i = 0; i < spells.length; i++) {
                int finalI = i;
                spellItems[i] = SMPItems.createItemStack(spells[i].getItem(), meta -> {
                    meta.setEnchantmentGlintOverride(
                            spells[finalI].handler instanceof ToggleableSpellHandler toggleable &&
                                    toggleable.isActive(evt.getPlayer()));
                    meta.displayName(meta.displayName()
                            .append(Component.text(" (#%s)".formatted(finalI+1), NamedTextColor.GRAY)));

                });
            }

            ItemStack[] contents = evt.getPlayer().getInventory().getContents();
            System.arraycopy(spellItems, 0, contents, 0, 9);
            evt.getPlayer().getInventory().setContents(contents);

            evt.getPlayer().getInventory().setHeldItemSlot(WandItemHelper.getSelectedIdx(wand));
        } else {
            tryResetHotbar(evt.getPlayer());
        }
    }

    @EventHandler
    public void onOpenInventory(InventoryOpenEvent evt) {
        tryResetHotbar((Player) evt.getPlayer());
    }

    @EventHandler
    public void onDisconnect(PlayerQuitEvent evt) {
        tryResetHotbar(evt.getPlayer());
    }

    @EventHandler
    public void onDeath(EntityDamageEvent evt) {
        if (evt.getEntity() instanceof Player player && player.getHealth() - evt.getFinalDamage() <= 0) {
            trySaveInventory(player.getOpenInventory().getTopInventory());
            tryResetHotbar(player);
        }
    }

    // prevent renaming spells, implement renaming wand
    @EventHandler
    public void onAnvil(PrepareAnvilEvent evt) {
        if (WandItemHelper.Spell.isSpell(evt.getInventory().getFirstItem())) {
            evt.setResult(null);
        }

        if (WandItemHelper.isWand(evt.getInventory().getFirstItem())) {
            String name = WandItemHelper.getName(evt.getInventory().getFirstItem());
            String input = evt.getInventory().getRenameText();

            if (!name.equals(input) && evt.getResult() == null) {
                // TODO: anvil
            }
        }
    }

    public static void disable() {
        hotbars.keySet().forEach(WandHandler::tryResetHotbar);
        menus.keySet().forEach(Inventory::close);

        for (WandItemHelper.Spell value : WandItemHelper.Spell.values()) {
            if (value.handler instanceof ToggleableSpellHandler toggleable) {
                Bukkit.getOnlinePlayers().forEach(p -> { if (toggleable.isActive(p)) toggleable.deactivate(p); });
            }
        }
    }
}
