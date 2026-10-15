package mobvolume;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SoundClassifierTest {
    private static final Set<String> LIVING = Set.of(
        "minecraft:zombie", "minecraft:zombie_villager", "minecraft:cow", "minecraft:wolf", "minecraft:pufferfish",
        "minecraft:happy_ghast", "minecraft:evoker", "minecraft:player", "minecraft:goat", "minecraft:bee",
        "minecraft:parrot", "minecraft:villager", "minecraft:horse", "mymod:dragonling", "mymod:grizzly_bear");
    private static final Set<String> NOT_LIVING = Set.of(
        "minecraft:evoker_fangs", "minecraft:arrow", "minecraft:glow_item_frame", "minecraft:item_frame",
        "minecraft:item", "minecraft:minecart", "mymod:crystal");

    private static final SoundClassifier.Entities ENTITIES = new SoundClassifier.Entities() {
        @Override
        public boolean exists(String id) {
            return LIVING.contains(id) || NOT_LIVING.contains(id);
        }

        @Override
        public boolean living(String id) {
            return LIVING.contains(id);
        }
    };

    private static void assertMatch(String sound, String mob, SoundType type) {
        assertEquals(new SoundClassifier.Match(mob, type), SoundClassifier.classify(sound, ENTITIES), sound);
    }

    @Test
    void basicTypes() {
        assertMatch("minecraft:entity.zombie.ambient", "minecraft:zombie", SoundType.AMBIENT);
        assertMatch("minecraft:entity.zombie.hurt", "minecraft:zombie", SoundType.HURT);
        assertMatch("minecraft:entity.zombie.death", "minecraft:zombie", SoundType.DEATH);
        assertMatch("minecraft:entity.zombie.step", "minecraft:zombie", SoundType.STEP);
        assertMatch("minecraft:entity.zombie.attack_wooden_door", "minecraft:zombie", SoundType.ATTACK);
        assertMatch("minecraft:entity.villager.work_armorer", "minecraft:villager", SoundType.OTHER);
    }

    @Test
    void attackEatAndMovement() {
        assertMatch("minecraft:entity.player.attack.crit", "minecraft:player", SoundType.ATTACK);
        assertMatch("minecraft:entity.evoker.prepare_summon", "minecraft:evoker", SoundType.ATTACK);
        assertMatch("minecraft:entity.goat.ram_impact", "minecraft:goat", SoundType.ATTACK);
        assertMatch("minecraft:entity.goat.screaming.ram_impact", "minecraft:goat", SoundType.ATTACK);
        assertMatch("minecraft:entity.horse.eat", "minecraft:horse", SoundType.EAT);
        assertMatch("minecraft:entity.player.burp", "minecraft:player", SoundType.EAT);
        assertMatch("minecraft:entity.horse.jump", "minecraft:horse", SoundType.MOVE);
        assertMatch("minecraft:entity.player.splash.high_speed", "minecraft:player", SoundType.MOVE);
        assertMatch("minecraft:entity.goat.long_jump", "minecraft:goat", SoundType.MOVE);
        // hurt, death and steps win over everything else
        assertMatch("minecraft:entity.player.hurt_drown", "minecraft:player", SoundType.HURT);
    }

    @Test
    void typeWordsAnywhere() {
        assertMatch("minecraft:entity.player.hurt_drown", "minecraft:player", SoundType.HURT);
        assertMatch("minecraft:entity.goat.screaming.ambient", "minecraft:goat", SoundType.AMBIENT);
        assertMatch("minecraft:entity.goat.screaming.hurt", "minecraft:goat", SoundType.HURT);
        assertMatch("minecraft:entity.horse.step_wood", "minecraft:horse", SoundType.STEP);
        assertMatch("minecraft:entity.horse.gallop", "minecraft:horse", SoundType.STEP);
    }

    @Test
    void idleNoisesNotCalledAmbient() {
        assertMatch("minecraft:entity.bee.loop", "minecraft:bee", SoundType.AMBIENT);
        assertMatch("minecraft:entity.wolf.pant", "minecraft:wolf", SoundType.AMBIENT);
        assertMatch("minecraft:entity.parrot.imitate.creeper", "minecraft:parrot", SoundType.AMBIENT);
    }

    @Test
    void exactIdBeatsShorterOne() {
        assertMatch("minecraft:entity.zombie_villager.ambient", "minecraft:zombie_villager", SoundType.AMBIENT);
        // evoker fangs are not the evoker, and not a mob at all
        assertNull(SoundClassifier.classify("minecraft:entity.evoker_fangs.attack", ENTITIES));
        assertNull(SoundClassifier.classify("minecraft:entity.glow_item_frame.add_item", ENTITIES));
    }

    @Test
    void soundVariantsBelongToTheirMob() {
        assertMatch("minecraft:entity.wolf_big.growl", "minecraft:wolf", SoundType.AMBIENT);
        assertMatch("minecraft:entity.wolf_puglin.hurt", "minecraft:wolf", SoundType.HURT);
        assertMatch("minecraft:entity.cow_moody.ambient", "minecraft:cow", SoundType.AMBIENT);
        assertMatch("minecraft:entity.baby_wolf.whine", "minecraft:wolf", SoundType.AMBIENT);
        assertMatch("minecraft:entity.baby_horse.step", "minecraft:horse", SoundType.STEP);
        assertMatch("minecraft:entity.zombie.ambient_baby", "minecraft:zombie", SoundType.AMBIENT);
    }

    @Test
    void aliases() {
        assertMatch("minecraft:entity.puffer_fish.blow_up", "minecraft:pufferfish", SoundType.ATTACK);
        assertMatch("minecraft:entity.ghastling.ambient", "minecraft:happy_ghast", SoundType.AMBIENT);
    }

    @Test
    void sharedGroups() {
        assertMatch("minecraft:entity.generic.explode", SoundClassifier.GENERIC, SoundType.ATTACK);
        assertMatch("minecraft:entity.generic.extinguish_fire", SoundClassifier.GENERIC, SoundType.OTHER);
        assertMatch("minecraft:entity.hostile.hurt", SoundClassifier.HOSTILE, SoundType.HURT);
    }

    @Test
    void notMobSounds() {
        assertNull(SoundClassifier.classify("minecraft:entity.arrow.hit", ENTITIES));
        assertNull(SoundClassifier.classify("minecraft:entity.minecart.riding", ENTITIES));
        assertNull(SoundClassifier.classify("minecraft:entity.item.pickup", ENTITIES));
        assertNull(SoundClassifier.classify("minecraft:entity.unknown_thing.ambient", ENTITIES));
        assertNull(SoundClassifier.classify("minecraft:block.stone.break", ENTITIES));
        assertNull(SoundClassifier.classify("minecraft:entity.", ENTITIES));
    }

    @Test
    void moddedMobs() {
        assertMatch("mymod:entity.dragonling.ambient", "mymod:dragonling", SoundType.AMBIENT);
        // another mod's sound for a vanilla mob name is not that mob
        assertNull(SoundClassifier.classify("othermod:entity.zombie.ambient", ENTITIES));
    }

    @Test
    void moddedNamesWithoutEntityPrefix() {
        assertMatch("mymod:grizzly_bear_idle", "mymod:grizzly_bear", SoundType.AMBIENT);
        assertMatch("mymod:grizzly_bear_hurt", "mymod:grizzly_bear", SoundType.HURT);
        assertMatch("mymod:grizzly_bear.death", "mymod:grizzly_bear", SoundType.DEATH);
        assertMatch("mymod:mob.grizzly_bear.step", "mymod:grizzly_bear", SoundType.STEP);
        assertMatch("mymod:dragonling_roar", "mymod:dragonling", SoundType.ATTACK);
        assertMatch("mymod:dragonling_sneeze", "mymod:dragonling", SoundType.OTHER);
        assertMatch("mymod:dragonling", "mymod:dragonling", SoundType.OTHER);
        // a mod's non-living entity, or no entity at all
        assertNull(SoundClassifier.classify("mymod:crystal_shatter", ENTITIES));
        assertNull(SoundClassifier.classify("mymod:music.boss_theme", ENTITIES));
        // vanilla sounds outside "entity." are never mob sounds, even if they start with a mob's name
        assertNull(SoundClassifier.classify("minecraft:zombie_thing.ambient", ENTITIES));
    }
}
