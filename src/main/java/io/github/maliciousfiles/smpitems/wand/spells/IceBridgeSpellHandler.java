package io.github.maliciousfiles.smpitems.wand.spells;

import io.github.maliciousfiles.smpitems.SMPItems;
import io.github.maliciousfiles.smpitems.wand.SpellHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.block.impl.CraftFluids;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IceBridgeSpellHandler implements SpellHandler {
    private static final float RANGE = 30;
    private static final int DECAY_TIME = 35; // ticks
    private static final int PLACE_SPEED = 2; // ticks per block
    private static final int WIDTH = 1; // number of blocks on either side
    private static final float LERP_INTERVAL = 0.1f;

    @Override
    public void castSpell(Player player) {
        BridgeRunnable runnable = new BridgeRunnable(player);

        Bukkit.getPluginManager().registerEvents(runnable, SMPItems.instance);
        runnable.runTaskTimer(SMPItems.instance, 0, PLACE_SPEED);
    }

    private static class BridgeRunnable extends BukkitRunnable implements Listener {
        private int blocksPlaced = 0;
        private final List<Map<Block, Material>> blocks = new ArrayList<>();

        private final Player player;
        private final Vector direction;
        private final Location[] starts;

        public BridgeRunnable(Player player) {
            this.player = player;

            double yaw = Math.toRadians(player.getYaw());
            direction = new Vector(-Math.sin(yaw), 0, Math.cos(yaw)).normalize();

            starts = new Location[1+WIDTH*2];
            starts[0] = player.getLocation().add(0, -1, 0);

            for (int i = 0; i < WIDTH; i++) {
                starts[2*i+1] = starts[0].clone().add(direction.clone().crossProduct(new Vector(0, 1, 0)).multiply(i+1));
                starts[2*i+2] = starts[0].clone().add(direction.clone().crossProduct(new Vector(0, 1, 0)).multiply(-(i+1)));
            }
        }

        public void run() {
            if (blocksPlaced >= RANGE) {
                cancel();
                return;
            }

            Map<Block, Material> blocks = new HashMap<>();
            for (Location start : starts) {
                for (float i = blocksPlaced-1; i <= blocksPlaced; i += LERP_INTERVAL) {
                    Location blockLocation = start.clone().add(direction.clone().multiply(i));
                    Block block = player.getWorld().getBlockAt(blockLocation);

                    if (block.isEmpty() || block.getBlockData() instanceof CraftFluids l && l.getLevel() == 0) {
                        blocks.put(block, block.getType());
                        block.setType(Material.PACKED_ICE);
                    } else if (block.isSolid() && !block.getType().name().contains("ICE")) {
                        Block top = blockLocation.add(0, 1, 0).getBlock();
                        if (top.isEmpty()) {
                            blocks.put(top, top.getType());
                            top.setType(Material.SNOW);
                        }
                    }
                }
            }

            this.blocks.add(blocks);
            blocksPlaced++;
        }

        public void cancel() {
            super.cancel();

            new BukkitRunnable() {
                private int blocksRemoved = 0;

                public void run() {
                    if (blocksRemoved >= blocks.size()) {
                        cancel();
                        return;
                    }

                    blocks.get(blocksRemoved++).forEach(Block::setType);
                }

                public void cancel() {
                    super.cancel();
                    BlockBreakEvent.getHandlerList().unregister(BridgeRunnable.this);
                    BlockPlaceEvent.getHandlerList().unregister(BridgeRunnable.this);
                }
            }.runTaskTimer(SMPItems.instance, DECAY_TIME, PLACE_SPEED);
        }

        @EventHandler
        public void onBreakBlock(BlockBreakEvent evt) {
            for (Map<Block, Material> map : blocks) {
                Material material;
                if ((material = map.remove(evt.getBlock())) != null) {
                    evt.getBlock().setType(material);
                    evt.setCancelled(true);
                    return;
                }
            }
        }

        @EventHandler
        public void onPlaceBlock(BlockPlaceEvent evt) {
            blocks.forEach(m->m.remove(evt.getBlock()));
        }
    }
}
