package io.github.maliciousfiles.smpitems.teleportationdevice;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import com.mojang.datafixers.util.Pair;
import io.github.maliciousfiles.smpitems.SMPItems;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventoryCrafting;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapedRecipe;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class TeleportationDeviceRecipe {

    private final Predicate<TeleportationDevice> discoverRecipe;
    private final Predicate<TeleportationDevice> validInput;
    private final Function<TeleportationDevice, ItemStack> transformer;
    private final ShapedRecipe recipe;
    private final int deviceIdx;

    private final Map<Integer, Pair<Material, Integer>> rawRecipe = new HashMap<>();

    public TeleportationDeviceRecipe(Predicate<TeleportationDevice> discoverRecipe,
                                     Predicate<TeleportationDevice> validInput,
                                     Function<TeleportationDevice, ItemStack> transformer,
                                     ItemStack defaultInput,
                                     ItemStack defaultOutput,
                                     NamespacedKey key,
                                     List<Pair<Material, Integer>> recipe) {
        this.discoverRecipe = discoverRecipe;

        this.validInput = validInput;
        this.transformer = transformer;
        this.recipe = new ShapedRecipe(key, defaultOutput)
                .shape("012", "345", "678");

        int dIdx = -1;
        for (int i = 0; i < 9; i++) {
            Pair<Material, Integer> pair = recipe.get(i);

            if (pair.getFirst() == null) {
                this.recipe.setIngredient(String.valueOf(i).charAt(0), defaultInput);
                dIdx = i;
                continue;
            }

            if (!pair.getFirst().isEmpty()) {
                this.recipe.setIngredient(String.valueOf(i).charAt(0), pair.getFirst());
                rawRecipe.put(i + 1, pair);
            }
        }
        this.deviceIdx = dIdx;

        SMPItems.addRecipe(this.recipe);
        Bukkit.getPluginManager().registerEvents(new RecipeHandler(), SMPItems.instance);
    }

    private class RecipeHandler implements Listener {

        @EventHandler
        public void onInventoryChange(PlayerInventorySlotChangeEvent evt) {
            TeleportationDevice device;
            if ((device = TeleportationDevice.fromItem(evt.getNewItemStack())) == null) return;

            if (discoverRecipe.test(device)) {
                evt.getPlayer().discoverRecipe(recipe.getKey());
            }
        }

        @EventHandler
        public void onClickRecipe(PlayerRecipeBookClickEvent evt) {
            if (!evt.getRecipe().equals(recipe.getKey())) return;
            evt.setCancelled(true);

            CraftingInventory inv = (CraftingInventory) evt.getPlayer().getOpenInventory().getTopInventory();
            PlayerInventory playerInv = evt.getPlayer().getInventory();

            for (ItemStack item : inv.getMatrix()) {
                if (item != null) playerInv.addItem(item);
            }
            inv.clear();

            ItemStack[] contents = playerInv.getContents();

            int[] amounts = new int[41];
            boolean ghost = false;

            craft:
            for (int i = 0; i < 9; i++) {
                if (i == deviceIdx) {
                    for (int j = 0; j < contents.length; j++) {
                        TeleportationDevice device = TeleportationDevice.fromItem(contents[j]);
                        if (device == null) continue;

                        if (validInput.test(device)) {
                            amounts[j] = 1;

                            inv.setItem(i+1, contents[j].clone());

                            continue craft;
                        }
                    }

                    ghost = true;
                    break;
                }

                Pair<Material, Integer> input = rawRecipe.get(i+1);
                if (input == null) continue;

                int j = 0;
                outer:
                for (int k = 0; k < input.getSecond(); k++) {
                    for (; j < contents.length; j++) {
                        ItemStack invItem = contents[j];
                        if (invItem != null && invItem.getType() == input.getFirst()) {
                            int amount = Math.min(invItem.getAmount() - amounts[j], input.getSecond() - k);
                            amounts[j] += amount;

                            k += amount - 1;
                            continue outer;
                        }
                    }

                    ghost = true;
                    break craft;
                }

                inv.setItem(i+1, new ItemStack(input.getFirst(), input.getSecond()));
            }

            if (ghost) {
                inv.clear();

                ServerPlayer nms = ((CraftPlayer) evt.getPlayer()).getHandle();
                nms.connection.send(new ClientboundPlaceGhostRecipePacket(nms.containerMenu.containerId, nms.server.getRecipeManager().byKey(CraftNamespacedKey.toMinecraft(recipe.getKey())).orElseThrow()));
            } else {
                for (int i = 0; i < amounts.length; i++) {
                    ItemStack item = playerInv.getItem(i);
                    if (item == null || amounts[i] == 0) continue;

                    item.setAmount(item.getAmount() - amounts[i]);
                    if (item.getAmount() == 0) playerInv.setItem(i, null);
                }

                if (evt.isMakeAll()) {
                    while (true) {
                        boolean stop = true;

                        craft:
                        for (int i = 1; i <= 9; i++) {
                            ItemStack item = inv.getItem(i);
                            if (i == deviceIdx+1 || item == null || item.getAmount() == item.getMaxStackSize()) continue;

                            for (int j = 0; j < playerInv.getContents().length; j++) {
                                ItemStack playerItem = playerInv.getItem(j);
                                if (item.isSimilar(playerItem)) {
                                    item.add();
                                    playerItem.setAmount(playerItem.getAmount() - 1);
                                    if (playerItem.getAmount() == 0) playerInv.setItem(j, null);

                                    stop = false;
                                    continue craft;
                                }
                            }
                        }

                        if (stop) break;
                    }
                }
            }
        }

        @EventHandler
        public void onPrepareCraft(PrepareItemCraftEvent evt) {
            TeleportationDevice device = TeleportationDevice.fromItem(evt.getInventory().getItem(deviceIdx+1));
            if (device == null || !validInput.test(device)) {
//                evt.getInventory().setResult(null);
                return;
            }

            for (Map.Entry<Integer, Pair<Material, Integer>> entry : rawRecipe.entrySet()) {
                ItemStack item = evt.getInventory().getItem(entry.getKey());
                if (item == null || item.getType() != entry.getValue().getFirst() || item.getAmount() < entry.getValue().getSecond()) {
//                    evt.getInventory().setResult(null);
                    return;
                }
            }

            evt.getInventory().setResult(transformer.apply(device));
        }

        @EventHandler
        public void onCraft(CraftItemEvent evt) {
            if (!(evt.getRecipe() instanceof CraftingRecipe cr && cr.getKey().equals(recipe.getKey()))) return;

            for (int i : rawRecipe.keySet()) {
                ItemStack item = evt.getInventory().getItem(i);
                if (item != null) {
                    item.setAmount(item.getAmount() - rawRecipe.get(i).getSecond()+1);
                }
            }
        }
    }
}
