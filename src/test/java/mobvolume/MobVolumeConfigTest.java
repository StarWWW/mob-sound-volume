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
        assertEquals(1.0f, c.factor(ZOMBIE, SoundType.HURT, HURT));
        assertFalse(c.changed(ZOMBIE, ZOMBIE_SOUNDS));
    }

    @Test
    void mobTypeAndSoundVolumesMultiply() {
        MobVolumeConfig c = new MobVolumeConfig();
        c.setVolume(ZOMBIE, 50);
        c.setVolume(ZOMBIE, SoundType.HURT, 50);
        assertEquals(0.25f, c.factor(ZOMBIE, SoundType.HURT, HURT), 1e-6);
        assertEquals(0.5f, c.factor(ZOMBIE, SoundType.AMBIENT, GROAN), 1e-6);
        c.setSoundVolume(GROAN, 20);
        assertEquals(0.1f, c.factor(ZOMBIE, SoundType.AMBIENT, GROAN), 1e-6);
        c.setSoundVolume(GROAN, 0);
        assertEquals(0.0f, c.factor(ZOMBIE, SoundType.AMBIENT, GROAN));
        c.setVolume(ZOMBIE, 200);
        assertEquals(1.0f, c.factor(ZOMBIE, SoundType.HURT, HURT), 1e-6);
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
        assertEquals(1.0f, c.factor(ZOMBIE, SoundType.AMBIENT, GROAN));
    }

    @Test
    void changedAndResetCoverEveryLevel() {
        MobVolumeConfig c = new MobVolumeConfig();
        c.setSoundVolume(HURT, 30);
        assertTrue(c.changed(ZOMBIE, ZOMBIE_SOUNDS), "a single changed sound counts for the mob");
        assertTrue(c.changed(ZOMBIE, SoundType.HURT, List.of(HURT)));
        assertFalse(c.changed(ZOMBIE, SoundType.AMBIENT, List.of(GROAN)));

        c.setVolume(ZOMBIE, SoundType.HURT, 40);
        c.reset(ZOMBIE, SoundType.HURT, List.of(HURT));
        assertEquals(100, c.volume(ZOMBIE, SoundType.HURT));
        assertEquals(100, c.soundVolume(HURT));

        c.setVolume(ZOMBIE, 30);
        c.setSoundVolume(GROAN, 10);
        c.setSoundVolume("minecraft:entity.cow.ambient", 0);
        c.reset(ZOMBIE, ZOMBIE_SOUNDS);
        assertFalse(c.changed(ZOMBIE, ZOMBIE_SOUNDS));
        assertEquals(0, c.soundVolume("minecraft:entity.cow.ambient"), "other mobs are left alone");
        c.resetAll();
        assertEquals(100, c.soundVolume("minecraft:entity.cow.ambient"));
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
        assertEquals(100, loaded.soundVolume(HURT));
    }
}
