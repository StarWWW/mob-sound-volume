package mobvolume;

import java.util.Locale;

/** The kinds of sound a mob makes, each with its own volume. */
public enum SoundType {
    AMBIENT,
    HURT,
    DEATH,
    STEP,
    MOVE,
    ATTACK,
    EAT,
    OTHER;

    /** Name used in the config file and in translation keys. */
    public final String key = name().toLowerCase(Locale.ROOT);

    public static SoundType byKey(String key) {
        for (SoundType type : values()) {
            if (type.key.equals(key)) return type;
        }
        return null;
    }
}
