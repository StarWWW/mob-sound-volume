package mobvolume;

import java.util.Locale;
import java.util.Map;

/**
 * The categories the mob list is split into, each with its own volume. Mostly the game's own spawn category
 * ({@code MobCategory}), so modded mobs land in the right place by themselves, plus a few groups the game does
 * not have: bosses, villagers, golems, and objects (entities that are not alive).
 */
public enum MobGroup {
    HOSTILE,
    BOSS,
    ANIMAL,
    WATER,
    VILLAGER,
    GOLEM,
    OTHER,
    /** Entities that are not alive: boats, minecarts, arrows, item frames, fireworks, lightning... */
    OBJECT,
    /** The sounds many mobs share ("minecraft:generic", "minecraft:hostile", "minecraft:fish"). */
    SHARED;

    /** Name used in the config file and in translation keys. */
    public final String key = name().toLowerCase(Locale.ROOT);

    /** Vanilla mobs that belong somewhere else than their spawn category says. */
    private static final Map<String, MobGroup> SPECIAL = Map.ofEntries(
        Map.entry("minecraft:ender_dragon", BOSS),
        Map.entry("minecraft:wither", BOSS),
        Map.entry("minecraft:warden", BOSS),
        Map.entry("minecraft:elder_guardian", BOSS),
        Map.entry("minecraft:villager", VILLAGER),
        Map.entry("minecraft:wandering_trader", VILLAGER),
        Map.entry("minecraft:iron_golem", GOLEM),
        Map.entry("minecraft:snow_golem", GOLEM),
        Map.entry("minecraft:copper_golem", GOLEM),
        Map.entry("minecraft:allay", GOLEM),
        Map.entry("minecraft:tadpole", WATER),
        Map.entry("minecraft:player", OTHER),
        Map.entry("minecraft:armor_stand", OTHER),
        Map.entry("minecraft:mannequin", OTHER)
    );

    /**
     * @param mob             entity id, e.g. "minecraft:zombie"
     * @param spawnCategory   the game's spawn category name ("monster", "creature", "water_ambient"...), or null
     *                        when there is no such entity (a sound name that matches none)
     * @param living          whether the entity is alive (a mob, an animal, the player) rather than a thing
     */
    public static MobGroup of(String mob, String spawnCategory, boolean living) {
        if (mob.equals(SoundClassifier.BOAT)) return OBJECT;
        if (SoundClassifier.isGroup(mob)) return SHARED;
        MobGroup special = SPECIAL.get(mob);
        if (special != null) return special;
        if (spawnCategory == null || !living) return OBJECT;
        return switch (spawnCategory) {
            case "monster" -> HOSTILE;
            case "creature", "ambient" -> ANIMAL;
            case "water_creature", "water_ambient", "underground_water_creature", "axolotls" -> WATER;
            default -> OTHER;
        };
    }

    public static MobGroup byKey(String key) {
        for (MobGroup group : values()) {
            if (group.key.equals(key)) return group;
        }
        return null;
    }
}
