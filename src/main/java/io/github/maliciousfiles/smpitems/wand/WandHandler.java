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
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
            });
    private static final ShapedRecipe WAND_RECIPE = new ShapedRecipe(SMPItems.key("wand"), WAND_ITEM)
            .shape("  N",
                   " L ",
                   "B  ")
            .setIngredient('B', Material.BREEZE_ROD)
            .setIngredient('L', Material.BLAZE_ROD)
            .setIngredient('N', Material.NETHER_STAR);

    static { SMPItems.addItem("wand", () -> WAND_ITEM); SMPItems.addRecipe(WAND_RECIPE); }

    // cast + open menu
    @EventHandler
    public void onInteract(PlayerInteractEvent evt) {
        if (!WandItemHelper.isWand(evt.getItem()) || !evt.getAction().isRightClick()) return;

        if (!evt.getPlayer().isSneaking()) {
            WandItemHelper.getSelected(evt.getItem()).handler.castSpell(evt.getPlayer());
        } else {
            Inventory menu = Bukkit.createInventory(null, 9, Component.text("Wand Spells"));
            menu.setContents(Arrays.stream(WandItemHelper.getSpells(evt.getItem()))
                    .map(s -> s == WandItemHelper.Spell.EMPTY ? null : s.getItem()).toArray(ItemStack[]::new));

            menus.add(menu);
            evt.getPlayer().openInventory(menu);
        }
    }

    // handle menu
    private static final List<Inventory> menus = new ArrayList<>();
    @EventHandler
    public void onInventoryClick(InventoryClickEvent evt) {
        if (!menus.contains(evt.getInventory())) return;

        if (evt.getView().getBottomInventory().equals(evt.getClickedInventory())) { // is player inv
            if (evt.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && !WandItemHelper.Spell.isSpell(evt.getCurrentItem())) {
                evt.setCancelled(true);
            }
        } else { // is wand inv
            switch (evt.getAction()) {
                case PLACE_ALL:
                case PLACE_SOME:
                case PLACE_ONE:
                case SWAP_WITH_CURSOR:
                    if (!WandItemHelper.Spell.isSpell(evt.getCursor())) evt.setCancelled(true);
                    break;
                case HOTBAR_SWAP:
                    if (!WandItemHelper.Spell.isSpell(evt.getWhoClicked().getInventory().getItem(evt.getHotbarButton()))) {
                        evt.setCancelled(true);
                    }
                    break;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent evt) {
        if (menus.remove(evt.getInventory())) {
            WandItemHelper.setSpells(evt.getPlayer().getInventory().getItemInMainHand(),
                    Arrays.stream(Arrays.copyOfRange(evt.getPlayer().getInventory().getContents(), 0, 9))
                            .map(WandItemHelper.Spell::fromItem).toArray(WandItemHelper.Spell[]::new));
        }
    }

    // cancel all
    @EventHandler
    public void onDrop(PlayerDropItemEvent evt) {
        if (WandItemHelper.isWand(evt.getItemDrop().getItemStack()) && evt.getPlayer().isSneaking()) {
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

    private void tryResetHotbar(Player player) {
        Pair<Integer, ItemStack[]> hotbar = hotbars.remove(player);
        if (hotbar == null) return;

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

            ItemStack[] contents = evt.getPlayer().getInventory().getContents();
            System.arraycopy(Arrays.stream(WandItemHelper.getSpells(wand)).map(WandItemHelper.Spell::getItem).toArray(ItemStack[]::new),
                    0, contents, 0, 9);
            evt.getPlayer().getInventory().setContents(contents);
        } else {
            tryResetHotbar(evt.getPlayer());
        }
    }

    @EventHandler
    public void onDisconnect(PlayerQuitEvent evt) {
        tryResetHotbar(evt.getPlayer());
    }

    // prevent renaming spells
    @EventHandler
    public void onAnvil(PrepareAnvilEvent evt) {
        if (WandItemHelper.Spell.isSpell(evt.getInventory().getFirstItem())) {
            evt.setResult(null);
        }
    }
}
