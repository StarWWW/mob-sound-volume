package mobvolume;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Works out which mob a sound event belongs to from its name.
 * <p>
 * Mob sounds are named {@code <namespace>:entity.<mob>.<what>}, for example {@code minecraft:entity.zombie.ambient}
 * or {@code minecraft:entity.wolf_big.hurt}. On a server the client only receives the sound and a position, never the
 * mob that made it, so the name is the only reliable way to tell. Mods that leave out the "entity." part
 * ({@code mymod:grizzly_bear_idle}) are recognised too.
 * <p>
 * Every {@code entity.} sound gets an entry, so each one can be set: mobs, things that are not alive (boats, minecarts,
 * arrows, item frames...), a few shared groups, and names that match no entity at all (listed under their own name).
 */
public final class SoundClassifier {
    /** Sounds shared by many mobs (or things), shown as their own entries. */
    public static final String GENERIC = "minecraft:generic";
    public static final String HOSTILE = "minecraft:hostile";
    /** {@code entity.fish.swim}: every kind of fish. */
    public static final String FISH = "minecraft:fish";
    /** {@code entity.boat.*}: boats are one entity per kind of wood, but share their sounds. */
    public static final String BOAT = "minecraft:boat";
    private static final Set<String> GROUPS = Set.of(GENERIC, HOSTILE, FISH, BOAT);

    /** Vanilla sound names that don't match their entity's id. */
    private static final Map<String, String> ALIASES = Map.of(
        "minecraft:puffer_fish", "minecraft:pufferfish",
        "minecraft:ghastling", "minecraft:happy_ghast",
        "minecraft:ender_eye", "minecraft:eye_of_ender"
    );

    /** Idle noises that are not literally called "ambient" (a purring cat, a panting wolf, a buzzing bee...). */
    private static final Set<String> AMBIENT_WORDS = Set.of(
        "ambient", "idle", "loop", "purr", "purreow", "pant", "whine", "growl", "imitate", "screech", "say");

    /** What the game knows about entity ids. */
    public interface Entities {
        /** Whether an entity type with this id exists at all. */
        boolean exists(String id);

        /** Whether it is a living thing (a mob, an animal, the player...) rather than an arrow, a boat or a minecart. */
        boolean living(String id);
    }

    public record Match(String mob, SoundType type) {
    }

    private SoundClassifier() {
    }

    /** True for the shared groups, which are not one entity. */
    public static boolean isGroup(String id) {
        return GROUPS.contains(id);
    }

    /** @return the mob (or thing) and sound type, or null when this is not an entity sound */
    public static Match classify(String soundId, Entities entities) {
        int colon = soundId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : soundId.substring(0, colon);
        String path = colon < 0 ? soundId : soundId.substring(colon + 1);
        if (!path.startsWith("entity.")) {
            // vanilla always uses "entity.", so only mods get the looser matching
            return namespace.equals("minecraft") ? null : classifyModded(namespace, path, entities);
        }
        String rest = path.substring("entity.".length());
        int dot = rest.indexOf('.');
        String name = dot < 0 ? rest : rest.substring(0, dot);
        String what = dot < 0 ? "" : rest.substring(dot + 1);
        if (name.isEmpty()) return null;

        String mob = namespace + ":" + name;
        if (GROUPS.contains(mob)) return new Match(mob, type(what));
        mob = ALIASES.getOrDefault(mob, mob);
        String found = findEntity(namespace, mob.substring(namespace.length() + 1), entities);
        // no such entity: still its own entry, under the name the sound uses
        return new Match(found != null ? found : mob, type(what));
    }

    /**
     * Many mods name mob sounds without "entity.": "mymod:grizzly_bear_idle", "mymod:grizzly_bear.hurt",
     * "mymod:mob.grizzly_bear.death". The name must start with one of that mod's living entities, longest first.
     * Only living ones, because without the "entity." part a name like "mymod:crystal_shatter" is a guess.
     */
    private static Match classifyModded(String namespace, String path, Entities entities) {
        if (path.startsWith("mob.")) path = path.substring("mob.".length());
        int dot = path.indexOf('.');
        String first = dot < 0 ? path : path.substring(0, dot);
        String afterDot = dot < 0 ? "" : path.substring(dot + 1);
        String[] words = first.split("_");
        for (int n = words.length; n >= 1; n--) {
            String id = namespace + ":" + String.join("_", Arrays.copyOfRange(words, 0, n));
            if (!entities.exists(id)) continue;
            if (!entities.living(id)) return null;
            String what = String.join("_", Arrays.copyOfRange(words, n, words.length));
            if (!afterDot.isEmpty()) what = what.isEmpty() ? afterDot : what + "." + afterDot;
            return new Match(id, type(what));
        }
        return null;
    }

    /**
     * The exact id if it exists; otherwise drops words from the end ("wolf_big" is a wolf, "cow_moody" a cow),
     * then from the start. An exact match wins even if it is not a mob, so "evoker_fangs" is never taken for an evoker.
     */
    private static String findEntity(String namespace, String name, Entities entities) {
        if (entities.exists(namespace + ":" + name)) return namespace + ":" + name;
        for (int i = name.lastIndexOf('_'); i > 0; i = name.lastIndexOf('_', i - 1)) {
            String id = namespace + ":" + name.substring(0, i);
            if (entities.exists(id)) return id;
        }
        for (int i = name.indexOf('_'); i >= 0; i = name.indexOf('_', i + 1)) {
            String id = namespace + ":" + name.substring(i + 1);
            if (entities.exists(id)) return id;
        }
        return null;
    }

    /** Attacks and their warnings: a creeper's fuse, a skeleton's shot, an evoker preparing a spell... */
    private static final Set<String> ATTACK_WORDS = Set.of(
        "attack", "bite", "shoot", "spit", "sting", "roar", "sonic", "boom", "charge", "crit", "sweep", "knockback",
        "nodamage", "strong", "weak", "cast", "prepare", "throw", "primed", "warn", "warning", "blow", "slam",
        "stun", "stunned", "fangs", "fireball", "ram", "impact", "explode", "burst", "blast");
    /** Moving around other than walking: jumping, swimming, flying, landing... */
    private static final Set<String> MOVE_WORDS = Set.of(
        "jump", "dash", "swim", "land", "fall", "flop", "splash", "squish", "flap", "paddle", "shamble", "spin",
        "roll", "glide", "fly", "riding", "slide", "teleport", "digging", "dig", "emerge");
    private static final Set<String> EAT_WORDS = Set.of("eat", "drink", "burp", "chew");

    /** "ambient", "hurt_drown", "step_wood", "screaming.ambient", "attack.crit"... */
    static SoundType type(String what) {
        String[] words = what.split("[._]");
        for (String w : words) {
            switch (w) {
                case "hurt" -> { return SoundType.HURT; }
                case "death" -> { return SoundType.DEATH; }
                case "step", "gallop" -> { return SoundType.STEP; }
                default -> { }
            }
        }
        for (String w : words) {
            if (ATTACK_WORDS.contains(w)) return SoundType.ATTACK;
        }
        for (String w : words) {
            if (MOVE_WORDS.contains(w)) return SoundType.MOVE;
        }
        for (String w : words) {
            if (EAT_WORDS.contains(w)) return SoundType.EAT;
        }
        for (String w : words) {
            if (AMBIENT_WORDS.contains(w)) return SoundType.AMBIENT;
        }
        return SoundType.OTHER;
    }
}
