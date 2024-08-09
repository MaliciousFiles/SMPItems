package io.github.maliciousfiles.smpitems.wand;

import io.github.maliciousfiles.smpitems.SMPItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.ListPersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WandItemHelper {

    private static final NamespacedKey SELECTED = SMPItems.key("selected");
    private static final NamespacedKey SPELLS = SMPItems.key("spells");
    private static final NamespacedKey WAND = SMPItems.key("wand");

    private static final ListPersistentDataType<String, String> STRING_LIST = PersistentDataType.LIST.listTypeFrom(PersistentDataType.STRING);

    private static <C> C getOrDefault(ItemStack wand, NamespacedKey key, PersistentDataType<?, C> type, C def) {
        return wand.getItemMeta().getPersistentDataContainer().getOrDefault(key, type, def);
    }

    private static <C> void set(ItemStack wand, NamespacedKey key, PersistentDataType<?, C> type, C value) {
        wand.editMeta(meta -> meta.getPersistentDataContainer().set(key, type, value));
    }

    public static ItemStack initWand(ItemStack wand) { set(wand, WAND, PersistentDataType.BOOLEAN, true); return wand; }
    public static boolean isWand(ItemStack wand) { return getOrDefault(wand, WAND, PersistentDataType.BOOLEAN, false); }

    public static Spell[] getSpells(ItemStack wand) {
        Spell[] spells = new Spell[Spell.values().length];

        List<String> spellNames = getOrDefault(wand, SPELLS, STRING_LIST, List.of());
        for (int i = 0; i < spells.length; i++) {
            spells[i] = i < spellNames.size() ? Spell.valueOf(spellNames.get(i)) : Spell.EMPTY;
        }

        return spells;
    }

    public static void setSpells(ItemStack wand, Spell[] spells) {
        set(wand, SPELLS, STRING_LIST, Arrays.stream(spells).map(Enum::name).toList());
    }

    public static Spell getSelected(ItemStack wand) {
        return getSpells(wand)[getOrDefault(wand, SELECTED, PersistentDataType.INTEGER, 0)];
    }

    public static Spell setSelected(ItemStack wand, int idx) {
        set(wand, SELECTED, PersistentDataType.INTEGER, idx);

        return getSpells(wand)[idx];
    }

    public static Spell modSelected(ItemStack wand, int mod) {
        int newIdx = getOrDefault(wand, SELECTED, PersistentDataType.INTEGER, 0) + mod;

        return setSelected(wand, newIdx);
    }

    public enum Spell {
        EMPTY("Empty", p -> {}),
        PUSH("Push", (p) -> {}),
        FIREBALL("Fireball", (p) -> {}),
        ICE_BRIDGE("Bridge of Ice", (p) -> {}),
        WALL("Impenetrable Wall", (p) -> {}),
        TELEPORT("Backstab", (p) -> {}),
        ARROW_CLOUD("Arrow Storm", (p) -> {}),
        LIFE_DRAIN("Vampirism", (p) -> {}),
        LEVITATE("Hold Monster", (p) -> {}),
        SWORDS("Haunted Dagger", (p) -> {});

        private static final NamespacedKey SPELL = SMPItems.key("spell");

        public final String name;
        public final SpellHandler handler;

        Spell(String name, SpellHandler handler) {
            this.name = name;
            this.handler = handler;
        }

        public ItemStack getItem() {
            return SMPItems.createItemStack(new ItemStack(Material.CLOCK), meta -> {
                meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
                meta.setCustomModelData(SMPItems.MODEL_DATA_BASE + ordinal());
                meta.getPersistentDataContainer().set(SPELL, PersistentDataType.STRING, name());
            });
        }

        public static Spell fromItem(ItemStack item) {
            if (item == null) return null;

            try {
                return Spell.valueOf(item.getItemMeta().getPersistentDataContainer().get(SPELL, PersistentDataType.STRING));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        public static boolean isSpell(ItemStack item) {
            return fromItem(item) != null;
        }
    }
}
