package io.github.maliciousfiles.smpitems.wand.spells;

import com.mojang.math.Transformation;
import io.github.maliciousfiles.smpitems.SMPItems;
import io.github.maliciousfiles.smpitems.wand.SpellHandler;
import io.github.maliciousfiles.smpitems.wand.ToggleableSpellHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftVector;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class SwordsSpellHandler extends ToggleableSpellHandler {

    private static final float RANGE = 30;
    private static final float GOTO_VEL = 6f; // blocks per second
    private static final float ATK_VEL = 6f; // blocks per second
    private static final float ROT_VEL = 1.8f; // secs per rot
    private static final float CIRC_RAD = 1f;
    private static final float MIN_HEIGHT_DIFF = 0.4f; // percent of the entity that it has to move
    private static final int ROT_TIME = 55; // ticks in rot phase
    private static final float SCALE = 0.65f;
    private static final int NUM_SWORD = 3;
    private static final int SWORD_SPACING = 23; // ticks between swords attacking
    private static final float DAMAGE = 3;
    private static final float KB = 0.18f;

    private static final Map<UUID, List<Sword>> swords = new HashMap<>();

    @Override
    public void activate(Player player) {
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
        if (target == null) {
            castSpell(player);
            return;
        };

        List<Sword> swords = new ArrayList<>();
        for (int i = 0; i < NUM_SWORD; i++) {
            Sword sword = new Sword(player, target, (i % 2) * 2 - 1,
                    Mth.TWO_PI / NUM_SWORD * i,
                    (float) target.getHeight() / NUM_SWORD * i,
                    i*SWORD_SPACING);
            sword.setPos(player.getX(), player.getY(), player.getZ());
            sword.level().addFreshEntity(sword);
            swords.add(sword);
        }

        SwordsSpellHandler.swords.put(player.getUniqueId(), swords);
    }

    @Override
    public void deactivate(Player player) {
        List<Sword> swords = SwordsSpellHandler.swords.remove(player.getUniqueId());
        if (swords == null) return;

        for (Sword sword : swords) sword.discard();
    }

    private class Sword extends Display.ItemDisplay {

        private final Player player;
        private final LivingEntity target;
        private final int direction;
        private final int initialRotDel;

        public Sword(Player player, LivingEntity target, int direction, float startRot, float startHeight, int initialRotDel) {
            super(EntityType.ITEM_DISPLAY, ((CraftPlayer) player).getHandle().serverLevel());
            this.setItemStack(CraftItemStack.asNMSCopy(
                    SMPItems.createItemStack(new ItemStack(Material.IRON_SWORD),
                            meta -> meta.setEnchantmentGlintOverride(true))));

            this.setTransformationInterpolationDuration(1);
            this.player = player;
            this.target = target;
            this.prevLoc = target.getLocation();
            this.direction = direction;
            this.initialRotDel = initialRotDel;

            this.stage = Stage.GOTO;
            this.rot = startRot;
            this.height = startHeight;
        }

        private Vector3f pos;
        private Vector3f fakePos;
        @Override
        public void setPos(double x, double y, double z) {
            super.setPos(x, y, z);
            this.pos = new Vector3f((float) x, (float) y, (float) z);
            this.fakePos = new Vector3f((float) x, (float) y, (float) z);
        }

        private enum Stage { GOTO, ROTATE, ATTACK}

        private Stage stage;
        private float rot;
        private float height;
        private boolean attacked;

        private Location prevLoc;

        private void startRot() {
            Bukkit.getScheduler().runTaskLater(SMPItems.instance, () -> stage = Stage.ATTACK,
                    ROT_TIME + (stage == Stage.GOTO ? initialRotDel : 0));
            stage = Stage.ROTATE;

            float oldHeight = height;
            do {
                height = (float) Math.random() * (float) target.getHeight();
            } while (Math.abs(height - oldHeight) < MIN_HEIGHT_DIFF * target.getHeight());
        }

        @Override
        public void tick() {
            if (!target.isValid()) {
                SwordsSpellHandler.this.castSpell(player);
            }

            super.tick();
            this.baseTick();

            fakePos.add((float) (target.getX() - prevLoc.getX()),
                    (float) (target.getY() - prevLoc.getY()),
                    (float) (target.getZ() - prevLoc.getZ()));
            prevLoc = target.getLocation();

            Vector dir;
            if (stage == Stage.GOTO) {
                Vector tarVec = target.getLocation()
                        .add(Mth.sin(rot)*CIRC_RAD, height, Mth.cos(rot)*CIRC_RAD)
                        .toVector();
                Vector curVec = new Vector(fakePos.x(), fakePos.y(), fakePos.z());

                dir = tarVec.clone().subtract(curVec).normalize().multiply(GOTO_VEL / 20);
                fakePos.add((float) dir.getX(), (float) dir.getY(), (float) dir.getZ());

                if (tarVec.distance(curVec) < 0.2f) startRot();
            } else if (stage == Stage.ROTATE) {
                rot = (rot + direction * Mth.TWO_PI / (20*ROT_VEL)) % Mth.TWO_PI;

                Vector curVec = target.getLocation()
                        .add(Mth.sin(rot)*CIRC_RAD, 0, Mth.cos(rot)*CIRC_RAD)
                        .toVector()
                        .setY(fakePos.y);
                fakePos = new Vector3f((float) curVec.getX(), (float) curVec.getY(), (float) curVec.getZ());

                Vector tarVec = target.getLocation()
                        .add(Mth.sin(rot - Mth.PI)*CIRC_RAD, height, Mth.cos(rot - Mth.PI)*CIRC_RAD)
                        .toVector();

                dir = tarVec.subtract(curVec);
            } else {
                Vector curVec = new Vector(fakePos.x(), fakePos.y(), fakePos.z());
                Vector tarVec = target.getLocation()
                        .add(Mth.sin(rot - Mth.PI)*CIRC_RAD, height, Mth.cos(rot - Mth.PI)*CIRC_RAD)
                        .toVector();

                dir = tarVec.clone().subtract(curVec).normalize().multiply(ATK_VEL / 20);
                fakePos.add((float) dir.getX(), (float) dir.getY(), (float) dir.getZ());

                if (!attacked && curVec.setY(0).distance(target.getLocation().toVector().setY(0)) < 0.2f) {
                    attacked = true;
                    target.damage(DAMAGE, player);

                    Vector kbDir = dir.clone().setY(0).normalize();
                    target.knockback(KB, -kbDir.getX(), -kbDir.getZ());
                }

                if (tarVec.distance(curVec) < 0.2f) {
                    rot -= Mth.PI;
                    attacked = false;
                    startRot();
                }
            }

            Vector3f rot = new Vector3f(0,
                    Mth.sign(dir.getZ()) * (float) Math.acos(-dir.clone().setY(0).normalize().getX()),
                    -(float) Math.atan2(dir.getY(), dir.clone().setY(0).length()) + Mth.PI / 4);
            this.setTransformation(new Transformation(
                    new Vector3f(fakePos).sub(pos),
                    new Quaternionf(),
                    new Vector3f(SCALE),
                    new Quaternionf().rotationXYZ(rot.x, rot.y, rot.z)
            ));
        }
    }
}
