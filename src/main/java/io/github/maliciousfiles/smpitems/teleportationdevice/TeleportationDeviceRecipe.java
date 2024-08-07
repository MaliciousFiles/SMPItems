package io.github.maliciousfiles.smpitems.teleportationdevice;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import com.mojang.datafixers.util.Pair;
import io.github.maliciousfiles.smpitems.SMPItems;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventoryCrafting;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                                     RecipeChoice defaultInput,
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
                if (item != null) playerInv.addItem(item).forEach((i, it) -> {
                    evt.getPlayer().getWorld().dropItem(evt.getPlayer().getLocation(), it, e -> e.setVelocity(new Vector()));
                });
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
                        if (invItem != null && invItem.getType() == input.getFirst() && invItem.getAmount() - amounts[j] > 0) {
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
                    playerInv.setItem(i, item.getAmount() == 0 ? null : item);
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
                                    playerInv.setItem(j, playerItem.getAmount() == 0 ? null : playerItem);

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

        private boolean isRecipe(CraftingInventory inv) {
            TeleportationDevice device = TeleportationDevice.fromItem(inv.getItem(deviceIdx+1));
            if (device == null || !validInput.test(device)) {
                return false;
            }

            for (Map.Entry<Integer, Pair<Material, Integer>> entry : rawRecipe.entrySet()) {
                ItemStack item = inv.getItem(entry.getKey());
                if (item == null || item.getType() != entry.getValue().getFirst() || item.getAmount() < entry.getValue().getSecond()) {
                    return false;
                }
            }

            return true;
        }

        @EventHandler
        public void onPrepareCraft(PrepareItemCraftEvent evt) {
            if (isRecipe(evt.getInventory())) {
                evt.getInventory().setResult(transformer.apply(TeleportationDevice.fromItem(evt.getInventory().getItem(deviceIdx+1))));
            }
        }

        @EventHandler
        public void onCraft(InventoryClickEvent evt) {
            if (!(evt.getInventory() instanceof CraftingInventory inv) || evt.getSlotType() != InventoryType.SlotType.RESULT || !isRecipe(inv)) return;

            ((CraftingMenu) ((CraftPlayer) evt.getWhoClicked()).getHandle().containerMenu).resultSlots.setRecipeUsed(((CraftServer) Bukkit.getServer()).getServer().getRecipeManager().byKey(CraftNamespacedKey.toMinecraft(recipe.getKey())).orElseThrow());
            ((CraftInventoryCrafting) inv).getMatrixInventory().setCurrentRecipe((RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>) ((CraftServer) Bukkit.getServer()).getServer().getRecipeManager().byKey(CraftNamespacedKey.toMinecraft(recipe.getKey())).orElseThrow());

            int[] amounts = new int[10];
            for (int i : rawRecipe.keySet()) {
                ItemStack item = evt.getInventory().getItem(i);
                if (item != null) amounts[i] = item.getAmount() - rawRecipe.get(i).getSecond();
            }
            Bukkit.getScheduler().runTask(SMPItems.instance, () -> {
                for (int i : rawRecipe.keySet()) {
                    ItemStack item = inv.getItem(i);
                    if (item != null) item.setAmount(amounts[i]);
                }
            });
        }
    }
}
