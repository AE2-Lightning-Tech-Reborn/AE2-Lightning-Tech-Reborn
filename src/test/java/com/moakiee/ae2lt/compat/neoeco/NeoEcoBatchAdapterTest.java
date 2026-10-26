package com.moakiee.ae2lt.compat.neoeco;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

public class NeoEcoBatchAdapterTest {
    private static final UUID JOB = UUID.fromString("537f731f-0555-4319-af1b-972c87a8e51a");

    private static NeoEcoBatchAdapter adapter() throws Exception {
        return new NeoEcoBatchAdapter(new NeoEcoBatchApi(Provider.class, Facade.class, Energy.class, Reservation.class));
    }
    private static BatchJobView job() {
        return (BatchJobView) Proxy.newProxyInstance(BatchJobView.class.getClassLoader(),
                new Class<?>[]{BatchJobView.class}, (p, m, a) -> m.getName().equals("craftingId") ? JOB : null);
    }
    private static KeyCounter[] inputs() {
        var slot = new KeyCounter(); slot.add(LightningKey.HIGH_VOLTAGE, 2);
        return new KeyCounter[]{slot};
    }

    @Test void partialAcceptanceRetainsOnlyUnacceptedCopiesAndDoesNotMutateTemplate() throws Exception {
        var provider = new Provider();
        var template = inputs();
        assertEquals(7, adapter().resolve(provider).pushBatch(null, template, 10, job()));
        assertEquals(2, template[0].get(LightningKey.HIGH_VOLTAGE));
        assertEquals(JOB, provider.job);
        assertEquals(3, provider.accepted);
        assertEquals(0, provider.ordinary);
        assertEquals(1, provider.submissions);
    }

    @Test void explicitRejectionFallsBackOnceWithJobIdentityAndFreshInputs() throws Exception {
        var provider = new Provider(); provider.accept = false;
        var template = inputs();
        var endpoint = adapter().resolve(provider);
        endpoint.beginDispatchTick(10);
        assertEquals(9, endpoint.pushBatch(null, template, 10, job()));
        assertEquals(JOB, provider.job);
        assertEquals(1, provider.ordinary);
        assertEquals(2, provider.ordinaryInput);
        assertEquals(2, template[0].get(LightningKey.HIGH_VOLTAGE));
        assertEquals(1, endpoint.getBatchCapacity(null));
        endpoint.beginDispatchTick(10);
        assertEquals(1, endpoint.getBatchCapacity(null));
        endpoint.beginDispatchTick(11);
        assertEquals(Long.MAX_VALUE, endpoint.getBatchCapacity(null));
    }

    @Test void unsupportedRecipeAndOrdinaryRejectionLeaveAllCopiesWithCpu() throws Exception {
        var provider = new Provider(); provider.prepare = false; provider.ordinaryAccept = false;
        assertEquals(10, adapter().resolve(provider).pushBatch(null, inputs(), 10, job()));
        assertEquals(0, provider.submissions);
        assertEquals(1, provider.ordinary);
    }

    @Test void submissionFailurePropagatesWithoutOrdinaryReplay() throws Exception {
        var provider = new Provider(); provider.failure = new IllegalStateException("ownership uncertain");
        var endpoint = adapter().resolve(provider);
        assertSame(provider.failure, assertThrows(IllegalStateException.class,
                () -> endpoint.pushBatch(null, inputs(), 10, job())));
        assertEquals(0, provider.ordinary);
        assertEquals(1, provider.submissions);
    }

    @Test void invalidAdmissionAndNonzeroEnergyAreRejectedBeforeSubmission() throws Exception {
        for (long count : new long[]{-1, 0, 11}) {
            var provider = new Provider(); provider.count = count;
            var endpoint = adapter().resolve(provider);
            assertEquals(9, endpoint.pushBatch(null, inputs(), 10, job()));
            assertEquals(0, provider.submissions);
            assertEquals(1, endpoint.getBatchCapacity(null));
            endpoint.beginDispatchTick(100);
            assertEquals(1, endpoint.getBatchCapacity(null));
        }
        var provider = new Provider(); provider.power = 1;
        var endpoint = adapter().resolve(provider);
        assertEquals(9, endpoint.pushBatch(null, inputs(), 10, job()));
        assertEquals(0, provider.submissions);
    }

    @Test void contextFreeAndEmptyDispatchDoNotTransferCustody() throws Exception {
        var provider = new Provider(); var endpoint = adapter().resolve(provider);
        assertEquals(10, endpoint.pushBatch(null, inputs(), 10));
        assertEquals(0, endpoint.pushBatch(null, inputs(), 0, job()));
        assertEquals(0, provider.submissions);
        assertEquals(0, provider.ordinary);
    }

    @Test void hugeAllocatedCountIsNotNarrowedAndPreparedBatchesAreSingleUse() throws Exception {
        var provider = new Provider(); provider.count = Long.MAX_VALUE;
        var api = new NeoEcoBatchApi(Provider.class, Facade.class, Energy.class, Reservation.class);
        var prepared = api.prepare(provider, null, inputs(), Long.MAX_VALUE, null, JOB);
        assertEquals(Long.MAX_VALUE, prepared.crafts);
        assertTrue(prepared.submit());
        assertThrows(IllegalStateException.class, prepared::submit);
    }

    @Test void absentModDoesNotLinkOptionalTypes() {
        assertNull(NeoEcoBatchCompat.createAdapter());
        assertNull(NeoEcoBatchCompat.loadAdapter(new ClassLoader(null) {}));
    }

    @Test void admissionLimitsExtractionAndSnapshotsTheBorrowedJobView() throws Exception {
        var provider = new Provider(); var endpoint = adapter().resolve(provider);
        var id = new java.util.concurrent.atomic.AtomicReference<>(JOB);
        var view = (BatchJobView) Proxy.newProxyInstance(BatchJobView.class.getClassLoader(),
                new Class<?>[]{BatchJobView.class}, (p,m,a) -> m.getName().equals("craftingId") ? id.get() : null);
        var prepared = endpoint.prepareBatch(null, inputs(), 10, view);
        assertEquals(3, prepared.capacity());
        id.set(UUID.randomUUID());
        assertEquals(0, prepared.push(3));
        assertEquals(JOB, provider.job);
        assertEquals(3, provider.accepted);
        assertThrows(IllegalArgumentException.class, () -> prepared.push(4));
    }

    // Protocol fixtures belong to the test source set; no NeoECO implementation is copied or bundled.
    public interface Energy { Reservation reserve(double amount); }
    public interface Reservation { void commit(); void refund(); }
    public static class Provider implements ICraftingProvider {
        boolean prepare = true, accept = true, ordinaryAccept = true;
        long count = 3, accepted, ordinaryInput;
        double power;
        int ordinary, submissions;
        UUID job;
        RuntimeException failure;
        @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
        @Override public boolean isBusy() { return false; }
        @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
            ordinary++; ordinaryInput = inputs[0].get(LightningKey.HIGH_VOLTAGE); inputs[0].clear();
            return ordinaryAccept;
        }
    }
    public static class Facade {
        public static Prepared prepareAllocated(ICraftingProvider value, IPatternDetails details,
                KeyCounter[] inputs, long offered, Level level, UUID job) {
            var provider = (Provider) value; provider.job = job; inputs[0].clear();
            return provider.prepare ? new Prepared(provider) : null;
        }
        public static boolean pushPatternForJob(ICraftingProvider value, IPatternDetails details,
                KeyCounter[] inputs, UUID job) {
            ((Provider) value).job = job;
            return value.pushPattern(details, inputs);
        }
    }
    public static class Prepared {
        private final Provider provider;
        Prepared(Provider provider) { this.provider = provider; }
        public long craftCount() { return provider.count; }
        public double power() { return provider.power; }
        public boolean submit(Energy energy) {
            provider.submissions++;
            if (provider.failure != null) throw provider.failure;
            var reservation = energy.reserve(0);
            if (provider.accept) { provider.accepted = provider.count; reservation.commit(); }
            else reservation.refund();
            return provider.accept;
        }
    }
}
