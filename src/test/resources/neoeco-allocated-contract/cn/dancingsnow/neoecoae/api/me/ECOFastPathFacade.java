package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.level.Level;

/** Test-owned public protocol fixture; no optional-mod implementation is copied. */
public final class ECOFastPathFacade {
    public interface Reservation {
        void commit();
        void refund();
        default boolean ready() { return true; }
    }

    public interface EnergyAccount {
        Reservation reserve(double amount);
        default boolean ready() { return true; }
    }

    public static PreparedBatch prepareAllocated(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, long requested, Level level, UUID craftingId) {
        var state = (Provider) provider;
        state.preparations++;
        state.jobId = craftingId;
        if (inputs[0].get(state.key) != 2) throw new AssertionError("Preparation lost its unit inputs");
        inputs[0].clear();
        return new PreparedBatch(state);
    }

    public static final class PreparedBatch {
        private final Provider provider;
        public PreparedBatch(Provider provider) { this.provider = provider; }
        public long craftCount() { return provider.count; }
        public double power() { return 0; }
        public List<appeng.api.stacks.GenericStack> remainders() { return List.of(); }
        public boolean submit(EnergyAccount account) {
            provider.submissions++;
            if (!account.ready()) throw new AssertionError("Energy account default method was not invoked");
            Object reserved = account.reserve(power());
            if (!(reserved instanceof Reservation reservation) || !reservation.ready())
                throw new AssertionError("Reservation default method was not invoked");
            if (provider.fail) throw new ECOIndeterminateBatchException("ownership uncertain", null);
            if (provider.accept) reservation.commit(); else reservation.refund();
            return provider.accept;
        }
    }

    public static boolean pushPatternForJob(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, UUID craftingId) {
        return provider.pushPattern(pattern, inputs);
    }

    // Discovery-only exact/lifecycle signatures required by LT's shared public facade loader.
    public static PreparedExactBatch prepareExactAllocated(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, java.math.BigInteger requested, Level level, UUID id) {
        throw new UnsupportedOperationException();
    }

    public static void cancelJob(Level level, UUID id) { throw new UnsupportedOperationException(); }

    public static final class PreparedExactBatch {
        public java.math.BigInteger craftCount() { throw new UnsupportedOperationException(); }
        public boolean submit() { throw new UnsupportedOperationException(); }
    }

    public static final class Provider implements ECOFastPathDispatchProvider {
        public AEKey key;
        public long count = 3;
        public boolean accept = true;
        public boolean fail;
        public int ordinary;
        public int preparations;
        public int submissions;
        public UUID jobId;
        public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
        public boolean isBusy() { return false; }
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            ordinary++;
            if (inputs[0].get(key) != 2) throw new AssertionError("Ordinary fallback lost its owned unit inputs");
            inputs[0].clear();
            return true;
        }
    }
}
