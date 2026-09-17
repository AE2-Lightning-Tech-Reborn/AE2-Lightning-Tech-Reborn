package com.moakiee.ae2lt.compat.neoeco;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import org.junit.jupiter.api.Test;

class NeoEcoFastPathBatchAdapterTest {
    private final BatchJobView job = (BatchJobView) Proxy.newProxyInstance(
            BatchJobView.class.getClassLoader(), new Class<?>[] {BatchJobView.class},
            (proxy, method, args) -> null);

    @Test
    void providerWithoutAllocatedFastPathStillReceivesOneOrdinaryCopy() {
        var provider = new OrdinaryProvider(true);
        var adapter = new NeoEcoFastPathBatchAdapter.AdaptedProvider(provider);
        var template = new KeyCounter[] {new KeyCounter()};

        assertEquals(6L, adapter.pushBatch(null, template, 7L, job));
        assertEquals(1, provider.calls);
        assertNotSame(template, provider.received);
        assertNotSame(template[0], provider.receivedSlot);
        assertNotNull(template[0], "the provider must not mutate the borrowed template array");
    }

    @Test
    void rejectedOrdinaryCopyLeavesTheWholeBatchForCallerRollback() {
        var provider = new OrdinaryProvider(false);
        var adapter = new NeoEcoFastPathBatchAdapter.AdaptedProvider(provider);

        assertEquals(7L, adapter.pushBatch(null, new KeyCounter[] {new KeyCounter()}, 7L, job));
        assertEquals(1, provider.calls);
    }

    @Test
    void singleCopyOrderCanCompleteWithoutFastPath() {
        var provider = new OrdinaryProvider(true);
        var adapter = new NeoEcoFastPathBatchAdapter.AdaptedProvider(provider);

        assertEquals(0L, adapter.pushBatch(null, new KeyCounter[] {new KeyCounter()}, 1L, job));
        assertEquals(1, provider.calls);
    }

    @Test
    void zeroCopyBudgetNeverPushesTheProvider() {
        var provider = new OrdinaryProvider(true);
        var adapter = new NeoEcoFastPathBatchAdapter.AdaptedProvider(provider);

        assertEquals(0L, adapter.pushBatch(null, new KeyCounter[0], 0L, job));
        assertEquals(0, provider.calls);
    }

    @Test
    void nativeFastPathProviderAlsoFallsBackForUnsupportedPattern() {
        var provider = new NativeProvider();
        IPatternDetails pattern = (IPatternDetails) Proxy.newProxyInstance(
                IPatternDetails.class.getClassLoader(), new Class<?>[] {IPatternDetails.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getInputs" -> new IPatternDetails.IInput[0];
                    case "getOutputs" -> List.of();
                    default -> null;
                });
        var adapter = new NeoEcoFastPathBatchAdapter().adapt(provider, pattern, job);

        assertNotNull(adapter);
        assertEquals(2L, adapter.pushBatch(pattern, new KeyCounter[] {new KeyCounter()}, 3L, job));
        assertEquals(1, ((OrdinaryProvider) provider).calls);
    }

    private static class OrdinaryProvider implements ICraftingProvider {
        private final boolean accepted;
        private int calls;
        private KeyCounter[] received;
        private KeyCounter receivedSlot;

        private OrdinaryProvider(boolean accepted) {
            this.accepted = accepted;
        }

        @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
        @Override public boolean isBusy() { return false; }
        @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            calls++;
            received = inputs;
            receivedSlot = inputs[0];
            if (accepted) inputs[0] = null;
            return accepted;
        }
    }

    private static final class NativeProvider extends OrdinaryProvider implements ECOFastPathDispatchProvider {
        private NativeProvider() { super(true); }
        @Override public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
            throw new AssertionError("unsupported recipe must be declined before batch preparation");
        }
    }
}
