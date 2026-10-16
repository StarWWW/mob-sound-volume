package mobvolume;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobGroupTest {
    @Test
    void followsTheGamesSpawnCategory() {
        assertEquals(MobGroup.HOSTILE, MobGroup.of("minecraft:zombie", "monster", true));
        assertEquals(MobGroup.ANIMAL, MobGroup.of("minecraft:cow", "creature", true));
        assertEquals(MobGroup.ANIMAL, MobGroup.of("minecraft:bat", "ambient", true));
        assertEquals(MobGroup.WATER, MobGroup.of("minecraft:cod", "water_ambient", true));
        assertEquals(MobGroup.WATER, MobGroup.of("minecraft:glow_squid", "underground_water_creature", true));
        assertEquals(MobGroup.WATER, MobGroup.of("minecraft:axolotl", "axolotls", true));
    }

    @Test
    void specialGroups() {
        assertEquals(MobGroup.BOSS, MobGroup.of("minecraft:ender_dragon", "monster", true));
        assertEquals(MobGroup.BOSS, MobGroup.of("minecraft:warden", "monster", true));
        assertEquals(MobGroup.VILLAGER, MobGroup.of("minecraft:villager", "misc", true));
        assertEquals(MobGroup.VILLAGER, MobGroup.of("minecraft:wandering_trader", "creature", true));
        assertEquals(MobGroup.GOLEM, MobGroup.of("minecraft:iron_golem", "misc", true));
        assertEquals(MobGroup.GOLEM, MobGroup.of("minecraft:allay", "creature", true));
        assertEquals(MobGroup.WATER, MobGroup.of("minecraft:tadpole", "creature", true));
        assertEquals(MobGroup.OTHER, MobGroup.of("minecraft:player", "misc", true));
        assertEquals(MobGroup.SHARED, MobGroup.of(SoundClassifier.GENERIC, null, true));
        assertEquals(MobGroup.SHARED, MobGroup.of(SoundClassifier.HOSTILE, null, true));
    }

    @Test
    void moddedMobsUseTheirSpawnCategory() {
        assertEquals(MobGroup.HOSTILE, MobGroup.of("mymod:dragonling", "monster", true));
        assertEquals(MobGroup.ANIMAL, MobGroup.of("mymod:grizzly_bear", "creature", true));
        assertEquals(MobGroup.OTHER, MobGroup.of("mymod:robot", "misc", true));
    }

    @Test
    void thingsThatAreNotAlive() {
        assertEquals(MobGroup.OBJECT, MobGroup.of("minecraft:minecart", "misc", false));
        assertEquals(MobGroup.OBJECT, MobGroup.of("minecraft:arrow", "misc", false));
        assertEquals(MobGroup.OBJECT, MobGroup.of(SoundClassifier.BOAT, null, false));
        // a sound name that matches no entity at all
        assertEquals(MobGroup.OBJECT, MobGroup.of("mymod:unknown", null, false));
        // the armor stand is a living entity that is listed with the player
        assertEquals(MobGroup.OTHER, MobGroup.of("minecraft:armor_stand", "misc", true));
        assertEquals(MobGroup.SHARED, MobGroup.of(SoundClassifier.FISH, null, false));
    }
}
