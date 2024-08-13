package io.github.maliciousfiles.smpitems.wand.spells;

import com.mojang.datafixers.util.Pair;
import io.github.maliciousfiles.smpitems.SMPItems;
import io.github.maliciousfiles.smpitems.wand.SpellHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockType;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WallSpellHandler implements SpellHandler {
    private static final int DURATION = 70;
    private static final int WIDTH = 2; // blocks on either side
    private static final int HEIGHT = 4;
    private static final int DISTANCE = 2;

    @Override
    public void castSpell(Player player) {
        double yaw = Math.toRadians(player.getYaw());
        Vector direction = new Vector(-Math.sin(yaw), 0, Math.cos(yaw)).normalize();

        List<Block> blocks = new ArrayList<>();
        for (int yMod = 0; yMod < HEIGHT; yMod++) {
            for (int xMod = -WIDTH; xMod <= WIDTH; xMod++) {
                blocks.add(player.getLocation()
                        .add(direction.clone().multiply(DISTANCE))
                        .add(0, yMod, 0)
                        .add(direction.clone()
                                .crossProduct(new Vector(0, 1, 0)).multiply(xMod)).getBlock());
            }
        }

        Map<Block, Pair<BlockData, BlockEntity>> replaced = new HashMap<>();
        for (Block block : blocks) {
            replaced.put(block, Pair.of(block.getBlockData().clone(),
                    ((CraftWorld) block.getWorld()).getHandle()
                            .getBlockEntity(CraftLocation.toBlockPosition(block.getLocation()))));
            block.setType(Material.OBSIDIAN, false);
        }


        Bukkit.getScheduler().runTaskLater(SMPItems.instance, () -> {
            replaced.forEach((block, pair) -> {
                block.setBlockData(pair.getFirst(), false);

                ServerLevel nms = ((CraftWorld) block.getWorld()).getHandle();
                if (pair.getSecond() == null) nms.removeBlockEntity(CraftLocation.toBlockPosition(block.getLocation()));
                else nms.setBlockEntity(pair.getSecond());
            });
        }, DURATION);
    }
}
