package io.github.maliciousfiles.smpitems.wand.spells;

import io.github.maliciousfiles.smpitems.SMPItems;
import io.github.maliciousfiles.smpitems.wand.SpellHandler;
import io.papermc.paper.entity.LookAnchor;
import net.minecraft.core.particles.BlockParticleOption;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class TeleportSpellHandler implements SpellHandler {

    private static final float RANGE = 20;
    private static final float DIST = 1.8f;
    private static final int TIME_TO_HIT = 40;
    private static final float DMG_MOD = 2;
    private static final float HEIGHT = 0.5f;

    @Override
    public void castSpell(Player player) {
        Vector direction = player.getLocation().getDirection();

        LivingEntity target = null;
        for (float i = 0; i < RANGE; i += 0.1f) {
            Location loc = player.getEyeLocation().add(direction.clone().multiply(i));

            Block block = loc.getBlock();
            Vector pointInBlock = new Vector(loc.getX() - block.getX(), loc.getY() - block.getY(), loc.getZ() - block.getZ());
            if (block.getCollisionShape().overlaps(new BoundingBox(
                    pointInBlock.getX() - 0.1f, pointInBlock.getY() - 0.1f, pointInBlock.getZ() - 0.1f,
                    pointInBlock.getX() + 0.1f, pointInBlock.getY() + 0.1f, pointInBlock.getZ() + 0.1f))) return;

            target = loc.getNearbyLivingEntities(0.1, le -> le != player).stream().findFirst().orElse(null);
            if (target != null) break;
        }
        if (target == null) return;

        player.teleport(target.getLocation()
                .subtract(target.getLocation().getDirection().multiply(DIST))
                .add(0, HEIGHT, 0));
        player.lookAt(target, LookAnchor.EYES, LookAnchor.EYES);

        new TeleportRunnable(player, target).runTaskLater(SMPItems.instance, TIME_TO_HIT);
    }

    private static class TeleportRunnable extends BukkitRunnable implements Listener {

        private final Player player;
        private final LivingEntity target;

        public TeleportRunnable(Player player, LivingEntity target) {
            this.player = player;
            this.target = target;

            Bukkit.getPluginManager().registerEvents(this, SMPItems.instance);
        }

        public void run() {
            EntityDamageByEntityEvent.getHandlerList().unregister(this);
            cancel();
        }

        @EventHandler
        public void onHit(EntityDamageByEntityEvent evt) {
            if (evt.getDamager() != player) return;

            if (evt.getEntity() == target) {
                evt.setDamage(evt.getDamage()*DMG_MOD);

                Vector direction = evt.getDamager().getLocation().getDirection();
                Location start = ((LivingEntity) evt.getDamager()).getEyeLocation();

                Location loc = ((LivingEntity) evt.getEntity()).getEyeLocation();
                for (float i = 0; i < evt.getDamager().getLocation().distance(evt.getEntity().getLocation()); i += 0.1f) {
                    loc = start.clone().add(direction.clone().multiply(i));
                    if (evt.getEntity().getBoundingBox().contains(loc.toVector())) break;
                }
                // TODO: make crit

                evt.getEntity().getWorld().spawnParticle(Particle.BLOCK, loc,
                        80, 0, 0, 0, 0.5,
                        Material.REDSTONE_BLOCK.asBlockType().createBlockData());
            }

            run();
        }
    }
}
