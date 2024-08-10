package io.github.maliciousfiles.smpitems.wand.spells;

import io.github.maliciousfiles.smpitems.wand.SpellHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PushSpellHandler implements SpellHandler {
    @Override
    public void castSpell(Player player) {
        player.getNearbyEntities(3.5, 3.5, 3.5).forEach(entity -> {
            Location entityLocation = entity.getLocation();
            if (Math.abs(entityLocation.getY()-player.getLocation().getY()) <= 0.25) {
                entityLocation.add(0, 1, 0);
            }

            entity.setVelocity(entityLocation.subtract(player.getLocation()).toVector().normalize().multiply(1.5));
        });
    }
}
