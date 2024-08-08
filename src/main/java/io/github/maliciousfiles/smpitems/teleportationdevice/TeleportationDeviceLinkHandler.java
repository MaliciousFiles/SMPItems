package io.github.maliciousfiles.smpitems.teleportationdevice;

import io.github.maliciousfiles.smpitems.SMPItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Crafter;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftCrafter;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeleportationDeviceLinkHandler implements Listener {
    private static final ItemStack LINK_DEVICES_ITEM = SMPItems.createItemStack(TeleportationDevice.BASE.withName(
            Component.text("Link Devices")
                    .decoration(TextDecoration.ITALIC, false)
                    .color(NamedTextColor.AQUA)).asItem(), meta -> meta.lore(List.of()));

    public static void init() {
        for (int i = 2; i < 9; i++) {
            NamespacedKey key = SMPItems.key("link_devices_"+i);
            SMPItems.addRecipe(new ShapelessRecipe(key, LINK_DEVICES_ITEM)
                    .addIngredient(i, TeleportationDevice.BASE.asItem()));
            MinecraftServer.getServer().getRecipeManager().byKey(CraftNamespacedKey.toMinecraft(key)).orElseThrow()
                    .value().getIngredients().forEach(ig -> ig.exact = false);
        }

        Bukkit.getPluginManager().registerEvents(new TeleportationDeviceLinkHandler(), SMPItems.instance);
    }

    private static void linkAllDevices(ItemStack[] items) {
        Map<TeleportationDevice, ItemStack> devices = new HashMap<>();
        for (ItemStack item : items) {
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
        evt.setCurrentItem(ItemStack.empty());

        linkAllDevices(evt.getInventory().getMatrix());
    }

    @EventHandler
    public void postAutoCraft(CrafterCraftEvent evt) {
        if (!evt.getResult().equals(LINK_DEVICES_ITEM)) return;
        evt.setResult(ItemStack.empty());

        try {
            Method dispense = CrafterBlock.class.getDeclaredMethod("dispenseItem", ServerLevel.class, BlockPos.class, CrafterBlockEntity.class, net.minecraft.world.item.ItemStack.class, BlockState.class, RecipeHolder.class);
            dispense.setAccessible(true);

            ServerLevel level = ((CraftWorld) evt.getBlock().getWorld()).getHandle();
            BlockPos pos = CraftLocation.toBlockPosition(evt.getBlock().getLocation());

            CrafterBlockEntity entity = ((CrafterBlockEntity) level.getBlockEntity(pos));
            linkAllDevices(entity.getItems().stream().map(CraftItemStack::asCraftMirror).toArray(ItemStack[]::new));

            for (net.minecraft.world.item.ItemStack nms : entity.getItems()) {
                dispense.invoke(Blocks.CRAFTER, level, pos, entity, nms, level.getBlockState(pos),
                        MinecraftServer.getServer().getRecipeManager().byKey(CraftNamespacedKey.toMinecraft(evt.getRecipe().getKey())).orElseThrow()
                );
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
