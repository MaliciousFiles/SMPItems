package io.github.maliciousfiles.smpitems.wand;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class ToggleableSpellHandler implements SpellHandler {

    private final List<UUID> active = new ArrayList<>();

    public void castSpell(Player player) {
        if (isActive(player)) {
            deactivate(player);
            active.remove(player.getUniqueId());
        } else {
            activate(player);
            active.add(player.getUniqueId());
        }
    }

    public boolean isActive(Player player) {
        return active.contains(player.getUniqueId());
    }

    protected abstract void activate(Player player);
    protected abstract void deactivate(Player player);
}
