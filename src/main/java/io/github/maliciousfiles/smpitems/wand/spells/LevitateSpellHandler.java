package io.github.maliciousfiles.smpitems.wand.spells;

import io.github.maliciousfiles.smpitems.SMPItems;
import io.github.maliciousfiles.smpitems.wand.SpellHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class LevitateSpellHandler implements SpellHandler {

    private static final float RANGE = 20;
    private static final float LEV_HEIGHT = 1.6f;
    private static final int LEV_TIME = 65;
    private static final int DURATION = 130;
    private static final float WIGGLE_AMT = 0.03f;

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
        LivingEntity finalTarget = target;
        finalTarget.setGravity(false);
        if (finalTarget instanceof Mob mob) mob.setAware(false);

        new BukkitRunnable() {
            private final Location start = finalTarget.getLocation();
            private float ticks = 0;
            private float height;

            public void run() {
                if (!finalTarget.isValid()) {
                    cancel();
                    return;
                }

                if (height < LEV_HEIGHT && !finalTarget.collidesAt(finalTarget.getLocation())) height += LEV_HEIGHT/LEV_TIME;

                Location loc = start.clone().add(0, height, 0)
                        .add(finalTarget.getLocation().getDirection()
                                .crossProduct(new Vector(0, 1, 0)).multiply(ticks % 2 == 0 ? -WIGGLE_AMT : WIGGLE_AMT));
                finalTarget.teleport(loc);

                if (ticks++ >= DURATION) {
                    finalTarget.setGravity(true);
                    if (finalTarget instanceof Mob mob) mob.setAware(true);
                    cancel();
                }
            }
        }.runTaskTimer(SMPItems.instance, 0, 1);
    }
}
