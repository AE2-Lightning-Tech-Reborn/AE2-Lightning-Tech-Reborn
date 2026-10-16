package com.moakiee.ae2ltcpuselection;

import com.moakiee.ae2lt.crafting.big.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.*;
import com.moakiee.thunderbolt.api.crafting.batch.*;
import com.moakiee.thunderbolt.ae2.crafting.*;
import com.moakiee.thunderbolt.core.crafting.big.*;
import com.moakiee.thunderbolt.core.crafting.planner.*;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.*;

public final class BigMixedProviderGameTests {
    private static final BigInteger N = BigInteger.TEN.pow(100);
    private static AEKey raw() { return AEItemKey.of(Items.IRON_INGOT); }
    private static AEKey output() { return AEItemKey.of(Items.GOLD_INGOT); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class Dispatcher {
        private final Object instance;
        private final java.lang.reflect.Method dispatch;
        Dispatcher() {
            try {
                var type = Class.forName("com.moakiee.ae2lt.crafting.big.BigProviderDispatcher");
                var constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                instance = constructor.newInstance();
                dispatch = type.getDeclaredMethod("dispatch", BigCraftingJob.class, BigProviderRecipe.class,
                        IPatternDetails.class, ICraftingProvider.class, net.minecraft.world.level.Level.class,
                        IEnergyService.class, long.class, Runnable.class);
                dispatch.setAccessible(true);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        long dispatch(BigCraftingJob job, BigProviderRecipe recipe, IPatternDetails details,
                ICraftingProvider provider, net.minecraft.world.level.Level level,
                IEnergyService energy, long tick, Runnable changed) {
            try { return (long) dispatch.invoke(instance, job, recipe, details, provider, level, energy, tick, changed); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
    }

    private static IPatternDetails details(GameTestHelper h) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(raw(), 2)), List.of(new GenericStack(output(), 3))), h.getLevel());
    }
    private static BigProviderRecipe recipe(GameTestHelper h, IPatternDetails pattern) {
        return Objects.requireNonNull(BigProviderRecipe.capture(pattern, List.of(Map.of(raw(), 2L)), h.getLevel()));
    }
    private static BigCraftingJob job(BigProviderRecipe recipe, BigInteger copies) {
        var program = BigExecutionProgram.of(List.of(BigExecutionProgram.block(
                List.of(new BigExecutionProgram.Step<>(recipe.pattern(), copies)), BigInteger.ONE)));
        var job = new BigCraftingJob(output(), copies.multiply(BigInteger.valueOf(3)), program);
        job.escrow.putAll(program.required());
        job.beginStreaming();
        return job;
    }
    private static IEnergyService power(double maximum) {
        return (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                new Class<?>[] {IEnergyService.class}, (p, m, args) -> {
                    if (m.getName().equals("extractAEPower")) return Math.min(maximum, (double) args[0]);
                    throw new AssertionError("Unexpected energy call " + m);
                });
    }
    private static class Provider implements ICraftingProvider {
        final IPatternDetails pattern;
        boolean accept = true, fail;
        int calls;
        Provider(IPatternDetails pattern) { this.pattern = pattern; }
        public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
        public boolean isBusy() { return false; }
        public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
            require(inputs.length == 1 && inputs[0].get(raw()) == 2, "single-copy input changed");
            calls++;
            if (fail) throw new IllegalStateException("provider failed after taking ownership");
            return accept;
        }
    }
    private static final class Batch extends Provider implements IBatchCraftingProvider {
        long offered, accepted = 7;
        BigCraftingJob synchronous;
        Batch(IPatternDetails p) { super(p); }
        public long getBatchCapacity(IPatternDetails p) { return Long.MAX_VALUE; }
        public long pushBatch(IPatternDetails p, KeyCounter[] template, long count) {
            require(template[0].get(raw()) == 2, "batch template was scaled");
            offered = count;
            if (synchronous != null) require(synchronous.receive(output(), accepted * 3, Actionable.MODULATE)
                    == accepted * 3, "synchronous receipt lost");
            return count - accepted;
        }
    }

    public static void ordinaryProviderKeepsHugeRemainderAndWaitsForActualOutput(GameTestHelper h) {
        var details = details(h); var recipe = recipe(h, details); var job = job(recipe, N);
        var provider = new Provider(details); var dispatcher = new Dispatcher();
        require(dispatcher.dispatch(job, recipe, details, provider, h.getLevel(), power(100), 1, () -> {}) == 1,
                "ordinary provider rejected because order exceeds long");
        require(job.remainingStepCopies().equals(N.subtract(BigInteger.ONE)), "remaining count truncated");
        require(job.escrow.get(raw()).equals(N.multiply(BigInteger.TWO).subtract(BigInteger.TWO)), "wrong input ownership");
        require(!job.escrow.containsKey(output()) && job.waiting.get(output()).equals(BigInteger.valueOf(3)), "fabricated output");
        require(!job.advanceStreaming(), "advanced before all copies and returns");
        var restored = BigCraftingJob.load(job.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        require(restored.acceptedCopies.equals(BigInteger.ONE) && restored.waiting.equals(job.waiting), "restart lost in-flight work");
        require(restored.receive(output(), 10, Actionable.SIMULATE) == 3 && restored.waiting.equals(job.waiting), "simulation mutated");
        require(restored.receive(output(), 10, Actionable.MODULATE) == 3 && restored.waiting.isEmpty(), "return not capped at requested");
        restored.cancel();
        require(restored.escrow.get(raw()).equals(N.multiply(BigInteger.TWO).subtract(BigInteger.TWO)), "cancel duplicated machine inputs");
        require(restored.escrow.get(output()).equals(BigInteger.valueOf(3)), "cancel lost returned output");
        h.succeed();
    }

    public static void longBatchPartialAcceptanceAndSynchronousReturnsAreExact(GameTestHelper h) {
        var details = details(h); var recipe = recipe(h, details); var job = job(recipe, N);
        var batch = new Batch(details); batch.synchronous = job;
        require(new Dispatcher().dispatch(job, recipe, details, batch, h.getLevel(), power(Double.MAX_VALUE), 1, () -> {}) == 7,
                "partial batch count lost");
        require(batch.offered == Long.MAX_VALUE / 3, "output transfer exceeded long boundary");
        require(job.acceptedCopies.equals(BigInteger.valueOf(7)) && job.waiting.isEmpty(), "partial/synchronous output mismatch");
        require(job.escrow.get(raw()).equals(N.multiply(BigInteger.TWO).subtract(BigInteger.valueOf(14))), "rejected inputs not refunded");
        require(job.escrow.get(output()).equals(BigInteger.valueOf(21)), "synchronous outputs changed");
        var restored = BigCraftingJob.load(job.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        require(restored.escrow.equals(job.escrow), "partial batch changed after restart");
        h.succeed();
    }

    public static void orderBeyondLongCompletesInBoundedPhysicalBatches(GameTestHelper h) {
        var details = details(h); var recipe = recipe(h, details);
        var copies = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
        var job = job(recipe, copies);
        int[] calls = {0};
        var provider = new IBatchCraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(details); }
            public boolean isBusy() { return false; }
            public long pushBatch(IPatternDetails p, KeyCounter[] template, long n) {
                require(n > 0 && n <= Long.MAX_VALUE / 3, "unbounded physical transfer");
                calls[0]++;
                return 0;
            }
        };
        var dispatcher = new Dispatcher();
        while (!job.returning && calls[0] < 5) {
            long n = dispatcher.dispatch(job, recipe, details, provider, h.getLevel(), power(Double.MAX_VALUE), 1, () -> {});
            require(n > 0 && !job.advanceStreaming(), "completed before real return");
            require(job.receive(output(), Math.multiplyExact(n, 3), Actionable.MODULATE) == n * 3, "return truncated");
            job.advanceStreaming();
        }
        require(job.returning && calls[0] == 4, "large order did not finish in four bounded calls");
        require(job.escrow.equals(Map.of(output(), copies.multiply(BigInteger.valueOf(3)))), "final exact inventory changed");
        h.succeed();
    }

    public static void preparedAdmissionAndEnergyLimitOnlyAcceptedInputs(GameTestHelper h) {
        var details = details(h); var recipe = recipe(h, details); var job = job(recipe, BigInteger.TEN);
        long[] preparedOffer = {0};
        var provider = new IBatchCraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(details); }
            public boolean isBusy() { return false; }
            public long pushBatch(IPatternDetails p, KeyCounter[] template, long n) {
                throw new AssertionError("prepared admission bypassed");
            }
            public PreparedBatch prepareBatch(IPatternDetails p, KeyCounter[] template, long n, BatchJobView view) {
                preparedOffer[0] = n;
                return new PreparedBatch() {
                    public long capacity() { return 2; }
                    public long push(long n) { require(n == 2, "prepared capacity exceeded"); return 1; }
                };
            }
        };
        long received = new Dispatcher().dispatch(job, recipe, details, provider, h.getLevel(), power(7), 1, () -> {});
        require(preparedOffer[0] == 3 && received == 1, "energy/admission/partial acceptance mismatch");
        require(job.escrow.get(raw()).equals(BigInteger.valueOf(18))
                && job.waiting.get(output()).equals(BigInteger.valueOf(3)), "charged rejected work");
        h.succeed();
    }

    public static void oneCopyBatchCapacityStillUsesItsJobAwareContract(GameTestHelper h) {
        var details = details(h); var recipe = recipe(h, details); var job = job(recipe, N);
        var provider = new IBatchCraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(details); }
            public boolean isBusy() { return false; }
            public long getBatchCapacity(IPatternDetails p) { return 1; }
            public long pushBatch(IPatternDetails p, KeyCounter[] template, long n) {
                throw new AssertionError("lost job context at one-copy capacity");
            }
            public long pushBatch(IPatternDetails p, KeyCounter[] template, long n, BatchJobView view) {
                require(n == 1 && view.craftingId().equals(job.id), "invalid one-copy batch context");
                return 0;
            }
        };
        require(new Dispatcher().dispatch(job, recipe, details, provider, h.getLevel(), power(100), 1, () -> {}) == 1,
                "one-copy batch refused");
        h.succeed();
    }

    public static void rejectionRefundsButIndeterminateOwnershipDoesNotRetry(GameTestHelper h) {
        var details = details(h); var recipe = recipe(h, details); var job = job(recipe, BigInteger.TEN);
        var provider = new Provider(details); provider.accept = false;
        var dispatcher = new Dispatcher();
        dispatcher.dispatch(job, recipe, details, provider, h.getLevel(), power(100), 1, () -> {});
        require(job.waiting.isEmpty() && job.escrow.get(raw()).equals(BigInteger.valueOf(20)), "rejection lost stock");
        provider.fail = true;
        dispatcher.dispatch(job, recipe, details, provider, h.getLevel(), power(100), 2, () -> {});
        require(job.dispatchFailed && job.escrow.get(raw()).equals(BigInteger.valueOf(18)), "uncertain inputs refunded");
        dispatcher.dispatch(job, recipe, details, provider, h.getLevel(), power(100), 3, () -> {});
        require(provider.calls == 2, "indeterminate call was retried");
        job.cancel();
        require(job.waiting.isEmpty() && job.escrow.get(raw()).equals(BigInteger.valueOf(18)), "cancel duplicated uncertain input");
        h.succeed();
    }

    public static void exactPlanDisplaySurvivesNativePacketWithoutPreviewOnlyFlag(GameTestHelper h) {
        var details = details(h); var recipe = recipe(h, details);
        var stock = Map.of(raw(), N.multiply(BigInteger.TWO));
        var result = BigCraftingPlanner.plan(CraftGraph.<AEKey>builder().stockExact(raw(), stock.get(raw()))
                .pattern(recipe.pattern()).build(), output(), N.multiply(BigInteger.valueOf(3)), stock);
        require(result.executable(), "ordinary provider graph is not executable");
        var plan = new BigCraftingPlan(output(), N.multiply(BigInteger.valueOf(3)), result, "");
        var report = ExactPlanReports.get(plan.summary());
        require(!report.previewOnly(), "executable report marked preview-only");
        require(report.entries().get(output()).crafting().equals(N.multiply(BigInteger.valueOf(3))), "display count truncated");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        var packet = new appeng.core.network.clientbound.CraftConfirmPlanPacket(plan.summary());
        appeng.core.network.clientbound.CraftConfirmPlanPacket.STREAM_CODEC.encode(buffer, packet);
        var decoded = appeng.core.network.clientbound.CraftConfirmPlanPacket.STREAM_CODEC.decode(buffer);
        require(report.equals(ExactPlanReports.get(decoded.plan())), "native packet changed exact quantities or executable flag");
        require(!ExactPlanReports.isPreview(decoded.plan()), "native decoded plan became preview only");
        buffer.release();
        h.succeed();
    }
}
