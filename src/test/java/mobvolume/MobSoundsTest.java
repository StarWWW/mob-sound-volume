package mobvolume;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobSoundsTest {
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    @Test
    void variantsKeepOnlyTheWordsThatDiffer() {
        List<Identifier> barks = List.of(id("entity.wolf.ambient"), id("entity.wolf_big.ambient"),
            id("entity.wolf_angry.ambient"));
        Map<Identifier, String> v = MobSounds.variants(barks);
        assertEquals("", v.get(id("entity.wolf.ambient")), "the plain one gets no extra name");
        assertEquals("big", v.get(id("entity.wolf_big.ambient")));
        assertEquals("angry", v.get(id("entity.wolf_angry.ambient")));
    }

    @Test
    void variantsAtTheEnd() {
        Map<Identifier, String> v = MobSounds.variants(List.of(id("entity.horse.step"), id("entity.horse.step_wood")));
        assertEquals("", v.get(id("entity.horse.step")));
        assertEquals("wood", v.get(id("entity.horse.step_wood")));
    }

    @Test
    void wholeNameWhenTheDifferenceIsNotEnough() {
        // [a b], [a x b] and [a b b]: taking the shared start and end away would leave "x" and "b" but also "" twice
        // in some layouts; whatever happens, no two sounds may end up with the same name
        List<Identifier> same = List.of(id("entity.a.b"), id("entity.a_b.b"), id("entity.a_b_b.b"));
        Map<Identifier, String> v = MobSounds.variants(same);
        assertEquals(3, new java.util.HashSet<>(v.values()).size());
    }
}
