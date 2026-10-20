package com.moakiee.ae2lt.crafting.big;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.crafting.runtime.CraftingDispatch;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;

/**
 * Optional Useless Mod 2.4.6 public bigint API. Reflection isolates older/absent releases without
 * compiling against or bundling their machine implementation. No private fields or machine ticks.
 */
public final class UselessBigIntegerCompat {
    private static final String PREFIX = "com.sorrowmist.useless.api.crafting.bigint.";
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("ae2lt", "exact_cpu");
    private static boolean initialized;
    private static Api api;

    private UselessBigIntegerCompat() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        if (ModList.get() == null || !ModList.get().isLoaded("useless_mod")) return;
        try {
            var candidate = new Api();
            candidate.register();
            api = candidate;
        } catch (ClassNotFoundException ignored) {
            // Older releases keep the existing native single/long provider path.
        } catch (ReflectiveOperationException | LinkageError failure) {
            appeng.core.AELog.warn("[ae2lt] Useless Mod bigint API unavailable: %s", failure);
        }
    }

    static List<Target> targets(IGrid grid) {
        if (api == null || grid == null) return List.of();
        try {
            var result = new ArrayList<Target>();
            for (var target : (List<?>) api.findTargets.invoke(null, grid)) {
                if (api.grid.invoke(target) == grid)
                    result.add(new Target(api, target, (Set<?>) api.kinds.invoke(target)));
            }
            return result;
        } catch (ReflectiveOperationException failure) {
            return List.of(); // Discovery has not transferred any ownership.
        }
    }

    /** One dispatch loop only; matrix-only orders and waiting steps never enumerate grid nodes. */
    static final class Discovery {
        private final IGrid grid;
        private List<Target> found;

        Discovery(IGrid grid) { this.grid = grid; }

        List<Target> forPattern(IPatternDetails details) {
            if (api == null || !api.supportedKinds.contains(
                    BuiltInRegistries.ITEM.getKey(details.getDefinition().getItem()))) return List.of();
            if (found == null) found = targets(grid);
            return found;
        }
    }

    record Target(Api api, Object value, Set<?> kinds) {
        boolean accepts(IPatternDetails details) {
            return kinds.contains(BuiltInRegistries.ITEM.getKey(details.getDefinition().getItem()));
        }
    }

    static boolean dispatch(BigCraftingService service, BigCraftingJob job, BigProviderRecipe recipe,
                            IPatternDetails details, Target target, Runnable changed) {
        // The published API rejects reusable inputs; preserve the native shared-input contract.
        if (!recipe.sharedInputs().isEmpty()) return false;
        BigInteger requested = job.remainingStepCopies();
        for (var input : recipe.inputs().entrySet())
            requested = requested.min(job.escrow.getOrDefault(input.getKey(), BigInteger.ZERO)
                    .divide(BigInteger.valueOf(input.getValue())));
        if (requested.signum() <= 0) return false;
        try (var transaction = CraftingDispatch.exact((key, amount) -> {
            var received = job.receiveBig(key, amount, Actionable.MODULATE);
            job.retainOutput(key, amount.subtract(received));
            changed.run();
        }, (key, amount) -> {
            job.retainOutput(key, amount);
            changed.run();
        })) {
            return dispatch(service, job, recipe, details, target, changed, requested, transaction);
        }
    }

    private static boolean dispatch(BigCraftingService service, BigCraftingJob job, BigProviderRecipe recipe,
                            IPatternDetails details, Target target, Runnable changed, BigInteger requested,
                            CraftingDispatch transaction) {
        var prototype = recipe.template();
        var receipt = new Receipt(service, job, transaction);
        var api = target.api();
        try {
            Object batch;
            BigCraftingTransfer offered;
            try {
                var binding = api.binding.newInstance(ID, receipt);
                // admit performs the capacity check; reusing its exact array is part of the API.
                batch = api.admit.invoke(target.value(), details, prototype, requested, binding);
                if (batch == null) return false;
                var count = (BigInteger) api.count.invoke(batch);
                if (count.signum() <= 0 || count.compareTo(requested) > 0) return false;
                offered = BigCraftingTransfer.of(recipe, count);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return false;
            }
            job.reserve(offered);
            changed.run();
            try {
                boolean accepted = (boolean) api.commit.invoke(batch, (Object) prototype);
                job.settle(offered, accepted ? offered : BigCraftingTransfer.of(recipe, BigInteger.ZERO));
                changed.run();
                transaction.accounted();
                return accepted;
            } catch (ReflectiveOperationException | RuntimeException uncertain) {
                // A throwing commit may already own the prototype and batch. Never refund or retry it.
                job.failDispatch();
                changed.run();
                appeng.core.AELog.warn("[ae2lt] Exact order %s stopped after uncertain Omniversal commit: %s",
                        job.id, uncertain);
                return false;
            }
        } finally {
            // Admission failure and pre-commit exceptions must also release the strong job reference.
            receipt.finishDispatch();
        }
    }

    /** Only actual pre-network handover credits stock; notification callbacks are informational. */
    private static final class Receipt {
        private final WeakReference<BigCraftingService> service;
        private final UUID owner;
        private BigCraftingJob dispatchJob;
        private final CraftingDispatch transaction;

        Receipt(BigCraftingService service, BigCraftingJob job, CraftingDispatch transaction) {
            this.service = new WeakReference<>(service);
            owner = job.id;
            dispatchJob = job;
            this.transaction = transaction;
        }

        Map<AEKey, BigInteger> claim(Map<AEKey, BigInteger> outputs) {
            if (dispatchJob == null) {
                var current = service.get();
                return current == null ? Map.of() : current.claimOutputs(owner, outputs);
            }
            if (dispatchJob.returning) return Map.of();
            var accepted = new LinkedHashMap<AEKey, BigInteger>();
            outputs.forEach((key, amount) -> {
                var n = dispatchJob.waiting.getOrDefault(key, BigInteger.ZERO)
                        .subtract(transaction.staged(key)).min(amount).max(BigInteger.ZERO);
                if (n.signum() > 0 && transaction.enqueueExact(key, n)) {
                    accepted.put(key, n);
                }
            });
            return accepted;
        }

        void finishDispatch() {
            dispatchJob = null;
        }
    }

    /** Resolve only published types/methods, once. Kept package-visible for real-API regression tests. */
    static final class Api {
        final Method findTargets, grid, kinds, admit, count, commit, token, outputKey, outputAmount;
        final Constructor<?> binding;
        final Set<?> supportedKinds;
        private final Class<?> adapter;
        private final Method register;
        private Object adapterInstance;

        Api() throws ReflectiveOperationException {
            ClassLoader loader = UselessBigIntegerCompat.class.getClassLoader();
            var discovery = Class.forName(PREFIX + "AlloyFurnaceBigIntegerApi", false, loader);
            var target = Class.forName(PREFIX + "AlloyFurnaceBigIntegerTarget", false, loader);
            var batch = Class.forName(PREFIX + "AlloyFurnaceBigIntegerBatch", false, loader);
            var output = Class.forName(PREFIX + "AlloyFurnaceBigIntegerOutput", false, loader);
            var cpuBinding = Class.forName(PREFIX + "cpu.AlloyFurnaceBigIntegerCpuBinding", false, loader);
            var context = Class.forName(PREFIX + "cpu.AlloyFurnaceBigIntegerBatchContext", false, loader);
            adapter = Class.forName(PREFIX + "cpu.AlloyFurnaceBigIntegerCpuAdapter", false, loader);
            var registry = Class.forName(PREFIX + "cpu.AlloyFurnaceBigIntegerCpuAdapters", false, loader);
            findTargets = discovery.getMethod("findTargets", IGrid.class);
            supportedKinds = (Set<?>) discovery.getMethod("acceptedPatternKinds").invoke(null);
            grid = target.getMethod("grid");
            kinds = target.getMethod("acceptedPatternKinds");
            admit = target.getMethod("admit", IPatternDetails.class, KeyCounter[].class, BigInteger.class, cpuBinding);
            count = batch.getMethod("count");
            commit = batch.getMethod("commit", KeyCounter[].class);
            binding = cpuBinding.getConstructor(ResourceLocation.class, Object.class);
            token = context.getMethod("cpuToken");
            outputKey = output.getMethod("what");
            outputAmount = output.getMethod("amount");
            adapter.getMethod("claimOutputs", context, List.class);
            register = registry.getMethod("register", adapter);
        }

        void register() throws ReflectiveOperationException {
            adapterInstance = Proxy.newProxyInstance(adapter.getClassLoader(), new Class<?>[]{adapter},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "id" -> ID;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "AE2LT exact CPU";
                        case "claimOutputs" -> claim(args[0], (List<?>) args[1]);
                        default -> null; // Admitted/finished notifications never duplicate real receipts.
                    });
            register.invoke(null, adapterInstance);
        }

        private Map<AEKey, BigInteger> claim(Object context, List<?> outputs) {
            try {
                if (!(token.invoke(context) instanceof Receipt receipt)) return Map.of();
                var amounts = new LinkedHashMap<AEKey, BigInteger>();
                for (var output : outputs) {
                    var key = (AEKey) outputKey.invoke(output);
                    var amount = BigAmounts.nonNegative((BigInteger) outputAmount.invoke(output));
                    amounts.merge(key, amount, BigInteger::add);
                }
                return receipt.claim(amounts);
            } catch (ReflectiveOperationException | RuntimeException invalid) {
                appeng.core.AELog.warn("[ae2lt] Omniversal output handover rejected: %s", invalid);
                return Map.of();
            }
        }
    }
}
