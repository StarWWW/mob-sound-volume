package mobvolume;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Settings, stored at config/mobvolume.properties.
 * Volumes are percentages, 0 (silent) to {@link #MAX} (twice as loud), at four levels: a category of mobs, the
 * whole mob, one kind of sound of that mob, and one single sound. A sound plays at category x mob x type x sound,
 * so a zombie at 50% with its hurt sounds at 50% is hurt at 25%. Anything not in the file is at 100%.
 * Each level is set and reset on its own: putting the zombie back to 100% keeps its hurt sounds at 50%.
 */
public final class MobVolumeConfig {
    public static final int MAX = 200;
    public static final int DEFAULT = 100;

    public boolean enabled = true;

    private final Map<MobGroup, Integer> groups = new EnumMap<>(MobGroup.class);
    private final Map<String, Integer> mobs = new HashMap<>();
    private final Map<String, EnumMap<SoundType, Integer>> types = new HashMap<>();
    /** Keyed by sound event id, e.g. "minecraft:entity.zombie.ambient". */
    private final Map<String, Integer> sounds = new HashMap<>();

    private static MobVolumeConfig instance;

    public static MobVolumeConfig get() {
        if (instance == null) {
            instance = new MobVolumeConfig();
            instance.load();
        }
        return instance;
    }

    public int volume(MobGroup group) {
        return groups.getOrDefault(group, DEFAULT);
    }

    public int volume(String mob) {
        return mobs.getOrDefault(mob, DEFAULT);
    }

    public int volume(String mob, SoundType type) {
        EnumMap<SoundType, Integer> map = types.get(mob);
        return map == null ? DEFAULT : map.getOrDefault(type, DEFAULT);
    }

    public int soundVolume(String sound) {
        return sounds.getOrDefault(sound, DEFAULT);
    }

    public void setVolume(MobGroup group, int percent) {
        percent = clamp(percent);
        if (percent == DEFAULT) {
            groups.remove(group);
        } else {
            groups.put(group, percent);
        }
    }

    public void setVolume(String mob, int percent) {
        put(mobs, mob, percent);
    }

    public void setVolume(String mob, SoundType type, int percent) {
        percent = clamp(percent);
        if (percent == DEFAULT) {
            EnumMap<SoundType, Integer> map = types.get(mob);
            if (map != null) {
                map.remove(type);
                if (map.isEmpty()) types.remove(mob);
            }
        } else {
            types.computeIfAbsent(mob, m -> new EnumMap<>(SoundType.class)).put(type, percent);
        }
    }

    public void setSoundVolume(String sound, int percent) {
        put(sounds, sound, percent);
    }

    private static void put(Map<String, Integer> map, String key, int percent) {
        percent = clamp(percent);
        if (percent == DEFAULT) {
            map.remove(key);
        } else {
            map.put(key, percent);
        }
    }

    /** Volume multiplier for one sound of this mob: 1 = unchanged, 0 = silent, 2 = twice as loud. */
    public float factor(MobGroup group, String mob, SoundType type, String sound) {
        if (!enabled) return 1.0f;
        return volume(group) / (float) DEFAULT * volume(mob) / DEFAULT * volume(mob, type) / DEFAULT
            * soundVolume(sound) / DEFAULT;
    }

    /** True if anything about this mob (given all of its sounds) has been changed. */
    public boolean changed(String mob, Collection<String> mobSounds) {
        return mobs.containsKey(mob) || types.containsKey(mob) || anyChanged(mobSounds);
    }

    /** True if this kind of sound, or one of its sounds, has been changed. */
    public boolean changed(String mob, SoundType type, Collection<String> typeSounds) {
        return volume(mob, type) != DEFAULT || anyChanged(typeSounds);
    }

    public boolean anyChanged(Collection<String> soundIds) {
        for (String s : soundIds) {
            if (sounds.containsKey(s)) return true;
        }
        return false;
    }

    /** Back to 100% for the category only (its mobs keep their own settings). */
    public void reset(MobGroup group) {
        groups.remove(group);
    }

    public void resetAll() {
        groups.clear();
        mobs.clear();
        types.clear();
        sounds.clear();
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(MAX, percent));
    }

    /** Only for tests: config folder to use instead of the game's. */
    static Path testConfigDir;

    private static Path file() {
        Path dir = testConfigDir != null ? testConfigDir : FabricLoader.getInstance().getConfigDir();
        return dir.resolve("mobvolume.properties");
    }

    public void load() {
        Path path = file();
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(reader);
        } catch (IOException e) {
            MobVolumeMod.LOGGER.warn("Could not read {}", path, e);
            return;
        }
        enabled = Boolean.parseBoolean(p.getProperty("enabled", "true").trim());
        for (String key : p.stringPropertyNames()) {
            int percent;
            try {
                percent = Integer.parseInt(p.getProperty(key).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (key.startsWith("category.")) {
                MobGroup group = MobGroup.byKey(key.substring("category.".length()));
                if (group != null) setVolume(group, percent);
            } else if (key.startsWith("sound.")) {
                setSoundVolume(key.substring("sound.".length()), percent);
            } else if (key.startsWith("volume.")) {
                // volume.<mob> or volume.<mob>/<type>
                String mob = key.substring("volume.".length());
                int slash = mob.lastIndexOf('/');
                SoundType type = slash < 0 ? null : SoundType.byKey(mob.substring(slash + 1));
                if (type != null) {
                    setVolume(mob.substring(0, slash), type, percent);
                } else {
                    setVolume(mob, percent);
                }
            }
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        groups.forEach((group, v) -> p.setProperty("category." + group.key, Integer.toString(v)));
        mobs.forEach((mob, v) -> p.setProperty("volume." + mob, Integer.toString(v)));
        types.forEach((mob, map) -> map.forEach((type, v) ->
            p.setProperty("volume." + mob + "/" + type.key, Integer.toString(v))));
        sounds.forEach((sound, v) -> p.setProperty("sound." + sound, Integer.toString(v)));
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                p.store(writer, "Mob Sound Volume (percent, 0-" + MAX + ", 100 = normal)");
            }
        } catch (IOException e) {
            MobVolumeMod.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
