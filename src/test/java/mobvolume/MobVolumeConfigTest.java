package mobvolume;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobVolumeConfigTest {
    private static final String ZOMBIE = "minecraft:zombie";
    private static final String GROAN = "minecraft:entity.zombie.ambient";
    private static final String HURT = "minecraft:entity.zombie.hurt";
    private static final List<String> ZOMBIE_SOUNDS = List.of(GROAN, HURT);

    @AfterEach
    void cleanUp() {
        MobVolumeConfig.testConfigDir = null;
    }

    @Test
    void defaultsToNormalVolume() {
        MobVolumeConfig c = new MobVolumeConfig();
        assertEquals(100, c.volume(ZOMBIE));
        assertEquals(100, c.soundVolume(GROAN));
        assertEquals(1.0f, c.factor(MobGroup.HOSTILE, ZOMBIE, SoundType.HURT, HURT));
        assertFalse(c.changed(ZOMBIE, ZOMBIE_SOUNDS));
    }

    @Test
    void mobTypeAndSoundVolumesMultiply() {
        MobVolumeConfig c = new MobVolumeConfig();
        c.setVolume(ZOMBIE, 50);
        c.setVolume(ZOMBIE, SoundType.HURT, 50);
        assertEquals(0.25f, c.factor(MobGroup.HOSTILE, ZOMBIE, SoundType.HURT, HURT), 1e-6);
        assertEquals(0.5f, c.factor(MobGroup.HOSTILE, ZOMBIE, SoundType.AMBIENT, GROAN), 1e-6);
        c.setSoundVolume(GROAN, 20);
        assertEquals(0.1f, c.factor(MobGroup.HOSTILE, ZOMBIE, SoundType.AMBIENT, GROAN), 1e-6);
        c.setSoundVolume(GROAN, 0);
        assertEquals(0.0f, c.factor(MobGroup.HOSTILE, ZOMBIE, SoundType.AMBIENT, GROAN));
        c.setVolume(ZOMBIE, 200);
        assertEquals(1.0f, c.factor(MobGroup.HOSTILE, ZOMBIE, SoundType.HURT, HURT), 1e-6);
    }

    @Test
    void clampsAndForgetsDefaults() {
        MobVolumeConfig c = new MobVolumeConfig();
        c.setVolume(ZOMBIE, 500);
        assertEquals(MobVolumeConfig.MAX, c.volume(ZOMBIE));
        c.setSoundVolume(GROAN, -5);
        assertEquals(0, c.soundVolume(GROAN));
        c.setVolume(ZOMBIE, 100);
        c.setSoundVolume(GROAN, 100);
        c.setVolume(ZOMBIE, SoundType.STEP, 70);
        c.setVolume(ZOMBIE, SoundType.STEP, 100);
        assertFalse(c.changed(ZOMBIE, ZOMBIE_SOUNDS));
    }

    @Test
    void disabledChangesNothing() {
        MobVolumeConfig c = new MobVolumeConfig();
        c.setVolume(ZOMBIE, 0);
        c.enabled = false;
        assertEquals(1.0f, c.factor(MobGroup.HOSTILE, ZOMBIE, SoundType.AMBIENT, GROAN));
    }

    @Test
    void changedCoversEveryLevel() {
        MobVolumeConfig c = new MobVolumeConfig();
        c.setSoundVolume(HURT, 30);
        assertTrue(c.changed(ZOMBIE, ZOMBIE_SOUNDS), "a single changed sound counts for the mob");
        assertTrue(c.changed(ZOMBIE, SoundType.HURT, List.of(HURT)));
        assertFalse(c.changed(ZOMBIE, SoundType.AMBIENT, List.of(GROAN)));
        c.setSoundVolume(HURT, 100);
        c.setVolume(ZOMBIE, SoundType.STEP, 70);
        assertTrue(c.changed(ZOMBIE, ZOMBIE_SOUNDS), "a changed kind of sound counts for the mob");
    }

    @Test
    void eachLevelIsResetOnItsOwn() {
        MobVolumeConfig c = new MobVolumeConfig();
        c.setVolume(MobGroup.HOSTILE, 80);
        c.setVolume(ZOMBIE, 30);
        c.setVolume(ZOMBIE, SoundType.HURT, 40);
        c.setSoundVolume(HURT, 20);

        // the mob back to 100%: its kinds of sound and its sounds keep their settings
        c.setVolume(ZOMBIE, MobVolumeConfig.DEFAULT);
        assertEquals(100, c.volume(ZOMBIE));
        assertEquals(40, c.volume(ZOMBIE, SoundType.HURT));
        assertEquals(20, c.soundVolume(HURT));
        assertTrue(c.changed(ZOMBIE, ZOMBIE_SOUNDS));

        // the kind of sound back to 100%: the single sound keeps its setting
        c.setVolume(ZOMBIE, SoundType.HURT, MobVolumeConfig.DEFAULT);
        assertEquals(20, c.soundVolume(HURT));

        // the category back to 100%: its mobs keep theirs
        c.reset(MobGroup.HOSTILE);
        assertEquals(100, c.volume(MobGroup.HOSTILE));
        assertEquals(20, c.soundVolume(HURT));

        c.setSoundVolume("minecraft:entity.cow.ambient", 0);
        c.resetAll();
        assertFalse(c.changed(ZOMBIE, ZOMBIE_SOUNDS));
        assertEquals(100, c.soundVolume("minecraft:entity.cow.ambient"));
    }

    @Test
    void categoryVolumeAppliesToAllItsMobs() {
        MobVolumeConfig c = new MobVolumeConfig();
        c.setVolume(MobGroup.HOSTILE, 50);
        c.setVolume(ZOMBIE, 50);
        assertEquals(0.25f, c.factor(MobGroup.HOSTILE, ZOMBIE, SoundType.AMBIENT, GROAN), 1e-6);
        assertEquals(1.0f, c.factor(MobGroup.ANIMAL, "minecraft:cow", SoundType.AMBIENT, "minecraft:entity.cow.ambient"));
        c.setVolume(MobGroup.WATER, 0);
        assertEquals(0.0f, c.factor(MobGroup.WATER, "minecraft:cod", SoundType.HURT, "minecraft:entity.cod.hurt"));
        c.reset(MobGroup.HOSTILE);
        assertEquals(100, c.volume(MobGroup.HOSTILE));
        assertEquals(50, c.volume(ZOMBIE), "resetting the category leaves its mobs alone");
        c.resetAll();
        assertEquals(100, c.volume(MobGroup.WATER));
    }

    @Test
    void savesAndLoads(@TempDir Path dir) {
        MobVolumeConfig.testConfigDir = dir;
        MobVolumeConfig c = new MobVolumeConfig();
        c.enabled = false;
        c.setVolume(ZOMBIE, 40);
        c.setVolume(ZOMBIE, SoundType.HURT, 0);
        c.setVolume("mymod:dragonling", SoundType.STEP, 150);
        c.setSoundVolume(GROAN, 25);
        c.setVolume(MobGroup.WATER, 30);
        c.save();

        MobVolumeConfig loaded = new MobVolumeConfig();
        loaded.load();
        assertFalse(loaded.enabled);
        assertEquals(40, loaded.volume(ZOMBIE));
        assertEquals(0, loaded.volume(ZOMBIE, SoundType.HURT));
        assertEquals(100, loaded.volume(ZOMBIE, SoundType.AMBIENT));
        assertEquals(150, loaded.volume("mymod:dragonling", SoundType.STEP));
        assertEquals(100, loaded.volume("mymod:dragonling"));
        assertEquals(25, loaded.soundVolume(GROAN));
        assertEquals(30, loaded.volume(MobGroup.WATER));
        assertEquals(100, loaded.volume(MobGroup.HOSTILE));
        assertEquals(100, loaded.soundVolume(HURT));
    }
}
