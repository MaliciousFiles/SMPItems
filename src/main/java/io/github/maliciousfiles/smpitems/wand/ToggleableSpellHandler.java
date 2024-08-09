package io.github.maliciousfiles.smpitems.wand;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public abstract class ToggleableSpellHandler implements SpellHandler {

    private final List<Player> active = new ArrayList<>();

    public void castSpell(Player player) {
        if (active.contains(player)) {
            deactivate(player);
            active.remove(player);
        } else {
            activate(player);
            active.add(player);
        }
    }

    public boolean isActive(Player player) {
        return active.contains(player);
    }

    protected abstract void activate(Player player);
    protected abstract void deactivate(Player player);
}
