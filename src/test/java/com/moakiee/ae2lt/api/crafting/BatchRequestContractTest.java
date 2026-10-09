package com.moakiee.ae2lt.api.crafting;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider;
import com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BatchRequestContractTest {
    @BeforeAll
    static void bootstrap() {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bothApisRejectNonPositiveInputAmounts() {
        var processingId = AEItemKey.of(Items.STICK);
        for (long amount : new long[] {Long.MIN_VALUE, -1, 0}) {
            var inputs = List.of(List.of(new GenericStack(processingId, amount)));
            assertThrows(IllegalArgumentException.class,
                    () -> new LightningBatchProvider.BatchRequest(LightningBatchProvider.API_VERSION,
                            LightningBatchProvider.CAPABILITY_ID, processingId, inputs, 1, UUID.randomUUID()));
            assertThrows(IllegalArgumentException.class,
                    () -> new TianshuSynthesizer.SynthesisRequest(processingId, inputs, 1,
                            TianshuSynthesizer.CAPABILITY_ID, TianshuSynthesizer.API_VERSION, UUID.randomUUID()));
        }
    }

    @Test
    void nestedInputListsAreImmutableSnapshotsForNonceEquality() {
        var processingId = AEItemKey.of(Items.STICK);
        var input = new GenericStack(processingId, 2);
        var slot = new ArrayList<>(List.of(input));
        var inputs = new ArrayList<List<GenericStack>>(List.of(slot));
        var nonce = UUID.randomUUID();
        var batch = new LightningBatchProvider.BatchRequest(LightningBatchProvider.API_VERSION,
                LightningBatchProvider.CAPABILITY_ID, processingId, inputs, 3, nonce);
        var synthesis = new TianshuSynthesizer.SynthesisRequest(processingId, inputs, 3,
                TianshuSynthesizer.CAPABILITY_ID, TianshuSynthesizer.API_VERSION, nonce);
        slot.clear();
        inputs.clear();
        assertEquals(List.of(List.of(input)), batch.inputsPerCraft());
        assertEquals(batch.inputsPerCraft(), synthesis.inputsPerCraft());
        assertThrows(UnsupportedOperationException.class, () -> batch.inputsPerCraft().clear());
        assertThrows(UnsupportedOperationException.class, () -> batch.inputsPerCraft().get(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> synthesis.inputsPerCraft().clear());
        assertThrows(UnsupportedOperationException.class, () -> synthesis.inputsPerCraft().get(0).clear());
        assertEquals(batch, new LightningBatchProvider.BatchRequest(LightningBatchProvider.API_VERSION,
                LightningBatchProvider.CAPABILITY_ID, processingId, List.of(List.of(input)), 3, nonce));
        assertEquals(synthesis, new TianshuSynthesizer.SynthesisRequest(processingId,
                List.of(List.of(input)), 3, TianshuSynthesizer.CAPABILITY_ID, TianshuSynthesizer.API_VERSION, nonce));
    }
}
