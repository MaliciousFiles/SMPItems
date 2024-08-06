package io.github.maliciousfiles.smpitems.teleportationdevice;

import com.mojang.datafixers.util.Pair;
import io.github.maliciousfiles.smpitems.SMPItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

public class TeleportationDevice implements Cloneable {
    private static final int MODEL_DATA_BASE = 9123487;


    public static final TeleportationDevice BASE = new TeleportationDevice(
            250, 5, 1, 10, false,
            Component.text("Teleportation Device")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decorate(TextDecoration.BOLD));

    private static final NamespacedKey RANGE_KEY = SMPItems.key("range");
    private static final NamespacedKey CONNECTIONS_KEY = SMPItems.key("connections");
    private static final NamespacedKey ANCHORS_KEY = SMPItems.key("anchors");
    private static final NamespacedKey ITEMS_KEY = SMPItems.key("items");
    private static final NamespacedKey USE_TIME_KEY = SMPItems.key("use_time");
    private static final NamespacedKey ID_KEY = SMPItems.key("id");
    private static final NamespacedKey UPGRADEABLE_KEY = SMPItems.key("upgradeable");
    private static final NamespacedKey UPGRADES_KEY = SMPItems.key("upgrades");
    private static final NamespacedKey FAVORITES_KEY = SMPItems.key("favorites");
    private static final NamespacedKey SELECTED_KEY = SMPItems.key("selected");

    private static final UUIDPersistentDataType UUID_TYPE = new UUIDPersistentDataType();
    private static final SelectionPersistentDataType SELECTION_TYPE = new SelectionPersistentDataType();

    private static final PersistentDataType<List<PersistentDataContainer>, List<Object>> SELECTION_LIST = PersistentDataType.LIST.listTypeFrom(new SelectionPersistentDataType());
    private static final PersistentDataType<List<String>, List<UpgradeType>> UPGRADE_LIST = PersistentDataType.LIST.listTypeFrom(new UpgradePersistentDataType());
    private static final PersistentDataType<List<PersistentDataContainer>, List<Location>> LOC_LIST = PersistentDataType.LIST.listTypeFrom(new LocationPersistentDataType());
    private static final PersistentDataType<List<String>, List<UUID>> UUID_LIST = PersistentDataType.LIST.listTypeFrom(UUID_TYPE);

    private int range;
    private int uses;
    private int connections;
    private int useTime;
    private boolean finalUpgradeable;

    private Component name;

    private UUID id;
    private List<Location> anchors = new ArrayList<>();
    private List<UUID> items = new ArrayList<>();
    private List<UpgradeType> upgrades = new ArrayList<>();

    public static final Object NO_SELECTION = new UUID(0, 0);
    public static final int MAX_FAV = 3;
    private List<Object> favorites = new ArrayList<>();
    private Object selected = NO_SELECTION;

    private TeleportationDevice(int range, int uses, int connections, int useTime, boolean finalUpgradeable, Component name) {
        this(range, uses, connections, useTime, finalUpgradeable, name, null);
    }

    private TeleportationDevice(int range, int uses, int connections, int useTime, boolean finalUpgradeable, Component name, UUID id) {
        this.range = range;
        this.uses = uses;
        this.connections = connections;
        this.useTime = useTime;
        this.finalUpgradeable = finalUpgradeable;
        this.name = name;
        this.id = id;
    }

    public static TeleportationDevice fromItem(ItemStack item) {
        if (item == null) return null;
        if (!(item.getItemMeta() instanceof Damageable meta)) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.get(ID_KEY, UUID_TYPE) == null) return null;

        TeleportationDevice device = new TeleportationDevice(
                pdc.get(RANGE_KEY, PersistentDataType.INTEGER),
                meta.getMaxDamage(),
                pdc.get(CONNECTIONS_KEY, PersistentDataType.INTEGER),
                pdc.get(USE_TIME_KEY, PersistentDataType.INTEGER),
                pdc.get(UPGRADEABLE_KEY, PersistentDataType.BOOLEAN),
                meta.displayName(),
                pdc.get(ID_KEY, UUID_TYPE)
        );

        device.anchors = new ArrayList<>(pdc.get(ANCHORS_KEY, LOC_LIST));
        device.items = new ArrayList<>(pdc.get(ITEMS_KEY, UUID_LIST));
        device.upgrades = new ArrayList<>(pdc.get(UPGRADES_KEY, UPGRADE_LIST));
        device.favorites = new ArrayList<>(pdc.get(FAVORITES_KEY, SELECTION_LIST));
        device.selected = pdc.getOrDefault(SELECTED_KEY, SELECTION_TYPE, NO_SELECTION);

        return device;
    }

    public int getUseTime() { return useTime; }
    public int getRange() { return range; }
    public int getConnections() { return connections; }
    public int getNumAnchors() { return anchors.size(); }
    public List<Object> getFavorites() { return List.copyOf(favorites); }
    public Object getSelected() { return selected; }
    public UUID getId() { return id; }
    public boolean isEvolved() { return finalUpgradeable; }
    public List<Location> getAnchors() { return List.copyOf(anchors); }
    public List<UUID> getItems() { return List.copyOf(items); }

    public boolean hasUpgrade(UpgradeType upgrade) {
        return upgrades.contains(upgrade);
    }

    public boolean hasAnchor(Location loc) {
        return anchors.contains(loc);
    }

    public boolean isLinked(UUID uuid) {
        return items.contains(uuid);
    }

    public TeleportationDevice evolved() {
        TeleportationDevice evolved = clone();
        evolved.range += 500;
        evolved.uses += 15;
        evolved.finalUpgradeable = true;
        evolved.name = Component.text("Evolved Teleportation Device")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);

        return evolved;
    }

    public TeleportationDevice withUpgrades(UpgradeType... upgrades) {
        TeleportationDevice device = clone();
        device.upgrades.addAll(List.of(upgrades));

        for (UpgradeType upgrade : upgrades) upgrade.apply(device);
        return device;
    }

    public TeleportationDevice toggleAnchor(Location location) {
        if (anchors.contains(location)) {
            anchors.remove(location);

            if (location.equals(selected)) selected = NO_SELECTION;
            favorites.remove(location);
        } else if (anchors.size() < connections || connections == -1) {
            anchors.add(location);

            if (favorites.size() < MAX_FAV) favorites.add(location);
        }

        return this;
    }

    public TeleportationDevice toggleItemLink(UUID item) {
        if (items.contains(item)) {
            items.remove(item);

            if (item.equals(selected)) selected = NO_SELECTION;
            favorites.remove(item);
        } else {
            items.add(item);

            if (favorites.size() < MAX_FAV) favorites.add(item);
        }

        return this;
    }

    public TeleportationDevice removeFavorite(Object obj) {
        favorites.remove(obj);
        if (obj == selected) selected = NO_SELECTION;
        return this;
    }

    public void addFavorite(Object obj) {
        if (favorites.size() < MAX_FAV) favorites.add(obj);
    }


    public TeleportationDevice select(Object object) {
        if (!(object instanceof Location || object instanceof UUID)) throw new IllegalArgumentException();

        this.selected = object;
        return this;
    }

    public TeleportationDevice withName(Component name) {
        TeleportationDevice device = clone();
        device.name = name;

        return device;
    }

    public ItemStack asItem() {
        ItemStack stack = new ItemStack(Material.SHIELD);

        updateItem(stack);
        return stack;
    }

    private String getUpgradeCompletion(UpgradeType... upgrades) {
        int num = Arrays.stream(upgrades).mapToInt(u -> (int) this.upgrades.stream().filter(u::equals).count()).sum();
        int max = Arrays.stream(upgrades).mapToInt(u -> u.limit).sum();

        return " (%s/%s)".formatted(num, max);
    }

    public void updateItem(ItemStack stack) {
        stack.editMeta(Damageable.class, meta -> {
            meta.setMaxStackSize(1);
            meta.setMaxDamage(uses == -1 ? null : uses);
            meta.displayName(name);

            int modelDataMod = uses != -1 && meta.getDamage() >= uses ? 1 : 0;
            meta.setCustomModelData(MODEL_DATA_BASE + modelDataMod);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(RANGE_KEY, PersistentDataType.INTEGER, range);
            pdc.set(CONNECTIONS_KEY, PersistentDataType.INTEGER, connections);
            pdc.set(USE_TIME_KEY, PersistentDataType.INTEGER, useTime);
            if (id != null) pdc.set(ID_KEY, UUID_TYPE, id);
            pdc.set(UPGRADEABLE_KEY, PersistentDataType.BOOLEAN, finalUpgradeable);
            pdc.set(ANCHORS_KEY, LOC_LIST, anchors);
            pdc.set(ITEMS_KEY, UUID_LIST, items);
            pdc.set(UPGRADES_KEY, UPGRADE_LIST, upgrades);
            pdc.set(FAVORITES_KEY, SELECTION_LIST, favorites);
            if (selected != null) pdc.set(SELECTED_KEY, SELECTION_TYPE, selected);

            meta.lore(List.of(
                    Component.text("Shift-right click to cycle favorites (hold for menu)")
                            .decoration(TextDecoration.ITALIC, false)
                            .color(NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Can Take Final Upgrades: ")
                            .append(Component.text(finalUpgradeable ? "yes" : "no", NamedTextColor.WHITE))
                            .decoration(TextDecoration.ITALIC, false)
                            .color(NamedTextColor.GRAY),
                    Component.text("Range: ")
                            .append(Component.text(range == -1 ? "∞" : String.valueOf(range), NamedTextColor.WHITE))
                            .append(Component.text(getUpgradeCompletion(UpgradeType.RANGE, UpgradeType.FINAL_RANGE)))
                            .decoration(TextDecoration.ITALIC, false)
                            .color(NamedTextColor.GRAY),
                    Component.text("Use Time: ")
                            .append(Component.text(useTime, NamedTextColor.WHITE))
                            .append(Component.text(getUpgradeCompletion(UpgradeType.USE_TIME)))
                            .decoration(TextDecoration.ITALIC, false)
                            .color(NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Anchors: %s/%s".formatted(anchors.size(), connections == -1 ? "∞" : connections))
//                            .append(Component.text(getUpgradeCompletion(UpgradeType.CONNECTIONS)))
                            .decoration(TextDecoration.ITALIC, false)
                            .color(NamedTextColor.GRAY),
                    Component.text("Linked: %s".formatted(items.size()))
                            .decoration(TextDecoration.ITALIC, false)
                            .color(NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Uses: "+(uses == -1 ? "∞" : "%s/%s".formatted(uses-meta.getDamage(), uses)))
//                            .append(Component.text(getUpgradeCompletion(UpgradeType.USES)))
                            .decoration(TextDecoration.ITALIC, false)
                            .color(NamedTextColor.GRAY)
            ));
        });
    }

    public TeleportationDevice clone() {
        TeleportationDevice device = new TeleportationDevice(range, uses, connections, useTime, finalUpgradeable, name, id);
        device.anchors = new ArrayList<>(anchors);
        device.items = new ArrayList<>(items);
        device.upgrades = new ArrayList<>(upgrades);
        device.favorites = new ArrayList<>(favorites);
        device.selected = selected;

        return device;
    }

    public static void initUUID(ItemStack item) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(ID_KEY, UUID_TYPE, UUID.randomUUID()));
    }

    public static boolean isAnchor(Block block) {
        return block != null && block.getChunk().getPersistentDataContainer().getOrDefault(ANCHORS_KEY, LOC_LIST, List.of()).contains(block.getLocation());
    }

    private static String locToString(Location loc) {
        return "%s/%s/%s".formatted(Math.floorMod(loc.getBlockX(), 16), Math.floorMod(loc.getBlockY(), 16), Math.floorMod(loc.getBlockZ(), 16));
    }

    public static String getAnchorName(Location loc) {
        return loc.getBlock().getChunk().getPersistentDataContainer().getOrDefault(SMPItems.key(locToString(loc)), PersistentDataType.STRING, "");
    }

    public static void setAnchor(Block block, String name) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();

        List<Location> anchors = new ArrayList<>(pdc.getOrDefault(ANCHORS_KEY, LOC_LIST, List.of()));
        if (name != null) {
            anchors.add(block.getLocation());
            pdc.set(SMPItems.key(locToString(block.getLocation())), PersistentDataType.STRING, name);
        } else {
            anchors.remove(block.getLocation());
            pdc.remove(SMPItems.key(locToString(block.getLocation())));
        }

        pdc.set(ANCHORS_KEY, LOC_LIST, anchors);
    }

    public static Pair<Player, TeleportationDevice> getPlayerWithItem(UUID item) {
        return Bukkit.getOnlinePlayers().stream().map(p-> {
            TeleportationDevice device = Arrays.stream(p.getInventory().getContents())
                    .map(TeleportationDevice::fromItem).filter(d -> d != null && d.id.equals(item)).findFirst().orElse(null);

            return device == null ? null : Pair.of((Player) p, device);
        }).filter(Objects::nonNull).findFirst().orElse(null);
    }

    public enum UpgradeType {
        RANGE(2, List.of(
                Pair.of(Material.LAPIS_BLOCK, 2), Pair.of(Material.GOLD_BLOCK, 2), Pair.of(Material.LAPIS_BLOCK, 2),
                Pair.of(Material.GOLD_BLOCK, 2),         Pair.of(null, 0),         Pair.of(Material.GOLD_BLOCK, 2),
                Pair.of(Material.LAPIS_BLOCK, 2), Pair.of(Material.GOLD_BLOCK, 2), Pair.of(Material.LAPIS_BLOCK, 2)
        ), device -> device.range += 500, Pair.of("Range: ", "+500")),
        FINAL_RANGE(RANGE, List.of(
                Pair.of(Material.GOLD_BLOCK, 1), Pair.of(Material.NETHERITE_INGOT, 1), Pair.of(Material.GOLD_BLOCK, 1),
                Pair.of(Material.END_CRYSTAL, 1),         Pair.of(null, 0),            Pair.of(Material.END_CRYSTAL, 1),
                Pair.of(Material.SHULKER_SHELL, 1), Pair.of(Material.GOLD_BLOCK, 1), Pair.of(Material.SHULKER_SHELL, 1)
        ), device -> device.range = -1, Pair.of("Range: ", "∞")),
        USE_TIME(2, List.of(
                Pair.of(Material.REDSTONE_BLOCK, 2), Pair.of(Material.QUARTZ_BLOCK, 2), Pair.of(Material.REDSTONE_BLOCK, 2),
                Pair.of(Material.QUARTZ_BLOCK, 2),         Pair.of(null, 0),            Pair.of(Material.QUARTZ_BLOCK, 2),
                Pair.of(Material.REDSTONE_BLOCK, 2), Pair.of(Material.QUARTZ_BLOCK, 2), Pair.of(Material.REDSTONE_BLOCK, 2)
        ), device -> device.useTime -= 3, Pair.of("Use Time: ", "-3")),
        FINAL_USE_TIME(USE_TIME, List.of(
                Pair.of(Material.REDSTONE_BLOCK, 4), Pair.of(Material.TOTEM_OF_UNDYING, 1), Pair.of(Material.REDSTONE_BLOCK, 4),
                Pair.of(Material.CRYING_OBSIDIAN, 1),         Pair.of(null, 0),            Pair.of(Material.CRYING_OBSIDIAN, 1),
                Pair.of(Material.REDSTONE_BLOCK, 4), Pair.of(Material.NETHERITE_INGOT, 1), Pair.of(Material.REDSTONE_BLOCK, 4)
        ), device -> device.useTime -= 2, Pair.of("Use Time: ", "-2"), Pair.of("Uninterruptible", "")),
        CONNECTIONS(2, List.of(
                Pair.of(Material.COAL_BLOCK, 4), Pair.of(Material.COPPER_BLOCK, 8), Pair.of(Material.COAL_BLOCK, 4),
                Pair.of(Material.COPPER_BLOCK, 8),         Pair.of(null, 0),            Pair.of(Material.COPPER_BLOCK, 8),
                Pair.of(Material.COAL_BLOCK, 4), Pair.of(Material.COPPER_BLOCK, 8), Pair.of(Material.COAL_BLOCK, 4)
        ), device -> device.connections += 2, Pair.of("Anchors: ", "+2")),
        FINAL_CONNECTIONS(CONNECTIONS, List.of(
                Pair.of(Material.COPPER_BLOCK, 32), Pair.of(Material.BEACON, 1),   Pair.of(Material.COPPER_BLOCK, 32),
                Pair.of(Material.DRAGON_BREATH, 1),         Pair.of(null, 0),          Pair.of(Material.DRAGON_BREATH, 1),
                Pair.of(Material.SCULK_CATALYST, 1), Pair.of(Material.NETHERITE_INGOT, 1), Pair.of(Material.SCULK_CATALYST, 1)
        ), device -> device.connections = -1, Pair.of("Anchors: ", "∞")),
        USES(2, List.of(
                Pair.of(Material.IRON_BLOCK, 8), Pair.of(Material.EMERALD_BLOCK, 4), Pair.of(Material.IRON_BLOCK, 8),
                Pair.of(Material.EMERALD_BLOCK, 4),         Pair.of(null, 0),        Pair.of(Material.EMERALD_BLOCK, 4),
                Pair.of(Material.IRON_BLOCK, 8), Pair.of(Material.EMERALD_BLOCK, 4), Pair.of(Material.IRON_BLOCK, 8)
        ), device -> device.uses += 10, Pair.of("Uses: ", "+10")),
        FINAL_USES(USES, List.of(

        ), device -> device.uses = -1, Pair.of("Uses: ", "∞"));

        public static void initAll() {
            for (UpgradeType value : values()) value.init();
        }

        private final int limit;
        private final List<Pair<Material, Integer>> rawRecipe;
        private TeleportationDeviceRecipe recipe;
        private final Consumer<TeleportationDevice> apply;
        private final UpgradeType prereq;
        private final Pair<String, String>[] lore;

        @SafeVarargs
        UpgradeType(UpgradeType prereq, List<Pair<Material, Integer>> recipe, Consumer<TeleportationDevice> apply, Pair<String, String>... lore) {
            this.limit = 1;
            this.rawRecipe = recipe;
            this.apply = apply;
            this.prereq = prereq;
            this.lore = lore;
        }

        @SafeVarargs
        UpgradeType(int limit, List<Pair<Material, Integer>> recipe, Consumer<TeleportationDevice> apply, Pair<String, String>... lore) {
            this.limit = limit;
            this.rawRecipe = recipe;
            this.apply = apply;
            this.prereq = null;
            this.lore = lore;
        }

        private void init() {
            UpgradeType[] inputUpgrades = prereq == null ? null : new UpgradeType[prereq.limit];
            if (inputUpgrades != null) Arrays.fill(inputUpgrades, prereq);

            ItemStack defaultOutput = new ItemStack(Material.SHIELD);
            defaultOutput.editMeta(meta -> {
                meta.displayName(Component.text(StringUtils.capitaliseAllWords(name().toLowerCase().replace('_', ' '))+" Upgrade")
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                meta.setCustomModelData(MODEL_DATA_BASE);

                List<Component> loreList = new ArrayList<>();
                loreList.add(Component.empty());
                loreList.addAll(Arrays.stream(lore).map(l -> Component.text(l.getFirst())
                        .color(NamedTextColor.GRAY)
                        .append(Component.text(l.getSecond(), NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false)).toList());
                meta.lore(loreList);
            });

            this.recipe = new TeleportationDeviceRecipe(
                    device -> device.isEvolved() == (prereq != null),
                    device -> device.upgrades.stream().filter(upgrade -> upgrade == this).count() < limit &&
                            (prereq == null || device.isEvolved() && device.upgrades.stream()
                                    .filter(upgrade -> upgrade == prereq).count() == prereq.limit),
                    device -> device.withUpgrades(this).asItem(),
                    prereq == null ? BASE.asItem() : BASE.evolved().withUpgrades(inputUpgrades).asItem(),
                    defaultOutput,
                    SMPItems.key("teleportation_%s_upgrade".formatted(name().toLowerCase())),
                    rawRecipe);
        }

        public void apply(TeleportationDevice device) {
            apply.accept(device);
        }
    }

    private static class SelectionPersistentDataType implements PersistentDataType<PersistentDataContainer, Object> {
        @Override
        public @NotNull Class<PersistentDataContainer> getPrimitiveType() {
            return PersistentDataContainer.class;
        }

        @Override
        public @NotNull Class<Object> getComplexType() {
            return Object.class;
        }

        @Override
        public @NotNull PersistentDataContainer toPrimitive(@NotNull Object complex, @NotNull PersistentDataAdapterContext context) {
            PersistentDataContainer pdc = complex instanceof Location loc ?
                    new LocationPersistentDataType().toPrimitive(loc, context) :
                    context.newPersistentDataContainer();
            if (complex instanceof UUID uuid) pdc.set(ID_KEY, UUID_TYPE, uuid);

            return pdc;
        }

        @Override
        public @NotNull Object fromPrimitive(@NotNull PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            if (primitive.has(ID_KEY, UUID_TYPE)) return primitive.get(ID_KEY, UUID_TYPE);

            return new LocationPersistentDataType().fromPrimitive(primitive, context);
        }
    }
    private static class LocationPersistentDataType implements PersistentDataType<PersistentDataContainer, Location> {

        private static final NamespacedKey WORLD = SMPItems.key("world");
        private static final NamespacedKey X = SMPItems.key("x");
        private static final NamespacedKey Y = SMPItems.key("y");
        private static final NamespacedKey Z = SMPItems.key("z");
        private static final NamespacedKey YAW = SMPItems.key("yaw");
        private static final NamespacedKey PITCH = SMPItems.key("pitch");

        @Override
        public @NotNull Class<PersistentDataContainer> getPrimitiveType() {
            return PersistentDataContainer.class;
        }

        @Override
        public @NotNull Class<Location> getComplexType() {
            return Location.class;
        }

        @Override
        public @NotNull PersistentDataContainer toPrimitive(@NotNull Location complex, @NotNull PersistentDataAdapterContext context) {
            PersistentDataContainer pdc = context.newPersistentDataContainer();

            pdc.set(WORLD, PersistentDataType.STRING, complex.getWorld().getUID().toString());
            pdc.set(X, PersistentDataType.DOUBLE, complex.getX());
            pdc.set(Y, PersistentDataType.DOUBLE, complex.getY());
            pdc.set(Z, PersistentDataType.DOUBLE, complex.getZ());
            pdc.set(YAW, PersistentDataType.FLOAT, complex.getYaw());
            pdc.set(PITCH, PersistentDataType.FLOAT, complex.getPitch());

            return pdc;
        }

        @Override
        public @NotNull Location fromPrimitive(@NotNull PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            return new Location(
                    Bukkit.getWorld(UUID.fromString(primitive.get(WORLD, PersistentDataType.STRING))),
                    primitive.get(X, PersistentDataType.DOUBLE),
                    primitive.get(Y, PersistentDataType.DOUBLE),
                    primitive.get(Z, PersistentDataType.DOUBLE),
                    primitive.get(YAW, PersistentDataType.FLOAT),
                    primitive.get(PITCH, PersistentDataType.FLOAT)
            );
        }
    }
    private static class UUIDPersistentDataType implements PersistentDataType<String, UUID> {

        @Override
        public @NotNull Class<String> getPrimitiveType() {
            return String.class;
        }

        @Override
        public @NotNull Class<UUID> getComplexType() {
            return UUID.class;
        }

        @Override
        public @NotNull String toPrimitive(@NotNull UUID complex, @NotNull PersistentDataAdapterContext context) {
            return complex.toString();
        }

        @Override
        public @NotNull UUID fromPrimitive(@NotNull String primitive, @NotNull PersistentDataAdapterContext context) {
            return UUID.fromString(primitive);
        }
    }
    private static class UpgradePersistentDataType implements PersistentDataType<String, UpgradeType> {

        @Override
        public @NotNull Class<String> getPrimitiveType() {
            return String.class;
        }

        @Override
        public @NotNull Class<UpgradeType> getComplexType() {
            return UpgradeType.class;
        }

        @Override
        public @NotNull String toPrimitive(@NotNull UpgradeType complex, @NotNull PersistentDataAdapterContext context) {
            return complex.name();
        }

        @Override
        public @NotNull UpgradeType fromPrimitive(@NotNull String primitive, @NotNull PersistentDataAdapterContext context) {
            return UpgradeType.valueOf(primitive);
        }
    }
}
