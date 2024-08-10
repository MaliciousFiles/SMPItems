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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;

import java.util.*;

public class WandHandler implements Listener {

    private static final ItemStack WAND_ITEM = SMPItems.createItemStack(WandItemHelper.initWand(new ItemStack(Material.CLOCK)),
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
        SMPItems.addRecipe(WAND_RECIPE);

        SMPItems.addItem("wand", args -> WAND_ITEM);
        SMPItems.addItem("spell", args -> WandItemHelper.Spell.valueOf(args[0].toUpperCase()).getItem(),
                Arrays.stream(WandItemHelper.Spell.values()).map(Enum::name).toList());
    }

    // cast + open menu
    @EventHandler
    public void onInteract(PlayerInteractEvent evt) {
        if (WandItemHelper.isWand(evt.getItem()) && evt.getAction().isRightClick() && !evt.getPlayer().isSneaking()) {
            WandItemHelper.getSelected(evt.getItem()).handler.castSpell(evt.getPlayer());
        } else if (hotbars.containsKey(evt.getPlayer()) && evt.getAction().isRightClick()) {
            tryResetHotbar(evt.getPlayer());
            ItemStack wand = evt.getPlayer().getInventory().getItemInMainHand();

            Inventory menu = Bukkit.createInventory(null, 9, Component.text("Wand Spells"));
            menu.setContents(Arrays.stream(WandItemHelper.getSpells(wand))
                    .map(s -> s == WandItemHelper.Spell.EMPTY ? null : s.getItem()).toArray(ItemStack[]::new));

            menus.put(menu, wand);
            evt.getPlayer().openInventory(menu);
        }
    }

    // handle menu
    private static final Map<Inventory, ItemStack> menus = new HashMap<>();

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
        if (playerInv.equals(evt.getClickedInventory())) { // is player inv
            switch (evt.getAction()) {
                case InventoryAction.MOVE_TO_OTHER_INVENTORY:
                    if (!isLegalSpell(evt.getCurrentItem(), spells)) evt.setCancelled(true);
                    break;
                case InventoryAction.DROP_ALL_SLOT:
                case InventoryAction.DROP_ONE_SLOT:
                    if (menus.get(evt.getInventory()).equals(evt.getCurrentItem())) {
                        trySaveInventory(evt.getInventory());
                        Bukkit.getScheduler().runTask(SMPItems.instance, evt.getInventory()::close);
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
                    }
                    break;
                case HOTBAR_SWAP:
                    ItemStack item = playerInv.getItem(evt.getHotbarButton());
                    if (!isLegalSpell(item, spells)) {
                        evt.setCancelled(true);
                        playerInv.setItem(evt.getHotbarButton(), item);
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
        ItemStack wand;
        if ((wand = menus.remove(inv)) != null) {
            WandItemHelper.setSpells(wand,
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
    public void onDisconnect(PlayerQuitEvent evt) {
        tryResetHotbar(evt.getPlayer());
    }

    @EventHandler
    public void onDeath(EntityDamageEvent evt) {
        if (evt.getEntity() instanceof Player player && player.getHealth() - evt.getFinalDamage() <= 0) {
            tryResetHotbar(player);
        }
    }

    // prevent renaming spells
    @EventHandler
    public void onAnvil(PrepareAnvilEvent evt) {
        if (WandItemHelper.Spell.isSpell(evt.getInventory().getFirstItem())) {
            evt.setResult(null);
        }
    }

    public static void disable() {
        Bukkit.broadcastMessage("Disabling wand handler");
        hotbars.keySet().forEach(WandHandler::tryResetHotbar);
        menus.keySet().forEach(Inventory::close);
    }
}
