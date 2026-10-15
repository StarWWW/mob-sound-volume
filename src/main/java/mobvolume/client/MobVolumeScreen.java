package mobvolume.client;

import mobvolume.MobGroup;
import mobvolume.MobSounds;
import mobvolume.MobSounds.MobInfo;
import mobvolume.MobSounds.SoundGroup;
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
 * kind opens up into one slider per sound, named by the game's own subtitles.
 */
public class MobVolumeScreen extends SettingsScreen {
    /** Mobs (and sounds) heard this recently are marked, and those mobs are listed first. */
    private static final long RECENT_MS = 2 * 60 * 1000;
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
    private @Nullable EditBox search;

    public MobVolumeScreen(@Nullable Screen parent) {
        super(Component.translatable("mobvolume.screen.title"), parent);
    }

    @Override
    protected void init() {
        mobs = MobSounds.catalog();
        recent = mobs.stream()
            // your own steps would always put the player on top
            .filter(m -> !MobSounds.isGroup(m.id()) && m.type() != Compat.PLAYER)
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
            recent.forEach(m -> addMob(list, "recent", m, query, locale));
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
                shown.forEach(m -> addMob(list, "category", m, query, locale));
            }
        }
        if (!found) {
            list.header(Component.translatable("mobvolume.screen.no_results"));
        }
    }

    /**
     * The mob, and when it is opened its kinds of sound and their sounds. A search that only matches some of the
     * mob's sounds (e.g. "groan") shows those sounds right away. A mob can be listed twice (recently heard and in its
     * category), so the rows are kept per section.
     */
    private void addMob(SettingsList list, String section, MobInfo mob, String query, Locale locale) {
        list.add(row(section + "/mob/" + mob.id(), () -> new MobRow(mob)));
        boolean nameMatch = query.isEmpty() || mob.name().getString().toLowerCase(locale).contains(query)
            || mob.id().contains(query);
        boolean open = expandedMobs.contains(mob.id());
        mob.groups().forEach((type, soundGroups) -> {
            String typeKey = mob.id() + "/" + type.key;
            List<SoundGroup> shown = new ArrayList<>();
            for (SoundGroup group : soundGroups) {
                boolean match = !nameMatch && group.name().getString().toLowerCase(locale).contains(query);
                if (match || (open && expandedTypes.contains(typeKey))) shown.add(group);
            }
            if (!open && shown.isEmpty()) return;
            list.add(row(section + "/type/" + typeKey, () -> new TypeRow(mob, type)));
            for (SoundGroup group : shown) {
                list.add(row(section + "/sound/" + typeKey + "/" + group.name().getString(),
                    () -> new SoundRow(mob, type, group)));
            }
        });
    }

    private SettingsList.Row row(String key, Supplier<SettingsList.Row> create) {
        return rows.computeIfAbsent(key, k -> create.get());
    }

    /**
     * A slider moved: update the sounds that are playing right now, and every row (texts, buttons, and the same
     * mob's slider in the other section).
     */
    private void afterChange() {
        MobSounds.refreshPlaying();
        if (list != null) list.children().forEach(SettingsList.Row::sync);
    }

    /** Something was reset: also move the sliders. */
    private void afterReset() {
        MobSounds.refreshPlaying();
        if (list != null) list.children().forEach(SettingsList.Row::sync);
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
            case SHARED -> Items.NOTE_BLOCK;
        };
        if (!item.builtInRegistryHolder().areComponentsBound()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        ICONS.put(key, stack);
        return stack;
    }

    private static @Nullable Item iconItem(MobInfo mob) {
        if (mob.id().equals(SoundClassifier.GENERIC)) return Items.NOTE_BLOCK;
        if (mob.id().equals(SoundClassifier.HOSTILE)) return Items.SKELETON_SKULL;
        if (mob.type() == Compat.PLAYER) return Items.PLAYER_HEAD;
        if (mob.type() == Compat.ARMOR_STAND) return Items.ARMOR_STAND;
        // "minecraft:zombie" -> "minecraft:zombie_spawn_egg", which modded mobs usually follow too
        Identifier egg = Identifier.tryParse(mob.id() + "_spawn_egg");
        return egg == null ? null : BuiltInRegistries.ITEM.getOptional(egg).orElse(null);
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
            this.reset = resetButton(b -> {
                config.reset(group);
                members.forEach(m -> config.reset(m.id(), m.soundKeys(null)));
                afterReset();
            });
        }

        private boolean changed() {
            return config.volume(group) != MobVolumeConfig.DEFAULT
                || members.stream().anyMatch(m -> config.changed(m.id(), m.soundKeys(null)));
        }

        @Override
        public void refresh() {
            expand.setMessage(Component.literal(OPEN_CATEGORIES.contains(group) ? "-" : "+"));
            reset.active = changed();
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
    private final class MobRow extends SettingsList.Row {
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
            this.reset = resetButton(b -> {
                config.reset(mob.id(), mob.soundKeys(null));
                afterReset();
            });
        }

        @Override
        public void refresh() {
            expand.setMessage(Component.literal(expandedMobs.contains(mob.id()) ? "-" : "+"));
            reset.active = config.changed(mob.id(), mob.soundKeys(null));
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
    private final class TypeRow extends SettingsList.Row {
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
                mob.groups().get(type).stream().map(gr -> gr.name().getString()).toList())));
            this.preview = smallButton("♪", Component.translatable("mobvolume.screen.preview"),
                b -> mob.preview(mob.sounds(type)));
            this.reset = resetButton(b -> {
                config.reset(mob.id(), type, mob.soundKeys(type));
                afterReset();
            });
        }

        @Override
        public void refresh() {
            expand.setMessage(Component.literal(expandedTypes.contains(key) ? "-" : "+"));
            reset.active = config.changed(mob.id(), type, mob.soundKeys(type));
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

    /** Under its kind of sound: [Zombie groans: 20%] [♪] [reset] */
    private final class SoundRow extends SettingsList.Row {
        private final SoundGroup group;
        private final VolumeSlider slider;
        private final Button preview;
        private final Button reset;

        SoundRow(MobInfo mob, SoundType type, SoundGroup group) {
            this.group = group;
            this.slider = new VolumeSlider(mob, type, group);
            this.slider.setTooltip(Tooltip.create(listTooltip(group.ids().stream().map(MobVolumeScreen::shortId).toList())));
            this.preview = smallButton("♪", Component.translatable("mobvolume.screen.preview"), b -> mob.preview(group.ids()));
            this.reset = resetButton(b -> {
                group.keys().forEach(k -> config.setSoundVolume(k, MobVolumeConfig.DEFAULT));
                afterReset();
            });
        }

        @Override
        public void refresh() {
            reset.active = config.anyChanged(group.keys());
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

    /** 0% (off) to 200%, for a whole mob, one kind of its sounds, or one sound. */
    private final class VolumeSlider extends AbstractSliderButton {
        private final MobInfo mob;
        private final @Nullable SoundType type;
        private final @Nullable SoundGroup group;

        VolumeSlider(MobInfo mob, @Nullable SoundType type, @Nullable SoundGroup group) {
            super(0, 0, 200, 20, Component.empty(), 0);
            this.mob = mob;
            this.type = type;
            this.group = group;
            sync();
        }

        private int saved() {
            if (group != null) return config.soundVolume(group.keys().get(0));
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
            if (group != null) return percent != MobVolumeConfig.DEFAULT;
            if (type != null) return config.changed(mob.id(), type, mob.soundKeys(type));
            return config.changed(mob.id(), mob.soundKeys(null));
        }

        private boolean heardRecently() {
            if (group != null) return isRecent(MobSounds.heardAgo(group.ids()));
            if (type != null) return isRecent(MobSounds.heardAgo(mob.sounds(type)));
            return recentIds.contains(mob.id());
        }

        /** What this actually plays at once the levels above it are applied. */
        private int effective(int percent) {
            double f = percent / 100.0 * config.volume(mob.category()) / 100.0;
            if (type != null) f *= config.volume(mob.id()) / 100.0;
            if (group != null) f *= config.volume(mob.id(), type) / 100.0;
            return (int) Math.round(f * 100);
        }

        @Override
        protected void updateMessage() {
            int percent = percent();
            MutableComponent name = group != null ? group.name().copy()
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
            if (group != null) {
                group.keys().forEach(k -> config.setSoundVolume(k, percent));
            } else if (type != null) {
                config.setVolume(mob.id(), type, percent);
            } else {
                config.setVolume(mob.id(), percent);
            }
            afterChange();
        }
    }
}
