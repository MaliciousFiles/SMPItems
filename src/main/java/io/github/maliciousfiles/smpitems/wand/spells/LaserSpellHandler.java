package io.github.maliciousfiles.smpitems.wand.spells;

import io.github.maliciousfiles.smpitems.SMPItems;
import io.github.maliciousfiles.smpitems.wand.SpellHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class LaserSpellHandler implements SpellHandler {
    private static final float RANGE = 20;
    private static final float STEP = 1.75f;
    private static final int SPEED = 1; // ticks per step
    private static final float DAMAGE = 14;
    private static final float RADIUS = 2.75f;
    private static final float KB_MULTIPLIER = 1.65f;

    @Override
    public void castSpell(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 3, 1);

        Vector direction = player.getLocation().getDirection();

        int passesPerStep = (int) (STEP/0.1f);
        new BukkitRunnable() {
            Location loc = player.getEyeLocation();

            float i = 0.2f;
            public void run() {
                if (i >= RANGE) {
                    cancel();
                    return;
                }

                Location newLoc = null;
                for (int j = 0; j < passesPerStep; j++) {
                    newLoc = player.getEyeLocation().add(direction.clone().multiply(i));

                    Block block = newLoc.getBlock();

                    Vector pointInBlock = new Vector(newLoc.getX() - block.getX(), newLoc.getY() - block.getY(), newLoc.getZ() - block.getZ());
                    if (block.getCollisionShape().overlaps(new BoundingBox(
                            pointInBlock.getX() - 0.1f, pointInBlock.getY() - 0.1f, pointInBlock.getZ() - 0.1f,
                            pointInBlock.getX() + 0.1f, pointInBlock.getY() + 0.1f, pointInBlock.getZ() + 0.1f))) {

                        cancel();
                        return;
                    }

                    i += 0.1f;
                }

                loc = newLoc;
                player.getWorld().spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0);

                if (!loc.getNearbyLivingEntities(0.8f, le -> !le.equals(player)).isEmpty()) cancel();
            }

            public void cancel() {
                super.cancel();

                for (int i = 0; i < 2; i++) loc.getWorld().strikeLightningEffect(loc);

                ((CraftWorld) loc.getWorld()).getHandle().explode(null, null, new ExplosionDamageCalculator() {
                    @Override
                    public float getEntityDamageAmount(Explosion explosion, Entity entity) {
                        Vec3 vec3 = explosion.center();
                        double d = Math.sqrt(entity.distanceToSqr(vec3)) / DAMAGE;
                        double e = (1.0 - d) * (double)Explosion.getSeenPercent(vec3, entity);
                        return (float)((e * e + e) / 2.0 * 7.0 * DAMAGE + 1.0);
                    }

                    @Override
                    public float getKnockbackMultiplier(Entity entity) { return KB_MULTIPLIER; }
                }, loc.getX(), loc.getY(), loc.getZ(), RADIUS, false, Level.ExplosionInteraction.NONE);
                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 10, 0.6, 0.6, 0.6, 0);
            }
        }.runTaskTimer(SMPItems.instance, 0, SPEED);
    }
}
