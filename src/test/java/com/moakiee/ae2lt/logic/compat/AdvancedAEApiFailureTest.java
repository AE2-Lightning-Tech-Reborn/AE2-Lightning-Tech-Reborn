package com.moakiee.ae2lt.logic.compat;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.GenericStack;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class AdvancedAEApiFailureTest {
    @Test
    void malformedSparseSlotsAreNotRewrittenAsEmptyIngredients() {
        assertNull(AdvancedAECompat.asStackList(List.of("not a stack")));
        assertEquals(Arrays.asList(null, null), AdvancedAECompat.asStackList(Arrays.asList(null, null)));
        assertEquals(List.of(), AdvancedAECompat.asStackList(new GenericStack[0]));
    }

    @Test
    void failedRecipeReadsAreUnknownRatherThanEqual() throws Exception {
        var inputs = BrokenReads.class.getMethod("inputs");
        var outputs = BrokenReads.class.getMethod("outputs");
        var directions = BrokenReads.class.getMethod("directions");
        assertNull(AdvancedAECompat.readPatternSemantics(new BrokenReads(), inputs, outputs, directions));
        assertNull(AdvancedAECompat.readPatternSemantics(new BrokenReads(), inputs, outputs, directions));
    }

    @Test
    void nullAndWrongTypedDirectionMapsRejectEditingAndComparison() throws Exception {
        var inputs = InvalidMap.class.getMethod("inputs");
        var outputs = InvalidMap.class.getMethod("outputs");
        var directions = InvalidMap.class.getMethod("directions");
        assertNull(AdvancedAECompat.readPatternSemantics(new InvalidMap(null), inputs, outputs, directions));
        assertNull(AdvancedAECompat.readPatternSemantics(new InvalidMap("wrong"), inputs, outputs, directions));
        assertNull(AdvancedAECompat.readPatternSemantics(new InvalidMap(new HashMap<>(java.util.Map.of("bad key", "bad face"))),
                inputs, outputs, directions));
        var valid = AdvancedAECompat.readPatternSemantics(new InvalidMap(new HashMap<>()), inputs, outputs, directions);
        assertNotNull(valid);
        assertEquals(valid, AdvancedAECompat.readPatternSemantics(new InvalidMap(new HashMap<>()), inputs, outputs, directions));
    }

    @Test
    void validatesPublicReturnTypesAndStaticnessBeforeReportingApiAvailable() {
        assertNotNull(AdvancedAECompat.findInstanceMethod(ValidAccessor.class, "getSparseInputs", GenericStack[].class));
        assertNull(AdvancedAECompat.findInstanceMethod(WrongAccessor.class, "getSparseInputs", GenericStack[].class));
        assertNull(AdvancedAECompat.findInstanceMethod(StaticAccessor.class, "getSparseInputs", GenericStack[].class));
        assertNotNull(AdvancedAECompat.findEncodeMethod(ValidEncoder.class));
        assertNull(AdvancedAECompat.findEncodeMethod(WrongEncoder.class));
        assertNull(AdvancedAECompat.findEncodeMethod(InstanceEncoder.class));
    }

    public static class BrokenReads {
        public GenericStack[] inputs() { throw new NoSuchMethodError("changed addon API"); }
        public GenericStack[] outputs() { return null; }
        public HashMap<?, ?> directions() { return null; }
    }

    public static class InvalidMap {
        private final Object map;
        InvalidMap(Object map) { this.map = map; }
        public GenericStack[] inputs() { return new GenericStack[1]; }
        public GenericStack[] outputs() { return new GenericStack[1]; }
        public Object directions() { return map; }
    }

    public static class ValidAccessor { public GenericStack[] getSparseInputs() { return null; } }
    public static class WrongAccessor { public Object getSparseInputs() { return null; } }
    public static class StaticAccessor { public static GenericStack[] getSparseInputs() { return null; } }
    public static class ValidEncoder {
        public static ItemStack encodeProcessingPattern(GenericStack[] inputs, GenericStack[] outputs, HashMap<?, ?> directions) { return null; }
    }
    public static class WrongEncoder {
        public static Object encodeProcessingPattern(GenericStack[] inputs, GenericStack[] outputs, HashMap<?, ?> directions) { return null; }
    }
    public static class InstanceEncoder {
        public ItemStack encodeProcessingPattern(GenericStack[] inputs, GenericStack[] outputs, HashMap<?, ?> directions) { return null; }
    }
}
