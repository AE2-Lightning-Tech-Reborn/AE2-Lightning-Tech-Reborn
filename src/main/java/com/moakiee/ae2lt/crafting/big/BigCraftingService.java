package com.moakiee.ae2lt.crafting.big;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.crafting.runtime.CraftingExecutionState;
import com.moakiee.ae2lt.crafting.runtime.CraftingTickBudget;
import com.moakiee.thunderbolt.api.crafting.*;
import com.moakiee.thunderbolt.core.crafting.big.*;
import com.moakiee.thunderbolt.core.crafting.planner.PlanningCancellation;
import com.moakiee.thunderbolt.core.storage.big.*;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

/** Exact orders own their inputs; no legacy CPU or matrix buffer also owns these stacks. */
public final class BigCraftingService {
    private static final ExecutorService PLANNERS =
            new ThreadPoolExecutor(
                    1,
                    2,
                    30,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(8),
                    r -> {
                        var t = new Thread(r, "ae2lt-exact-planner");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.AbortPolicy());
    private final TianshuSupercomputerControllerBlockEntity host;
    private BigCraftingJob job;
    private BigCraftingCpu cpu;
    private boolean cpuListChanged;
    private final BigProviderDispatcher providerDispatcher = new BigProviderDispatcher();
    private final BigCraftingPatternLookup patternLookup = new BigCraftingPatternLookup();
    private long waitingChangedAt = Long.MIN_VALUE;
    private final CraftingExecutionState execution = new CraftingExecutionState();
    private final CraftingTickBudget budget = new CraftingTickBudget();

    public BigCraftingCpu cpu() {
        if (job == null) return null;
        if (cpu == null || cpu.job() != job) cpu = new BigCraftingCpu(this, job);
        return cpu;
    }

    public net.minecraft.network.chat.Component name() {
        return host.getBlockState().getBlock().getName();
    }

    public boolean consumeCpuListChanged() {
        boolean changed = cpuListChanged;
        cpuListChanged = false;
        return changed;
    }

    public void toggleSuspended() {
        if (job != null && !job.dispatchFailed) {
            job.suspended = !job.suspended;
            host.markCpuDirty();
        }
    }

    private String status = "idle";
    private long nextReturn;

    public BigCraftingService(TianshuSupercomputerControllerBlockEntity host) {
        this.host = host;
    }

    public BigCraftingJob job() {
        return job;
    }

    public String status() {
        return job == null ? status : job.status;
    }

    public boolean busy() {
        return job != null;
    }

    public boolean available() {
        return host.isCpuActive() && host.getCoreProfile().unboundedBatch();
    }

    public CompletableFuture<BigCraftingPlanner.Result<AEKey>> preview(
            AEKey target, BigInteger amount, IActionSource source) {
        if (!available()) throw new IllegalStateException("unavailable");
        return preview(host.getGrid(), host.getLevel(), target, amount, source);
    }

    /** Calculation/display remains available even when no exact CPU is currently online. */
    public static CompletableFuture<BigCraftingPlanner.Result<AEKey>> preview(
            appeng.api.networking.IGrid grid, net.minecraft.world.level.Level level,
            AEKey target, BigInteger amount, IActionSource source) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        var context =
                new PlanningAttemptContext() {
                    public long deadlineNanos() {
                        return deadline;
                    }

                    public void checkpoint() {
                        if (Thread.currentThread().isInterrupted()
                                || System.nanoTime() - deadline >= 0)
                            throw new CancellationException("budget");
                    }

                    public void report(PlanningDiagnosticSnapshot snapshot) {}
                };
        BigGraphExport export;
        try (var ignored = PlanningCancellation.bind(context)) {
            export = BigGraphExport.capture(grid, level, target, source);
        }
        return CompletableFuture.supplyAsync(
                () -> {
                    try (var ignored = PlanningCancellation.bind(context)) {
                        return BigCraftingPlanner.plan(
                                export.graph(), target, amount, export.stock());
                    }
                },
                PLANNERS);
    }

    public boolean submit(
            AEKey target,
            BigInteger amount,
            BigCraftingPlanner.Result<AEKey> plan,
            IActionSource source) {
        if (!available() || busy() || plan == null || !plan.executable()) return false;
        var matrices = BigGraphExport.matrices(host.getGrid());
        for (var block : plan.program().blocks())
            for (var step : block.steps()) {
                if (step.recipe().source() instanceof BigMatrixRecipe recipe) {
                    if (matrices.stream().noneMatch(m -> m.validateBigRecipe(recipe))) return false;
                } else if (!(step.recipe().source() instanceof BigCraftingRecipe recipe)
                        || recipe.resolve(host.getGrid().getCraftingService(), host.getLevel()) == null) return false;
            }
        var storage = host.getGrid().getStorageService().getInventory();
        for (var e : plan.program().required().entrySet())
            if (BigStorageOps.extract(
                                    storage, e.getKey(), e.getValue(), Actionable.SIMULATE, source)
                            .compareTo(e.getValue())
                    < 0) return false;
        job = new BigCraftingJob(target, amount, plan.program());
        status = "running";
        nextReturn = 0;
        cpuListChanged = true;
        try {
            for (var e : job.program.required().entrySet()) {
                var taken =
                        BigStorageOps.extract(
                                storage, e.getKey(), e.getValue(), Actionable.MODULATE, source);
                if (taken.signum() > 0) job.escrow.put(e.getKey(), taken);
                if (!taken.equals(e.getValue())) {
                    job.cancel();
                    return false;
                }
            }
            return true;
        } catch (RuntimeException failure) {
            job.cancel();
            throw failure;
        } finally {
            host.markCpuDirty();
            invalidate();
        }
    }

    public void cancel() {
        if (job != null) {
            job.cancel();
            changed();
            tick();
        }
    }

    public void tick() {
        if (job == null || host.getLevel() == null || !execution.tryEnter()) return;
        try {
            tickOrder();
        } finally {
            execution.leave();
        }
    }

    private void tickOrder() {
        if (!job.returning && (job.suspended || job.dispatchFailed)) return;
        long now = host.getLevel().getGameTime();
        if (budget.begin(now, host.getCoreProfile().successfulDispatchesPerTick(), Long.MAX_VALUE)) {
            job.elapsedTicks++;
        }
        if (!job.returning) {
            if (!available()) {
                job.status = "unavailable";
                return;
            }
            if (job.waitingForMaterials()) return;
            var matrices = new MatrixTargets();
            var exactTargets = new UselessBigIntegerCompat.Discovery(host.getGrid());
            while (!job.returning && !job.suspended && !job.dispatchFailed) {
                // Old saves may contain an already accepted timed matrix batch. Complete it once,
                // preserving its deadline; new matrix dispatches settle below in this same tick.
                if (job.readyAt >= 0) {
                    if (now < job.readyAt) return;
                    if (job.streamingBlock) job.commitMatrixStep();
                    else job.commitBlock();
                    changed();
                    continue;
                }
                if (job.streamingBlock && job.advanceStreaming()) {
                    changed();
                    continue;
                }
                if (budget.remainingOperations() <= 0) break;
                var block = job.current();
                if (job.streamingBlock || block.steps().stream()
                        .anyMatch(step -> !(step.recipe().source() instanceof BigMatrixRecipe))) {
                    job.beginStreaming();
                    if (!dispatchStreaming(matrices, exactTargets, now)) break;
                } else if (!dispatchMatrixBlock(matrices.get())) break;
            }
        }
        // A completed final dispatch delivers even when it consumed the last unit of work.
        if (job.returning && now >= nextReturn) {
            nextReturn = now + 5;
            returnEscrow();
        }
    }

    private boolean takeWork() {
        return budget.tryOperation();
    }

    private final class MatrixTargets {
        private List<com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity> found;

        List<com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity> get() {
            if (found == null) found = BigGraphExport.matrices(host.getGrid());
            return found;
        }
    }

    private boolean dispatchMatrixBlock(List<com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity> matrices) {
        var block = job.current();
        // Quantities stay compressed. Only actual provider calls consume the tick budget.
        while (job.acceptedStep < block.steps().size() && takeWork()) {
            var step = block.steps().get(job.acceptedStep);
            var recipe = (BigMatrixRecipe) step.recipe().source();
            var matrix = matrices.stream().filter(m -> m.validateBigRecipe(recipe)).findFirst().orElse(null);
            if (matrix == null) {
                job.status = "recipe_unavailable";
                return false;
            }
            var required = BigAmounts.nonNegative(step.copies().multiply(block.repetitions()));
            var received = matrix.acceptBigCrafting(recipe, required.subtract(job.acceptedCopies));
            if (received.signum() > 0) {
                job.acceptedCopies = job.acceptedCopies.add(received);
                changed();
            }
            if (job.acceptedCopies.compareTo(required) < 0) {
                job.status = "dispatch";
                return false;
            }
            job.acceptedCopies = BigInteger.ZERO;
            job.acceptedStep++;
            changed();
        }
        if (job.acceptedStep < block.steps().size()) return false;
        for (var step : block.steps())
            if (matrices.stream().noneMatch(m -> m.validateBigRecipe((BigMatrixRecipe) step.recipe().source()))) {
                job.status = "recipe_unavailable";
                return false;
            }
        job.commitBlock();
        changed();
        return true;
    }

    private boolean dispatchStreaming(MatrixTargets matrices,
                                      UselessBigIntegerCompat.Discovery exactTargets, long now) {
        if (job.remainingStepCopies().signum() == 0) {
            job.status = "waiting";
            return false;
        }
        var step = job.currentStep();
        if (step.recipe().source() instanceof BigMatrixRecipe recipe) {
            var required = BigExecutionProgram.repeat(BigExecutionProgram.arcs(step.recipe()), step.copies()).required();
            if (job.waitForMaterials(required)) {
                job.status = "waiting";
                return false;
            }
            if (!takeWork()) return false;
            var matrix = matrices.get().stream().filter(m -> m.validateBigRecipe(recipe)).findFirst().orElse(null);
            if (matrix == null) {
                job.status = "recipe_unavailable";
                return false;
            }
            var accepted = matrix.acceptBigCrafting(recipe, job.remainingStepCopies());
            if (accepted.signum() <= 0) return false;
            job.acceptedCopies = job.acceptedCopies.add(accepted);
            if (job.remainingStepCopies().signum() == 0) job.commitMatrixStep();
            changed();
            return true;
        }
        var recipe = (BigProviderRecipe) step.recipe().source();
        var oneCopy = new LinkedHashMap<AEKey, BigInteger>();
        recipe.inputs().forEach((key, count) -> oneCopy.put(key, BigInteger.valueOf(count)));
        if (job.waitForMaterials(oneCopy)) {
            job.status = "waiting";
            return false;
        }
        var crafting = host.getGrid().getCraftingService();
        var details = patternLookup.resolve(recipe, crafting, host.getLevel());
        if (details == null || !(crafting instanceof appeng.me.service.CraftingService nativeCrafting)) {
            job.status = "recipe_unavailable";
            return false;
        }
        if (recipe.sharedInputs().isEmpty()) {
            for (var target : exactTargets.forPattern(details)) {
                if (!target.accepts(details)) continue;
                if (!takeWork()) return false;
                if (UselessBigIntegerCompat.dispatch(this, job, recipe, details, target, this::changed)) return true;
                if (job.returning || job.dispatchFailed) return false;
            }
        }
        for (var provider : nativeCrafting.getProviders(details)) {
            if (!takeWork()) break;
            long received = providerDispatcher.dispatch(job, recipe, details, provider,
                    host.getLevel(), host.getGrid().getEnergyService(), now, this::changed);
            if (job.returning || job.dispatchFailed) return false;
            if (received > 0) return true;
        }
        job.status = job.waiting.isEmpty() ? "dispatch" : "waiting";
        return false;
    }

    /** Physical bulk receipts only; completion notifications must never credit inventory again. */
    Map<AEKey, BigInteger> claimOutputs(UUID owner, Map<AEKey, BigInteger> outputs) {
        if (job == null || !job.id.equals(owner) || job.returning || !available()) return Map.of();
        if (!execution.tryEnterDelivery()) return Map.of();
        try {
            var accepted = new LinkedHashMap<AEKey, BigInteger>();
            outputs.forEach((key, amount) -> {
                var received = job.receiveBig(key, amount, Actionable.MODULATE);
                if (received.signum() > 0) accepted.put(key, received);
            });
            if (!accepted.isEmpty()) changed();
            return accepted;
        } finally {
            execution.leaveDelivery();
        }
    }

    public void addWaitingKeys(Set<AEKey> keys) {
        if (job != null && !job.returning) keys.addAll(job.waiting.keySet());
    }

    public long requested(AEKey key) {
        return job == null || job.returning ? 0
                : BigAmounts.project(job.waiting.getOrDefault(key, BigInteger.ZERO));
    }

    public long insert(AEKey key, long amount, Actionable mode) {
        if (amount <= 0) return 0;
        return insertExact(key, BigInteger.valueOf(amount), mode).longValueExact();
    }

    public BigInteger insertExact(AEKey key, BigInteger amount, Actionable mode) {
        boolean ownsGuard = execution.tryEnter();
        try {
            if (job == null) return BigInteger.ZERO;
            var accepted = job.receiveBig(key, amount, mode);
            if (accepted.signum() > 0 && mode == Actionable.MODULATE) changed();
            return accepted;
        } finally {
            if (ownsGuard) execution.leave();
        }
    }

    public long waitingChangedAt() { return waitingChangedAt; }

    private void changed() {
        waitingChangedAt = appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
        host.markCpuDirty();
    }

    private void returnEscrow() {
        if (host.getGrid() == null) {
            job.status = "returning";
            return;
        }
        var storage = host.getGrid().getStorageService().getInventory();
        for (var e : new ArrayList<>(job.escrow.entrySet())) {
            var inserted =
                    BigStorageOps.insert(
                            storage,
                            e.getKey(),
                            e.getValue(),
                            Actionable.MODULATE,
                            host.getActionSource());
            if (inserted.signum() > 0) {
                var left = e.getValue().subtract(inserted);
                if (left.signum() == 0) job.escrow.remove(e.getKey());
                else job.escrow.put(e.getKey(), left);
                host.markCpuDirty();
            }
        }
        invalidate();
        if (job.escrow.isEmpty()) {
            status = job.cancelled ? "cancelled" : "complete";
            job = null;
            patternLookup.clear();
            cpuListChanged = true;
            host.markCpuDirty();
        } else job.status = "returning";
    }

    private void invalidate() {
        if (host.getGrid() != null) host.getGrid().getStorageService().invalidateCache();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        if (job != null) tag.put("job", job.save(registries));
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        patternLookup.clear();
        job = tag.contains("job") ? BigCraftingJob.load(tag.getCompound("job"), registries) : null;
        status = "idle";
        cpuListChanged = true;
        nextReturn = 0;
        waitingChangedAt = appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
    }
}
