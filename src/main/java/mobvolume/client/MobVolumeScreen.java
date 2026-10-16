package mobvolume.client;

import mobvolume.MobGroup;
import mobvolume.MobSounds;
import mobvolume.MobSounds.MobInfo;
import mobvolume.MobSounds.Sound;
import mobvolume.MobVolumeConfig;
import mobvolume.MobVolumeMod;
import mobvolume.SoundClassifier;
import mobvolume.SoundType;
import mobvolume.client.ui.SettingsList;
import mobvolume.client.ui.SettingsScreen;
import mobvolume.compat.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A searchable list of every mob with a volume slider. A mob opens up into one slider per kind of sound, and each
 * kind opens up into one slider per sound, named by the game's own subtitles. Every slider is reset on its own, so
 * putting a mob back to 100% keeps what was set for its kinds of sound and its sounds.
 */
public class MobVolumeScreen extends SettingsScreen {
    /** Mobs (and sounds) heard this recently are marked, and those mobs are listed first. */
    private static final long RECENT_MS = 2 * 60 * 1000;
    /** Your own item and experience pickups would always be on top of "Recently heard". */
    private static final Set<String> OWN_ACTIONS = Set.of("minecraft:item", "minecraft:experience_orb");
    /** How far the mobs of an opened category are moved to the right. */
    private static final int INDENT = 14;
    private static final int TOOLTIP_LINES = 12;
    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    private static final WidgetSprites RESET_SPRITES = new WidgetSprites(
        Identifier.fromNamespaceAndPath(MobVolumeMod.MOD_ID, "reset"),
        Identifier.fromNamespaceAndPath(MobVolumeMod.MOD_ID, "reset_disabled"),
        Identifier.fromNamespaceAndPath(MobVolumeMod.MOD_ID, "reset"));

    /** Opened categories; remembered while the game runs. Closed at first, so the list starts as an overview. */
    private static final Set<MobGroup> OPEN_CATEGORIES = EnumSet.noneOf(MobGroup.class);

    private final MobVolumeConfig config = MobVolumeConfig.get();
    private final Set<String> expandedMobs = new HashSet<>();
    private final Set<String> expandedTypes = new HashSet<>();
    /** Rows are kept while hidden (collapsed or filtered out) so their widgets keep focus and state. */
    private final Map<String, SettingsList.Row> rows = new HashMap<>();
    private List<MobInfo> mobs = List.of();
    private List<MobInfo> recent = List.of();
    private Set<String> recentIds = Set.of();
    /** Mobs with any setting changed, as listed in "Configured" when the list was last built. */
    private Set<String> configuredIds = Set.of();
    private @Nullable EditBox search;

    public MobVolumeScreen(@Nullable Screen parent) {
        super(Component.translatable("mobvolume.screen.title"), parent);
    }

    @Override
    protected void init() {
        mobs = MobSounds.catalog();
        recent = mobs.stream()
            // your own steps would always put the player on top
            .filter(m -> !MobSounds.isGroup(m.id()) && m.type() != Compat.PLAYER && !OWN_ACTIONS.contains(m.id()))
            .filter(m -> isRecent(MobSounds.heardAgo(m.id())))
            .sorted(Comparator.comparingLong(m -> MobSounds.heardAgo(m.id())))
            .toList();
        recentIds = new HashSet<>(recent.stream().map(MobInfo::id).toList());
        super.init();
    }

    private static boolean isRecent(long ago) {
        return ago >= 0 && ago < RECENT_MS;
    }

    @Override
    protected int addHeaderControls(LinearLayout header) {
        search = header.addChild(new EditBox(this.font, 0, 0, SettingsList.ROW_WIDTH, 20,
            Component.translatable("mobvolume.screen.search")));
        search.setHint(Component.translatable("mobvolume.screen.search").setStyle(EditBox.SEARCH_HINT_STYLE));
        search.setResponder(text -> {
            if (list != null) {
                list.rebuild();
                list.setScrollAmount(0);
            }
        });
        return 26;
    }

    @Override
    protected void setInitialFocus() {
        if (search != null) setInitialFocus(search);
    }

    @Override
    protected void buildList(SettingsList list) {
        list.header(Component.translatable("mobvolume.section.general"));
        list.row(list.checkbox(Component.translatable("mobvolume.screen.enabled"), config.enabled, v -> {
            config.enabled = v;
            MobSounds.refreshPlaying();
        }, Component.translatable("mobvolume.screen.enabled.tooltip")));

        Locale locale = MobSounds.gameLocale();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(locale);
        if (query.isEmpty() && !recent.isEmpty()) {
            list.header(Component.translatable("mobvolume.section.recent"));
            recent.forEach(m -> addMob(list, "recent", m, query, locale, false, false));
        }

        // everything that has been changed, at any level, so it is easy to find again
        List<MobInfo> configured = configuredMobs();
        configuredIds = ids(configured);
        if (query.isEmpty() && !configured.isEmpty()) {
            list.header(Component.translatable("mobvolume.section.configured"));
            configured.forEach(m -> addMob(list, "configured", m, query, locale, false, false));
        }

        // one row per category; opened, or opened by itself when the search finds something in it
        list.header(Component.translatable("mobvolume.section.categories"));
        boolean found = false;
        for (MobGroup group : MobGroup.values()) {
            List<MobInfo> members = mobs.stream().filter(m -> m.category() == group).toList();
            List<MobInfo> shown = query.isEmpty() ? members
                : members.stream().filter(m -> m.searchText().contains(query)).toList();
            if (shown.isEmpty()) continue;
            found = true;
            list.add(row("category/" + group.key, () -> new CategoryRow(group, members)));
            if (!query.isEmpty() || OPEN_CATEGORIES.contains(group)) {
                // a little to the right, hanging from the category
                for (int i = 0; i < shown.size(); i++) {
                    addMob(list, "category", shown.get(i), query, locale, true, i < shown.size() - 1);
                }
            }
        }
        if (!found) {
            list.header(Component.translatable("mobvolume.screen.no_results"));
        }
    }

    /**
     * The mob, and when it is opened its kinds of sound and their sounds. A search that only matches some of the
     * mob's sounds (e.g. "groan") shows those sounds right away. A mob can be listed more than once (recently heard,
     * configured, and in its category), so the rows are kept per section.
     *
     * @param inCategory   listed under its category's row: moved a little to the right, with a line to the category
     * @param moreBelow    another mob of the category follows, so that line goes on past this mob's rows
     */
    private void addMob(SettingsList list, String section, MobInfo mob, String query, Locale locale,
                        boolean inCategory, boolean moreBelow) {
        list.add(tree(row(section + "/mob/" + mob.id(), () -> new MobRow(mob)), inCategory, moreBelow));
        boolean nameMatch = query.isEmpty() || mob.name().getString().toLowerCase(locale).contains(query)
            || mob.id().contains(query);
        boolean open = expandedMobs.contains(mob.id());
        mob.byType().forEach((type, sounds) -> {
            String typeKey = mob.id() + "/" + type.key;
            List<Sound> shown = new ArrayList<>();
            for (Sound sound : sounds) {
                boolean match = !nameMatch && sound.name().getString().toLowerCase(locale).contains(query);
                if (match || (open && expandedTypes.contains(typeKey))) shown.add(sound);
            }
            if (!open && shown.isEmpty()) return;
            list.add(tree(row(section + "/type/" + typeKey, () -> new TypeRow(mob, type)), inCategory, moreBelow));
            for (Sound sound : shown) {
                list.add(tree(row(section + "/sound/" + sound.key(), () -> new SoundRow(mob, type, sound)),
                    inCategory, moreBelow));
            }
        });
    }

    private SettingsList.Row row(String key, Supplier<SettingsList.Row> create) {
        return rows.computeIfAbsent(key, k -> create.get());
    }

    private static SettingsList.Row tree(SettingsList.Row row, boolean inCategory, boolean moreBelow) {
        ((TreeRow) row).place(inCategory, moreBelow);
        return row;
    }

    private List<MobInfo> configuredMobs() {
        return mobs.stream().filter(m -> config.changed(m.id(), m.soundKeys(null))).toList();
    }

    private static Set<String> ids(List<MobInfo> list) {
        Set<String> out = new HashSet<>();
        list.forEach(m -> out.add(m.id()));
        return out;
    }

    /**
     * A slider moved: update the sounds that are playing right now, and every row (texts, buttons, and the same
     * mob's slider in the other section).
     */
    private void afterChange() {
        MobSounds.refreshPlaying();
        if (list != null) list.children().forEach(SettingsList.Row::sync);
    }

    /**
     * Something was reset: also move the sliders. When a mob has nothing changed any more (or the other way round),
     * "Configured" is built again. That is not done while a slider is dragged, as the rows would move under the mouse.
     */
    private void afterReset() {
        MobSounds.refreshPlaying();
        if (list == null) return;
        if (!ids(configuredMobs()).equals(configuredIds)) {
            list.rebuild();
        } else {
            list.children().forEach(SettingsList.Row::sync);
        }
    }

    @Override
    protected void save() {
        config.save();
    }

    @Override
    protected Component resetLabel() {
        return Component.translatable("mobvolume.screen.reset_all");
    }

    @Override
    protected Component resetQuestion() {
        return Component.translatable("mobvolume.screen.reset_all.title");
    }

    @Override
    protected Component resetWarning() {
        return Component.translatable("mobvolume.screen.reset_all.message");
    }

    @Override
    protected void reset() {
        config.resetAll();
        MobSounds.refreshPlaying();
    }

    /** Opened in-game with the key: keep singleplayer running, so changes can be heard on the mobs around you. */
    @Override
    public boolean isPauseScreen() {
        return parent != null;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The mob's spawn egg. Item data is only ready once a world has been loaded, so on the title screen
     * (e.g. opened from Mod Menu) there are no icons yet.
     */
    private static ItemStack icon(MobInfo mob) {
        ItemStack cached = ICONS.get(mob.id());
        if (cached != null) return cached;
        Item item = iconItem(mob);
        if (item == null) {
            ICONS.put(mob.id(), ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        if (!item.builtInRegistryHolder().areComponentsBound()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        ICONS.put(mob.id(), stack);
        return stack;
    }

    private static ItemStack categoryIcon(MobGroup group) {
        String key = "category/" + group.key;
        ItemStack cached = ICONS.get(key);
        if (cached != null) return cached;
        Item item = switch (group) {
            case HOSTILE -> Items.ZOMBIE_HEAD;
            case BOSS -> Items.DRAGON_HEAD;
            case ANIMAL -> Items.COW_SPAWN_EGG;
            case WATER -> Items.TROPICAL_FISH_BUCKET;
            case VILLAGER -> Items.EMERALD;
            case GOLEM -> Items.CARVED_PUMPKIN;
            case OTHER -> Items.ARMOR_STAND;
            case OBJECT -> Items.MINECART;
            case SHARED -> Items.NOTE_BLOCK;
        };
        if (!item.builtInRegistryHolder().areComponentsBound()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        ICONS.put(key, stack);
        return stack;
    }

    /** Items for the entries that have neither a spawn egg nor an item of their own name. */
    private static final Map<String, Item> SPECIAL_ICONS = Map.of(
        SoundClassifier.GENERIC, Items.NOTE_BLOCK,
        SoundClassifier.HOSTILE, Items.SKELETON_SKULL,
        SoundClassifier.FISH, Items.COD,
        SoundClassifier.BOAT, Items.OAK_BOAT,
        "minecraft:player", Items.PLAYER_HEAD,
        "minecraft:fishing_bobber", Items.FISHING_ROD,
        "minecraft:eye_of_ender", Items.ENDER_EYE,
        "minecraft:experience_orb", Items.EXPERIENCE_BOTTLE,
        "minecraft:lightning_bolt", Items.TRIDENT,
        "minecraft:shulker_bullet", Items.SHULKER_SHELL);

    private static @Nullable Item iconItem(MobInfo mob) {
        Item special = SPECIAL_ICONS.get(mob.id());
        if (special != null) return special;
        // "minecraft:zombie" -> "minecraft:zombie_spawn_egg", which modded mobs usually follow too
        Identifier egg = Identifier.tryParse(mob.id() + "_spawn_egg");
        Item item = egg == null ? null : BuiltInRegistries.ITEM.getOptional(egg).orElse(null);
        if (item != null) return item;
        // things are often an item of the same name: minecart, arrow, item frame, painting, TNT...
        Identifier same = Identifier.tryParse(mob.id());
        return same == null ? null : BuiltInRegistries.ITEM.getOptional(same).orElse(null);
    }

    /** "Sounds:" and then one grey line per entry, shortened when there are many. */
    private static Component listTooltip(List<String> lines) {
        MutableComponent text = Component.translatable("mobvolume.screen.sounds").withStyle(ChatFormatting.GRAY);
        for (int i = 0; i < Math.min(lines.size(), TOOLTIP_LINES); i++) {
            text.append(Component.literal("\n" + lines.get(i)).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (lines.size() > TOOLTIP_LINES) {
            text.append(Component.literal("\n").append(Component.translatable("mobvolume.screen.sounds.more",
                lines.size() - TOOLTIP_LINES)).withStyle(ChatFormatting.DARK_GRAY));
        }
        return text;
    }

    private static String shortId(Identifier id) {
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    private static Button smallButton(String label, Component tooltip, Button.OnPress onPress) {
        return Button.builder(Component.literal(label), onPress).size(20, 20).tooltip(Tooltip.create(tooltip)).build();
    }

    private static Button resetButton(Button.OnPress onPress) {
        return SpriteIconButton.builder(Component.translatable("mobvolume.screen.reset"), onPress, true)
            .sprite(RESET_SPRITES, 12, 12)
            .size(20, 20)
            .withTootip()
            .build();
    }

    private static void treeLine(GuiGraphicsExtractor g, int lineX, int top, int y, int toX) {
        g.fill(lineX, top, lineX + 1, y + 10, 0x66FFFFFF);
        g.fill(lineX, y + 10, toX, y + 11, 0x66FFFFFF);
    }

    // ---------------------------------------------------------------- rows

    /**
     * A row of a mob (or one of its sounds). Under a category it is moved {@link #INDENT} to the right, and a line
     * runs down from the category's [+] button to each of its mobs.
     */
    private abstract static class TreeRow extends SettingsList.Row {
        private boolean inCategory;
        private boolean moreBelow;

        void place(boolean inCategory, boolean moreBelow) {
            this.inCategory = inCategory;
            this.moreBelow = moreBelow;
        }

        /** Left edge of the row's own content. */
        int left() {
            return getContentX() + (inCategory ? INDENT : 0);
        }

        /** The category's line: down this whole row, or for the mob's own row down to it and across. */
        void categoryLine(GuiGraphicsExtractor g, boolean mobRow) {
            if (!inCategory) return;
            int lineX = getContentX() + 9;
            if (mobRow) {
                if (moreBelow) {
                    g.fill(lineX, getY() - 2, lineX + 1, getY() + height(), 0x66FFFFFF);
                    g.fill(lineX, getContentY() + 10, left() - 2, getContentY() + 11, 0x66FFFFFF);
                } else {
                    treeLine(g, lineX, getY() - 2, getContentY(), left() - 2);
                }
            } else if (moreBelow) {
                g.fill(lineX, getY() - 2, lineX + 1, getY() + height(), 0x66FFFFFF);
            }
        }
    }

    /** [+] [icon] [Hostile mobs (44): 100%] [♪] [reset] - opens up into the mobs of the category. */
    private final class CategoryRow extends SettingsList.Row {
        private final MobGroup group;
        private final List<MobInfo> members;
        private final Button expand;
        private final CategorySlider slider;
        private final Button preview;
        private final Button reset;

        CategoryRow(MobGroup group, List<MobInfo> members) {
            this.group = group;
            this.members = members;
            this.expand = smallButton("", Component.translatable("mobvolume.screen.expand_category"), b -> {
                if (!OPEN_CATEGORIES.remove(group)) OPEN_CATEGORIES.add(group);
                if (list != null) list.rebuild();
            });
            this.slider = new CategorySlider(group, members.size());
            this.slider.setTooltip(Tooltip.create(Component.translatable("mobvolume.screen.category.tooltip")));
            this.preview = smallButton("♪", Component.translatable("mobvolume.screen.preview"),
                b -> members.get(RandomSource.create().nextInt(members.size())).previewMob());
            // the category's own volume only; its mobs keep theirs
            this.reset = resetButton(b -> {
                config.reset(group);
                afterReset();
            });
        }

        @Override
        public void refresh() {
            expand.setMessage(Component.literal(OPEN_CATEGORIES.contains(group) ? "-" : "+"));
            reset.active = config.volume(group) != MobVolumeConfig.DEFAULT;
            slider.refreshMessage();
        }

        @Override
        public void sync() {
            slider.sync();
            refresh();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            int x = getContentX();
            int y = getContentY();
            int right = getContentRight();
            expand.setPosition(x, y);
            expand.extractRenderState(g, mouseX, mouseY, a);
            ItemStack icon = categoryIcon(group);
            if (!icon.isEmpty()) g.item(icon, x + 24, y + 2);
            slider.setPosition(x + 44, y);
            slider.setWidth(right - 44 - (x + 44));
            slider.extractRenderState(g, mouseX, mouseY, a);
            preview.setPosition(right - 42, y);
            preview.extractRenderState(g, mouseX, mouseY, a);
            reset.setPosition(right - 20, y);
            reset.extractRenderState(g, mouseX, mouseY, a);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(expand, slider, preview, reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(expand, slider, preview, reset);
        }
    }

    /** 0% (off) to 200% for every mob of a category; the name is gold so the row reads as a heading. */
    private final class CategorySlider extends AbstractSliderButton {
        private final MobGroup group;
        private final int count;

        CategorySlider(MobGroup group, int count) {
            super(0, 0, 200, 20, Component.empty(), 0);
            this.group = group;
            this.count = count;
            sync();
        }

        private int percent() {
            return (int) Math.round(this.value * MobVolumeConfig.MAX);
        }

        void sync() {
            this.value = config.volume(group) / (double) MobVolumeConfig.MAX;
            updateMessage();
        }

        void refreshMessage() {
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = percent();
            MutableComponent text = Component.translatable("mobvolume.category." + group.key)
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" (" + count + ")").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": ").withStyle(ChatFormatting.WHITE));
            text.append(percent == 0
                ? Component.translatable("mobvolume.screen.off").withStyle(ChatFormatting.RED)
                : Component.translatable("mobvolume.screen.percent", percent).withStyle(ChatFormatting.WHITE));
            setMessage(text);
        }

        @Override
        protected void applyValue() {
            config.setVolume(group, percent());
            afterChange();
        }
    }

    /** [+] [egg] [Zombie: 50%] [♪] [reset] */
    private final class MobRow extends TreeRow {
        private final MobInfo mob;
        private final Button expand;
        private final VolumeSlider slider;
        private final Button preview;
        private final Button reset;

        MobRow(MobInfo mob) {
            this.mob = mob;
            this.expand = smallButton("", Component.translatable("mobvolume.screen.expand"), b -> {
                if (!expandedMobs.remove(mob.id())) expandedMobs.add(mob.id());
                if (list != null) list.rebuild();
            });
            this.slider = new VolumeSlider(mob, null, null);
            this.slider.setTooltip(Tooltip.create(Component.literal(mob.id()).withStyle(ChatFormatting.GRAY)));
            this.preview = smallButton("♪", Component.translatable("mobvolume.screen.preview"), b -> mob.previewMob());
            // the mob's own volume only; its kinds of sound and its sounds keep theirs
            this.reset = resetButton(b -> {
                config.setVolume(mob.id(), MobVolumeConfig.DEFAULT);
                afterReset();
            });
        }

        @Override
        public void refresh() {
            expand.setMessage(Component.literal(expandedMobs.contains(mob.id()) ? "-" : "+"));
            reset.active = config.volume(mob.id()) != MobVolumeConfig.DEFAULT;
            slider.refreshMessage();
        }

        @Override
        public void sync() {
            slider.sync();
            refresh();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            int x = left();
            int y = getContentY();
            int right = getContentRight();
            categoryLine(g, true);
            expand.setPosition(x, y);
            expand.extractRenderState(g, mouseX, mouseY, a);
            ItemStack icon = icon(mob);
            if (!icon.isEmpty()) g.item(icon, x + 24, y + 2);
            slider.setPosition(x + 44, y);
            slider.setWidth(right - 44 - (x + 44));
            slider.extractRenderState(g, mouseX, mouseY, a);
            preview.setPosition(right - 42, y);
            preview.extractRenderState(g, mouseX, mouseY, a);
            reset.setPosition(right - 20, y);
            reset.extractRenderState(g, mouseX, mouseY, a);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(expand, slider, preview, reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(expand, slider, preview, reset);
        }
    }

    /** Under its mob: [+] [Hurt sounds: 50%] [♪] [reset] */
    private final class TypeRow extends TreeRow {
        private final MobInfo mob;
        private final SoundType type;
        private final String key;
        private final Button expand;
        private final VolumeSlider slider;
        private final Button preview;
        private final Button reset;

        TypeRow(MobInfo mob, SoundType type) {
            this.mob = mob;
            this.type = type;
            this.key = mob.id() + "/" + type.key;
            this.expand = smallButton("", Component.translatable("mobvolume.screen.expand_type"), b -> {
                if (!expandedTypes.remove(key)) expandedTypes.add(key);
                if (list != null) list.rebuild();
            });
            this.slider = new VolumeSlider(mob, type, null);
            this.slider.setTooltip(Tooltip.create(listTooltip(
                mob.byType().get(type).stream().map(sound -> sound.name().getString()).toList())));
            this.preview = smallButton("♪", Component.translatable("mobvolume.screen.preview"),
                b -> mob.preview(mob.sounds(type)));
            // this kind of sound only; its sounds keep their own volumes
            this.reset = resetButton(b -> {
                config.setVolume(mob.id(), type, MobVolumeConfig.DEFAULT);
                afterReset();
            });
        }

        @Override
        public void refresh() {
            expand.setMessage(Component.literal(expandedTypes.contains(key) ? "-" : "+"));
            reset.active = config.volume(mob.id(), type) != MobVolumeConfig.DEFAULT;
            slider.refreshMessage();
        }

        @Override
        public void sync() {
            slider.sync();
            refresh();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            int x = left();
            int y = getContentY();
            int right = getContentRight();
            categoryLine(g, false);
            treeLine(g, x + 31, getY() - 2, y, x + 42);
            expand.setPosition(x + 44, y);
            expand.extractRenderState(g, mouseX, mouseY, a);
            slider.setPosition(x + 68, y);
            slider.setWidth(right - 44 - (x + 68));
            slider.extractRenderState(g, mouseX, mouseY, a);
            preview.setPosition(right - 42, y);
            preview.extractRenderState(g, mouseX, mouseY, a);
            reset.setPosition(right - 20, y);
            reset.extractRenderState(g, mouseX, mouseY, a);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(expand, slider, preview, reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(expand, slider, preview, reset);
        }
    }

    /** Under its kind of sound: [Zombie groans: 20%] [♪] [reset] - one sound event. */
    private final class SoundRow extends TreeRow {
        private final Sound sound;
        private final VolumeSlider slider;
        private final Button preview;
        private final Button reset;

        SoundRow(MobInfo mob, SoundType type, Sound sound) {
            this.sound = sound;
            this.slider = new VolumeSlider(mob, type, sound);
            this.slider.setTooltip(Tooltip.create(Component.literal(shortId(sound.id())).withStyle(ChatFormatting.GRAY)));
            this.preview = smallButton("♪", Component.translatable("mobvolume.screen.preview"),
                b -> mob.preview(List.of(sound.id())));
            this.reset = resetButton(b -> {
                config.setSoundVolume(sound.key(), MobVolumeConfig.DEFAULT);
                afterReset();
            });
        }

        @Override
        public void refresh() {
            reset.active = config.soundVolume(sound.key()) != MobVolumeConfig.DEFAULT;
            slider.refreshMessage();
        }

        @Override
        public void sync() {
            slider.sync();
            refresh();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            int x = left();
            int y = getContentY();
            int right = getContentRight();
            categoryLine(g, false);
            g.fill(x + 31, getY() - 2, x + 32, getY() + height(), 0x66FFFFFF);
            treeLine(g, x + 55, getY() - 2, y, x + 78);
            slider.setPosition(x + 80, y);
            slider.setWidth(right - 44 - (x + 80));
            slider.extractRenderState(g, mouseX, mouseY, a);
            preview.setPosition(right - 42, y);
            preview.extractRenderState(g, mouseX, mouseY, a);
            reset.setPosition(right - 20, y);
            reset.extractRenderState(g, mouseX, mouseY, a);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(slider, preview, reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(slider, preview, reset);
        }
    }

    /**
     * 0% (off) to 200%, for a whole mob, one kind of its sounds, or one sound. The name is yellow when this or
     * anything under it has been changed.
     */
    private final class VolumeSlider extends AbstractSliderButton {
        private final MobInfo mob;
        private final @Nullable SoundType type;
        private final @Nullable Sound sound;

        VolumeSlider(MobInfo mob, @Nullable SoundType type, @Nullable Sound sound) {
            super(0, 0, 200, 20, Component.empty(), 0);
            this.mob = mob;
            this.type = type;
            this.sound = sound;
            sync();
        }

        private int saved() {
            if (sound != null) return config.soundVolume(sound.key());
            return type == null ? config.volume(mob.id()) : config.volume(mob.id(), type);
        }

        private int percent() {
            return (int) Math.round(this.value * MobVolumeConfig.MAX);
        }

        void sync() {
            this.value = saved() / (double) MobVolumeConfig.MAX;
            updateMessage();
        }

        void refreshMessage() {
            updateMessage();
        }

        private boolean changed(int percent) {
            if (sound != null) return percent != MobVolumeConfig.DEFAULT;
            if (type != null) return config.changed(mob.id(), type, mob.soundKeys(type));
            return config.changed(mob.id(), mob.soundKeys(null));
        }

        private boolean heardRecently() {
            if (sound != null) return isRecent(MobSounds.heardAgo(List.of(sound.id())));
            if (type != null) return isRecent(MobSounds.heardAgo(mob.sounds(type)));
            return recentIds.contains(mob.id());
        }

        /** What this actually plays at once the levels above it are applied. */
        private int effective(int percent) {
            double f = percent / 100.0 * config.volume(mob.category()) / 100.0;
            if (type != null) f *= config.volume(mob.id()) / 100.0;
            if (sound != null) f *= config.volume(mob.id(), type) / 100.0;
            return (int) Math.round(f * 100);
        }

        @Override
        protected void updateMessage() {
            int percent = percent();
            MutableComponent name = sound != null ? sound.name().copy()
                : type != null ? Component.translatable("mobvolume.type." + type.key)
                : mob.name().copy();
            if (changed(percent)) name.withStyle(ChatFormatting.YELLOW);
            MutableComponent text = Component.empty();
            if (heardRecently()) text.append(Component.literal("♪ ").withStyle(ChatFormatting.AQUA));
            text.append(name).append(": ");
            if (percent == 0) {
                text.append(Component.translatable("mobvolume.screen.off").withStyle(ChatFormatting.RED));
            } else {
                text.append(Component.translatable("mobvolume.screen.percent", percent));
                int effective = effective(percent);
                if (effective != percent) {
                    text.append(Component.literal(" (= ").append(Component.translatable("mobvolume.screen.percent", effective))
                        .append(")").withStyle(ChatFormatting.GRAY));
                }
            }
            setMessage(text);
        }

        @Override
        protected void applyValue() {
            int percent = percent();
            if (sound != null) {
                config.setSoundVolume(sound.key(), percent);
            } else if (type != null) {
                config.setVolume(mob.id(), type, percent);
            } else {
                config.setVolume(mob.id(), percent);
            }
            afterChange();
        }
    }
}
