package com.moakiee.ae2lt.api.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider;
import com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NarrowCraftingCapabilityContractTest {
    @Test
    void submissionsRequireConsistentNonNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new LightningBatchProvider.BatchSubmission(
                LightningBatchProvider.SubmissionStatus.REJECTED, -1L, List.of(), 1L, true,
                LightningBatchProvider.RejectionReason.INVALID_REQUEST));
        assertThrows(NullPointerException.class, () -> new TianshuSynthesizer.SynthesisRequest(
                null, List.of(), 0L, TianshuSynthesizer.CAPABILITY_ID,
                TianshuSynthesizer.API_VERSION, UUID.randomUUID()));
    }

    @Test
    void capabilityContractsExposeNoPlannerCpuProviderOrScreenTypes() {
        for (var type : List.of(LightningBatchProvider.class, TianshuSynthesizer.class)) {
            for (var method : type.getMethods()) {
                var signature = method.toGenericString().toLowerCase(java.util.Locale.ROOT);
                assertTrue(!signature.contains("planner") && !signature.contains("craftingcpu")
                        && !signature.contains("screen") && !signature.contains("jobid"), signature);
            }
        }
    }
}
