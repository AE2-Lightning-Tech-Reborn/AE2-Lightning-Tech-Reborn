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

    public static Batch prepareAllocated(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, long requested, Level level, UUID craftingId) {
        var state = (Provider) provider;
        state.preparations++;
        state.jobId = craftingId;
        if (inputs[0].get(state.key) != 2) throw new AssertionError("Preparation lost its unit inputs");
        inputs[0].clear();
        return new Batch(state);
    }

    public static final class Batch {
        private final Provider provider;
        public Batch(Provider provider) { this.provider = provider; }
        public long craftCount() { return provider.count; }
        public boolean submit(EnergyAccount account) {
            provider.submissions++;
            if (!account.ready()) throw new AssertionError("Energy account default method was not invoked");
            Object reserved = account.reserve(provider.count);
            if (!(reserved instanceof Reservation reservation) || !reservation.ready())
                throw new AssertionError("Reservation default method was not invoked");
            if (provider.fail) throw new ECOIndeterminateBatchException("ownership uncertain", null);
            if (provider.accept) reservation.commit(); else reservation.refund();
            return provider.accept;
        }
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
