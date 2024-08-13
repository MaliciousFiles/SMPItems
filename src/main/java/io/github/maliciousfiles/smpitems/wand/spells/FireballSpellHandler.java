package io.github.maliciousfiles.smpitems.wand.spells;

import io.github.maliciousfiles.smpitems.wand.SpellHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class FireballSpellHandler implements SpellHandler {
    public static final float VEL = 1.3f;
    public static final float RADIUS = 3;
    private static final float DAMAGE = 9;
    private static final float KB_MULTIPLIER = 1.5f;
    private static final float SPREAD = 20;
    private static final int EXTRA = 1; // extra fireballs on either side

    @Override
    public void castSpell(Player player) {
        for (int i = -EXTRA; i <= EXTRA; i++) {
            Fireball fireball = new Fireball(((CraftPlayer) player).getHandle().serverLevel());

            // from Player#launchProjectile
            fireball.preserveMotion = true;
            fireball.moveTo(CraftLocation.toBlockPosition(player.getEyeLocation()), player.getYaw(), player.getPitch());

            Location loc = player.getLocation();
            loc.setYaw(loc.getYaw() + SPREAD * i);

            fireball.getBukkitEntity().setVelocity(loc.getDirection().multiply(VEL));
            fireball.level().addFreshEntity(fireball);
        }
    }

    private static class Fireball extends LargeFireball {
        public Fireball(ServerLevel level) {
            super(EntityType.FIREBALL, level);
        }

        @Override
        public void tick() {
            super.tick();
            setDeltaMovement(getDeltaMovement().scale(0.99).subtract(0, 0.08, 0));
            markHurt();
        }

        @Override
        protected void onHit(HitResult hitResult) {
            if (hitResult instanceof EntityHitResult ehr && !(ehr.getEntity() instanceof LivingEntity)) return;

            this.level().gameEvent(GameEvent.PROJECTILE_LAND, hitResult.getLocation(),
                    GameEvent.Context.of(this, hitResult instanceof BlockHitResult bhr ? this.level().getBlockState(bhr.getBlockPos()) : null));

            this.level().explode(this, null, new ExplosionDamageCalculator() {
                @Override
                public float getEntityDamageAmount(Explosion explosion, Entity entity) {
                    Vec3 vec3 = explosion.center();
                    double d = Math.sqrt(entity.distanceToSqr(vec3)) / DAMAGE;
                    double e = (1.0 - d) * (double)Explosion.getSeenPercent(vec3, entity);
                    return (float)((e * e + e) / 2.0 * 7.0 * DAMAGE + 1.0);
                }

                @Override
                public float getKnockbackMultiplier(Entity entity) { return KB_MULTIPLIER; }
            }, this.getX(), this.getY(), this.getZ(), RADIUS, true, Level.ExplosionInteraction.NONE);

            this.discard();
        }
    }
}
