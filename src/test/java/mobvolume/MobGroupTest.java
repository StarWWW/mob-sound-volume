package mobvolume;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobGroupTest {
    @Test
    void followsTheGamesSpawnCategory() {
        assertEquals(MobGroup.HOSTILE, MobGroup.of("minecraft:zombie", "monster"));
        assertEquals(MobGroup.ANIMAL, MobGroup.of("minecraft:cow", "creature"));
        assertEquals(MobGroup.ANIMAL, MobGroup.of("minecraft:bat", "ambient"));
        assertEquals(MobGroup.WATER, MobGroup.of("minecraft:cod", "water_ambient"));
        assertEquals(MobGroup.WATER, MobGroup.of("minecraft:glow_squid", "underground_water_creature"));
        assertEquals(MobGroup.WATER, MobGroup.of("minecraft:axolotl", "axolotls"));
    }

    @Test
    void specialGroups() {
        assertEquals(MobGroup.BOSS, MobGroup.of("minecraft:ender_dragon", "monster"));
        assertEquals(MobGroup.BOSS, MobGroup.of("minecraft:warden", "monster"));
        assertEquals(MobGroup.VILLAGER, MobGroup.of("minecraft:villager", "misc"));
        assertEquals(MobGroup.VILLAGER, MobGroup.of("minecraft:wandering_trader", "creature"));
        assertEquals(MobGroup.GOLEM, MobGroup.of("minecraft:iron_golem", "misc"));
        assertEquals(MobGroup.GOLEM, MobGroup.of("minecraft:allay", "creature"));
        assertEquals(MobGroup.WATER, MobGroup.of("minecraft:tadpole", "creature"));
        assertEquals(MobGroup.OTHER, MobGroup.of("minecraft:player", "misc"));
        assertEquals(MobGroup.SHARED, MobGroup.of(SoundClassifier.GENERIC, null));
        assertEquals(MobGroup.SHARED, MobGroup.of(SoundClassifier.HOSTILE, null));
    }

    @Test
    void moddedMobsUseTheirSpawnCategory() {
        assertEquals(MobGroup.HOSTILE, MobGroup.of("mymod:dragonling", "monster"));
        assertEquals(MobGroup.ANIMAL, MobGroup.of("mymod:grizzly_bear", "creature"));
        assertEquals(MobGroup.OTHER, MobGroup.of("mymod:robot", "misc"));
        assertEquals(MobGroup.OTHER, MobGroup.of("mymod:unknown", null));
    }
}
