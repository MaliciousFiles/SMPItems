package io.github.maliciousfiles.smpitems.wand;

import io.github.maliciousfiles.smpitems.SMPItems;
import io.github.maliciousfiles.smpitems.wand.spells.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.ListPersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class WandItemHelper {

    private static final NamespacedKey SELECTED = SMPItems.key("selected");
    private static final NamespacedKey SPELLS = SMPItems.key("spells");
    private static final NamespacedKey WAND = SMPItems.key("wand");
    private static final NamespacedKey NAME = SMPItems.key("name");

    private static final ListPersistentDataType<String, String> STRING_LIST = PersistentDataType.LIST.listTypeFrom(PersistentDataType.STRING);

    private static <C> C getOrDefault(ItemStack wand, NamespacedKey key, PersistentDataType<?, C> type, C def) {
        return wand.getItemMeta().getPersistentDataContainer().getOrDefault(key, type, def);
    }

    private static <C> void set(ItemStack wand, NamespacedKey key, PersistentDataType<?, C> type, C value) {
        wand.editMeta(meta -> meta.getPersistentDataContainer().set(key, type, value));
    }

    private static void remove(ItemStack wand, NamespacedKey key) {
        wand.editMeta(meta -> meta.getPersistentDataContainer().remove(key));
    }

    public static ItemStack initWand(ItemStack wand) { set(wand, WAND, PersistentDataType.BOOLEAN, true); return wand; }
    public static boolean isWand(ItemStack wand) { return wand != null && wand.getItemMeta() != null && getOrDefault(wand, WAND, PersistentDataType.BOOLEAN, false); }

    private static void updateName(ItemStack wand) {
        wand.editMeta(meta -> meta.displayName(Component.text(getName(wand))
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" (", NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false)
                        .append(Component.text(getSelected(wand).name, NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(")")))));
    }

    public static Spell[] getSpells(ItemStack wand) {
        Spell[] spells = new Spell[9];

        List<String> spellNames = getOrDefault(wand, SPELLS, STRING_LIST, List.of());
        for (int i = 0; i < spells.length; i++) {
            spells[i] = i < spellNames.size() ? Spell.valueOf(spellNames.get(i)) : Spell.EMPTY;
        }

        return spells;
    }

    public static void setName(ItemStack wand, String name) {
        if (name == null || name.isEmpty()) {
            remove(wand, NAME);
        } else {
            set(wand, NAME, PersistentDataType.STRING, name);
        }

        updateName(wand);
    }

    public static String getName(ItemStack wand) {
        return getOrDefault(wand, NAME, PersistentDataType.STRING, "Wand");
    }

    public static void setSpells(ItemStack wand, Spell[] spells) {
        set(wand, SPELLS, STRING_LIST, Arrays.stream(spells).map(Enum::name).toList());

        updateName(wand);
    }

    public static int getSelectedIdx(ItemStack wand) {
        return getOrDefault(wand, SELECTED, PersistentDataType.INTEGER, 0);
    }

    public static Spell getSelected(ItemStack wand) {
        return getSpells(wand)[getSelectedIdx(wand)];
    }

    public static Spell setSelected(ItemStack wand, int idx) {
        set(wand, SELECTED, PersistentDataType.INTEGER, idx);

        updateName(wand);
        return getSpells(wand)[idx];
    }

    // TODO: make all line spells raycast (i.e. go through open doors)
    public enum Spell {
        EMPTY("Empty", p -> {}),
        PUSH("Push", new PushSpellHandler()),                   // air
        FIREBALL("Fireball", new FireballSpellHandler()),                        // fire
        ICE_BRIDGE("Bridge of Ice", new IceBridgeSpellHandler()),                 // water
        WALL("Impenetrable Wall", new WallSpellHandler()),                   // earth
        TELEPORT("Backstab", new TeleportSpellHandler()),                        // chaos
        LASER("Sonic Shriek", new LaserSpellHandler()),                       // energy
        LIFE_DRAIN("Vampirism", new LifeDrainSpellHandler()),                     // life
        LEVITATE("Hold Monster", new LevitateSpellHandler()),                    // order
        SWORDS("Haunted Daggers", new SwordsSpellHandler());                   // death

        private static final NamespacedKey SPELL = SMPItems.key("spell");

        public final String name;
        public final SpellHandler handler;

        Spell(String name, SpellHandler handler) {
            this.name = name;
            this.handler = handler;
        }

        public ItemStack getItem() {
            return SMPItems.createItemStack(new ItemStack(Material.RECOVERY_COMPASS), meta -> {
                meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
                meta.setCustomModelData(SMPItems.MODEL_DATA_BASE + ordinal());
                meta.getPersistentDataContainer().set(SPELL, PersistentDataType.STRING, name());
                meta.setMaxStackSize(1);
            });
        }

        public static Spell fromItem(ItemStack item) {
            if (item == null) return Spell.EMPTY;

            try {
                return Spell.valueOf(Optional.ofNullable(item.getItemMeta()
                        .getPersistentDataContainer().get(SPELL, PersistentDataType.STRING)).orElse(""));
            } catch (IllegalArgumentException ignored) {
                return Spell.EMPTY;
            }
        }

        public static boolean isSpell(ItemStack item) {
            return fromItem(item) != Spell.EMPTY;
        }
    }
}
