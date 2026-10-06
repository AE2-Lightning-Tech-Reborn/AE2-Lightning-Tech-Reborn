package com.moakiee.ae2lt.grid;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class FrequencyBindingIdTest {
    @Test
    void legacyAndMalformedIdsNormalizeButPositiveIdsSurviveBeforeRegistryReady() {
        var binding = new FrequencyBindingHelper(null);
        for (int id : new int[] {Integer.MIN_VALUE, -2, -1, 0, 1, Integer.MAX_VALUE}) {
            var input = new CompoundTag();
            input.putInt("FrequencyId", id);
            binding.load(input);
            assertEquals(id > 0 ? id : -1, binding.getFrequencyId());
            var output = new CompoundTag();
            binding.save(output);
            assertEquals(id > 0 ? id : -1, output.getInt("FrequencyId"));
        }
        binding.load(new CompoundTag());
        binding.setFrequency(0);
        assertEquals(-1, binding.getFrequencyId());
    }
}
