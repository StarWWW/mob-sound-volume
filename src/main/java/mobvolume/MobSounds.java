package mobvolume;

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
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
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

    public static SoundClassifier.@Nullable Match match(Identifier sound) {
        return MATCHES.computeIfAbsent(sound,
            s -> Optional.ofNullable(SoundClassifier.classify(s.toString(), ENTITIES))).orElse(null);
    }

    /** Volume multiplier for a sound: 1 for anything that is not a mob sound. */
    public static float factor(SoundInstance instance) {
        try {
            Identifier id = instance.getIdentifier();
            SoundClassifier.Match m = match(id);
            return m == null ? 1.0f : MobVolumeConfig.get().factor(m.mob(), m.type(), id.toString());
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
            return !(instance instanceof TickableSoundInstance)
                && MobVolumeConfig.get().factor(m.mob(), m.type(), id.toString()) <= 0;
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
        return mob.equals(SoundClassifier.GENERIC) || mob.equals(SoundClassifier.HOSTILE);
    }

    /**
     * One sound as the player knows it: every sound event with the same subtitle, e.g. the barks of all seven wolf
     * variants are one "Wolf barks". Named by the game's own (translated) subtitle.
     */
    public record SoundGroup(Component name, List<Identifier> ids) {
        public List<String> keys() {
            return ids.stream().map(Identifier::toString).toList();
        }
    }

    /** A mob (or shared group) and all of its sounds, by type. */
    public record MobInfo(String id, Component name, @Nullable EntityType<?> type,
                          Map<SoundType, List<SoundGroup>> groups, String searchText) {
        /** Sound event ids of one type, or of the whole mob when the type is null. */
        public List<Identifier> sounds(@Nullable SoundType soundType) {
            List<Identifier> out = new ArrayList<>();
            groups.forEach((t, list) -> {
                if (soundType == null || t == soundType) list.forEach(g -> out.addAll(g.ids()));
            });
            return out;
        }

        public List<String> soundKeys(@Nullable SoundType soundType) {
            return sounds(soundType).stream().map(Identifier::toString).toList();
        }

        public SoundSource source() {
            if (id.equals(SoundClassifier.HOSTILE)) return SoundSource.HOSTILE;
            if (type == EntityType.PLAYER) return SoundSource.PLAYERS;
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

    /** Every mob that has at least one sound, A-Z in the game's language, followed by the shared groups. */
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
            Component name = type != null
                ? type.getDescription()
                : Component.translatable("mobvolume.group." + id.substring(id.indexOf(':') + 1));
            StringBuilder search = new StringBuilder(name.getString()).append(' ').append(id);
            Map<SoundType, List<SoundGroup>> groups = new EnumMap<>(SoundType.class);
            sounds.forEach((soundType, ids) -> {
                // the same subtitle text is the same sound for the player
                Map<String, SoundGroup> byName = new HashMap<>();
                for (Identifier sound : ids) {
                    Component label = soundName(soundManager, sound);
                    byName.computeIfAbsent(label.getString(), k -> new SoundGroup(label, new ArrayList<>())).ids().add(sound);
                }
                List<SoundGroup> list = new ArrayList<>(byName.values());
                list.forEach(g -> g.ids().sort(Comparator.comparing(Identifier::toString)));
                list.sort(Comparator.comparing(g -> g.name().getString(), collator));
                list.forEach(g -> search.append(' ').append(g.name().getString()));
                groups.put(soundType, List.copyOf(list));
            });
            mobs.add(new MobInfo(id, name, type, groups, search.toString().toLowerCase(locale)));
        });
        mobs.sort(Comparator.comparing((MobInfo m) -> isGroup(m.id()))
            .thenComparing(m -> m.name().getString(), collator));
        return mobs;
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
