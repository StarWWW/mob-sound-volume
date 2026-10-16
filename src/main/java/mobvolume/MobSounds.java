package mobvolume;

import mobvolume.compat.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.jspecify.annotations.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Connects the game's sounds to the config: which mob a sound is from and how loud it should be. */
public final class MobSounds {
    private static final Map<Identifier, Optional<SoundClassifier.Match>> MATCHES = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_HEARD = new ConcurrentHashMap<>();
    private static final Map<Identifier, Long> LAST_HEARD_SOUND = new ConcurrentHashMap<>();
    private static final Map<String, MobGroup> CATEGORIES = new ConcurrentHashMap<>();

    private static final SoundClassifier.Entities ENTITIES = new SoundClassifier.Entities() {
        @Override
        public boolean exists(String id) {
            return entityType(id) != null;
        }

        @Override
        public boolean living(String id) {
            EntityType<?> type = entityType(id);
            // every living entity (mob, animal, player...) has attributes; arrows, boats, minecarts don't
            return type != null && DefaultAttributes.hasSupplier(type);
        }
    };

    private MobSounds() {
    }

    public static @Nullable EntityType<?> entityType(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(key)) return null;
        return BuiltInRegistries.ENTITY_TYPE.getValue(key);
    }

    /** The category a mob is listed under (and whose volume applies to it). */
    public static MobGroup category(String mob) {
        return CATEGORIES.computeIfAbsent(mob, id -> {
            EntityType<?> type = isGroup(id) ? null : entityType(id);
            return MobGroup.of(id, type == null ? null : type.getCategory().getName(), ENTITIES.living(id));
        });
    }

    private static float factor(SoundClassifier.Match m, Identifier sound) {
        return MobVolumeConfig.get().factor(category(m.mob()), m.mob(), m.type(), sound.toString());
    }

    public static SoundClassifier.@Nullable Match match(Identifier sound) {
        return MATCHES.computeIfAbsent(sound,
            s -> Optional.ofNullable(SoundClassifier.classify(s.toString(), ENTITIES))).orElse(null);
    }

    /** Volume multiplier for a sound: 1 for anything that is not a mob sound. */
    public static float factor(SoundInstance instance) {
        try {
            Identifier id = instance.getIdentifier();
            SoundClassifier.Match m = match(id);
            return m == null ? 1.0f : factor(m, id);
        } catch (Throwable t) {
            MobVolumeMod.logOnce(t);
            return 1.0f;
        }
    }

    /**
     * Called for every sound the game is about to start.
     * @return true if it should not be started at all (a muted mob), so it doesn't show a subtitle either
     */
    public static boolean onPlay(SoundInstance instance) {
        try {
            Identifier id = instance.getIdentifier();
            SoundClassifier.Match m = match(id);
            if (m == null) return false;
            if (!(instance instanceof Preview)) {
                long now = System.currentTimeMillis();
                LAST_HEARD.put(m.mob(), now);
                LAST_HEARD_SOUND.put(id, now);
            }
            // looping sounds (a bee's buzz) are started silently instead, so they come back when turned up again
            return !(instance instanceof TickableSoundInstance) && factor(m, id) <= 0;
        } catch (Throwable t) {
            MobVolumeMod.logOnce(t);
            return false;
        }
    }

    /** Milliseconds since this mob was last heard, or -1 if it wasn't heard since the game started. */
    public static long heardAgo(String mob) {
        Long time = LAST_HEARD.get(mob);
        return time == null ? -1 : System.currentTimeMillis() - time;
    }

    /** Milliseconds since any of these sounds was last heard, or -1. */
    public static long heardAgo(Collection<Identifier> sounds) {
        long latest = -1;
        for (Identifier sound : sounds) {
            Long time = LAST_HEARD_SOUND.get(sound);
            if (time != null) latest = Math.max(latest, time);
        }
        return latest < 0 ? -1 : System.currentTimeMillis() - latest;
    }

    /** Applies changed settings to sounds that are already playing. */
    public static void refreshPlaying() {
        Minecraft.getInstance().getSoundManager().refreshCategoryVolume(SoundSource.MASTER);
    }

    public static boolean isGroup(String mob) {
        return SoundClassifier.isGroup(mob);
    }

    /**
     * One sound event, with its own volume. Named by the game's own (translated) subtitle; when several sounds of a
     * mob share a subtitle (the seven wolf variants all "bark"), the sound's name is added so each can be told apart.
     */
    public record Sound(Component name, Identifier id) {
        public String key() {
            return id.toString();
        }
    }

    /** A mob (shared group, or thing), the category it is listed under, and every one of its sounds, by type. */
    public record MobInfo(String id, Component name, @Nullable EntityType<?> type, MobGroup category,
                          Map<SoundType, List<Sound>> byType, String searchText) {
        /** Sound event ids of one type, or of the whole mob when the type is null. */
        public List<Identifier> sounds(@Nullable SoundType soundType) {
            List<Identifier> out = new ArrayList<>();
            byType.forEach((t, list) -> {
                if (soundType == null || t == soundType) list.forEach(sound -> out.add(sound.id()));
            });
            return out;
        }

        public List<String> soundKeys(@Nullable SoundType soundType) {
            return sounds(soundType).stream().map(Identifier::toString).toList();
        }

        public SoundSource source() {
            if (id.equals(SoundClassifier.HOSTILE)) return SoundSource.HOSTILE;
            if (type == Compat.PLAYER) return SoundSource.PLAYERS;
            return type != null && type.getCategory() == MobCategory.MONSTER ? SoundSource.HOSTILE : SoundSource.NEUTRAL;
        }

        /** Plays one of these sounds so the volume can be heard. */
        public void preview(List<Identifier> candidates) {
            if (candidates.isEmpty()) return;
            Identifier sound = candidates.get(RandomSource.create().nextInt(candidates.size()));
            Minecraft mc = Minecraft.getInstance();
            if (lastPreview != null) mc.getSoundManager().stop(lastPreview);
            lastPreview = new Preview(sound, source());
            mc.getSoundManager().play(lastPreview);
        }

        /** For the whole mob: preferably one of its idle sounds. */
        public void previewMob() {
            List<Identifier> idle = sounds(SoundType.AMBIENT);
            preview(idle.isEmpty() ? sounds(null) : idle);
        }
    }

    private static @Nullable Preview lastPreview;

    /** Every mob (or thing) that has at least one sound, by category, A-Z in the game's language within each. */
    public static List<MobInfo> catalog() {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        Map<String, EnumMap<SoundType, List<Identifier>>> byMob = new HashMap<>();
        for (Identifier sound : soundManager.getAvailableSounds()) {
            SoundClassifier.Match m = match(sound);
            if (m == null) continue;
            byMob.computeIfAbsent(m.mob(), k -> new EnumMap<>(SoundType.class))
                .computeIfAbsent(m.type(), k -> new ArrayList<>())
                .add(sound);
        }
        Locale locale = gameLocale();
        Collator collator = Collator.getInstance(locale);
        List<MobInfo> mobs = new ArrayList<>();
        byMob.forEach((id, sounds) -> {
            EntityType<?> type = isGroup(id) ? null : entityType(id);
            Component name = displayName(id, type);
            StringBuilder search = new StringBuilder(name.getString()).append(' ').append(id);
            Map<SoundType, List<Sound>> byType = new EnumMap<>(SoundType.class);
            sounds.forEach((soundType, ids) -> {
                Map<String, List<Identifier>> bySubtitle = new HashMap<>();
                for (Identifier sound : ids) {
                    bySubtitle.computeIfAbsent(soundName(soundManager, sound).getString(), k -> new ArrayList<>()).add(sound);
                }
                Map<Identifier, String> variants = new HashMap<>();
                bySubtitle.values().forEach(same -> {
                    if (same.size() > 1) variants.putAll(variants(same));
                });
                List<Sound> list = new ArrayList<>();
                for (Identifier sound : ids) {
                    Component label = soundName(soundManager, sound);
                    String variant = variants.getOrDefault(sound, "");
                    if (!variant.isEmpty()) {
                        // e.g. "Wolf barks (big)" next to the plain "Wolf barks"
                        label = label.copy().append(Component.literal(" (" + variant + ")").withStyle(ChatFormatting.GRAY));
                    }
                    list.add(new Sound(label, sound));
                }
                list.sort(Comparator.comparing((Sound sound) -> sound.name().getString(), collator)
                    .thenComparing(sound -> sound.id().toString()));
                list.forEach(sound -> search.append(' ').append(sound.name().getString()));
                byType.put(soundType, List.copyOf(list));
            });
            mobs.add(new MobInfo(id, name, type, category(id), byType, search.toString().toLowerCase(locale)));
        });
        mobs.sort(Comparator.comparing(MobInfo::category).thenComparing(m -> m.name().getString(), collator));
        return mobs;
    }

    /**
     * The entity's own name; for a shared group our own name ("Generic", "Fish"); for a sound name that matches no
     * entity, the game's translation if there is one, otherwise the name made readable ("magic_missile" ->
     * "Magic missile", with the mod's id added).
     */
    private static Component displayName(String id, @Nullable EntityType<?> type) {
        if (type != null) return type.getDescription();
        int colon = id.indexOf(':');
        String namespace = id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (isGroup(id)) return Component.translatable("mobvolume.group." + path);
        String readable = Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
        if (!namespace.equals("minecraft")) readable += " (" + namespace + ")";
        return Component.translatableWithFallback("entity." + namespace + "." + path, readable);
    }

    /**
     * What tells sounds with the same subtitle apart: the words of their names that they don't all share.
     * Among the wolf barks "entity.wolf_big.ambient" is "big" and the plain "entity.wolf.ambient" is "". If that would
     * leave two of them the same, each gets its whole name instead.
     */
    static Map<Identifier, String> variants(List<Identifier> same) {
        List<String[]> all = new ArrayList<>();
        for (Identifier id : same) all.add(words(id));
        int shortest = Integer.MAX_VALUE;
        for (String[] w : all) shortest = Math.min(shortest, w.length);
        int prefix = 0;
        while (prefix < shortest && sameWord(all, prefix, false)) prefix++;
        int suffix = 0;
        while (suffix < shortest - prefix && sameWord(all, suffix, true)) suffix++;
        Map<Identifier, String> out = new HashMap<>();
        for (int i = 0; i < same.size(); i++) {
            String[] w = all.get(i);
            out.put(same.get(i), String.join(" ", Arrays.copyOfRange(w, prefix, w.length - suffix)));
        }
        if (new HashSet<>(out.values()).size() < out.size()) {
            for (int i = 0; i < same.size(); i++) out.put(same.get(i), String.join(" ", all.get(i)));
        }
        return out;
    }

    /** Whether every name has the same word at this position, counted from the start or from the end. */
    private static boolean sameWord(List<String[]> all, int index, boolean fromEnd) {
        String first = null;
        for (String[] w : all) {
            String word = fromEnd ? w[w.length - 1 - index] : w[index];
            if (first == null) {
                first = word;
            } else if (!first.equals(word)) {
                return false;
            }
        }
        return true;
    }

    /** "minecraft:entity.wolf_big.ambient" -> [wolf, big, ambient] */
    private static String[] words(Identifier sound) {
        String path = sound.getPath();
        if (path.startsWith("entity.")) path = path.substring("entity.".length());
        return path.split("[._]");
    }

    /** The sound's subtitle ("Zombie groans"), or for the few without one, its name ("loop"). */
    private static Component soundName(SoundManager soundManager, Identifier sound) {
        WeighedSoundEvents event = soundManager.getSoundEvent(sound);
        Component subtitle = event == null ? null : event.getSubtitle();
        if (subtitle != null) return subtitle;
        String path = sound.getPath();
        String what = path.startsWith("entity.") ? path.substring(path.indexOf('.', "entity.".length()) + 1) : path;
        return Component.literal(what.replace('_', ' ').replace('.', ' '));
    }

    public static Locale gameLocale() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected(); // e.g. "tr_tr"
        return Locale.forLanguageTag(code.replace('_', '-'));
    }

    /** The sound played by the settings screen's preview button; not counted as "recently heard". */
    private static final class Preview extends SimpleSoundInstance {
        Preview(Identifier sound, SoundSource source) {
            super(sound, source, 1.0f, 1.0f, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        }
    }
}
