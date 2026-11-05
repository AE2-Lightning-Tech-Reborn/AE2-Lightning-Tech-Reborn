package com.moakiee.ae2lt.machine.miningfactory;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class MiningFactoryParallelConfigTest {
    @Test
    void independentDefaultsMatchEightMatrixCapacityAndBoundOverflow() {
        var builder = new ForgeConfigSpec.Builder();
        MiningFactoryConfig.define(builder);
        var spec = builder.build();
        var config = CommentedConfig.inMemory();
        spec.correct(config);
        spec.setConfig(config);
        assertEquals(8, MiningFactoryInventory.MATRIX_SLOT_LIMIT);
        assertEquals(8, MiningFactoryConfig.parallelCapacity(0));
        assertEquals(8, MiningFactoryConfig.parallelCapacity(-1));
        assertEquals(256, MiningFactoryConfig.parallelCapacity(1));
        assertEquals(2048, MiningFactoryConfig.parallelCapacity(8));
        assertEquals(2048, MiningFactoryConfig.parallelCapacity(32));
        assertEquals(2048, MiningFactoryConfig.parallelCapacity(Integer.MAX_VALUE));
        spec.getValues().<ForgeConfigSpec.IntValue>get("miningFactory.parallelPerMatrix").set(Integer.MAX_VALUE / 8);
        assertEquals(2_147_483_640, MiningFactoryConfig.parallelCapacity(8));
        assertEquals(2_147_483_640, MiningFactoryConfig.parallelCapacity(Integer.MAX_VALUE));
        spec.getValues().<ForgeConfigSpec.IntValue>get("miningFactory.lootSamplesPerTick").set(4096);
        assertEquals(4096, MiningFactoryConfig.samples());
    }
}
