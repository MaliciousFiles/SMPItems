package io.github.maliciousfiles.smpitems.wand.spells;

import io.github.maliciousfiles.smpitems.SMPItems;
import io.github.maliciousfiles.smpitems.wand.ToggleableSpellHandler;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LifeDrainSpellHandler extends ToggleableSpellHandler {
    private static final int INTERVAL = 15;
    private static final float HEALING = 0.5f;
    private static final float DAMAGE = 1.5f;
    private static final float RADIUS = 2.75f;
    private static final float PARTICLE_STEP = 0.6f;

    private final Map<UUID, DrainRunnable> runnables = new HashMap<>();

    @Override
    protected void activate(Player player) {
        DrainRunnable runnable = new DrainRunnable(player);
        runnable.runTaskTimer(SMPItems.instance, 0, INTERVAL);

        runnables.put(player.getUniqueId(), runnable);
    }

    @Override
    protected void deactivate(Player player) {
        Optional.ofNullable(runnables.remove(player.getUniqueId())).ifPresent(BukkitRunnable::cancel);
    }

    private static class DrainRunnable extends BukkitRunnable {
        private final OfflinePlayer player;

        public DrainRunnable(OfflinePlayer player) { this.player = player; }

        public void run() {
            if (!player.isOnline()) return;

            player.getPlayer().getLocation().getNearbyLivingEntities(RADIUS).forEach(entity -> {
                if (entity.getUniqueId().equals(player.getUniqueId()) ||
                        !entity.isValid() || !player.getPlayer().hasLineOfSight(entity)) return;

                entity.damage(DAMAGE, DamageSource.builder(DamageType.MAGIC)
                        .withDamageLocation(player.getPlayer().getLocation())
                        .withDirectEntity(player.getPlayer())
                        .withCausingEntity(player.getPlayer())
                        .build());
                player.getPlayer().heal(HEALING, EntityRegainHealthEvent.RegainReason.MAGIC);

                Location entityLoc = entity.getEyeLocation();
                Location playerLoc = player.getPlayer().getEyeLocation();

                Vector direction = entityLoc.clone().subtract(playerLoc).toVector().normalize();
                for (float i = 0.8f; i < entityLoc.distance(playerLoc); i += PARTICLE_STEP) {
                    playerLoc.getWorld().spawnParticle(Particle.DUST,
                            playerLoc.clone().add(direction.clone().multiply(i)), 1, 0, 0,
                            0, 0, new Particle.DustOptions(Color.RED, 1.5f));
                }
            });
        }
    }
}
