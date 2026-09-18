package com.moakiee.ae2lt.crafting.big;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
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

    public BigCraftingCpu cpu() {
        if (job == null) return null;
        if (cpu == null || cpu.job() != job) cpu = new BigCraftingCpu(this, job);
        return cpu;
    }

    public net.minecraft.network.chat.Component name() {
        return host.getDisplayName();
    }

    public boolean consumeCpuListChanged() {
        boolean changed = cpuListChanged;
        cpuListChanged = false;
        return changed;
    }

    public void toggleSuspended() {
        if (job != null) {
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
            export = BigGraphExport.capture(host.getGrid(), host.getLevel(), target, source);
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
                if (!(step.recipe().source() instanceof BigMatrixRecipe recipe)
                        || matrices.stream().noneMatch(m -> m.validateBigRecipe(recipe)))
                    return false;
            }
        var storage = host.getGrid().getStorageService().getInventory();
        for (var e : plan.program().required().entrySet())
            if (BigStorageOps.extract(
                                    storage, e.getKey(), e.getValue(), Actionable.SIMULATE, source)
                            .compareTo(e.getValue())
                    < 0) return false;
        job = new BigCraftingJob(target, amount, plan.program());
        status = "running";
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
            host.markCpuDirty();
            tick();
        }
    }

    public void tick() {
        if (job == null || host.getLevel() == null) return;
        if (!job.returning && job.suspended) return;
        job.elapsedTicks++;
        long now = host.getLevel().getGameTime();
        if (job.returning) {
            if (now >= nextReturn) {
                nextReturn = now + 5;
                returnEscrow();
            }
            return;
        }
        if (!available()) {
            job.status = "unavailable";
            return;
        }
        if (job.readyAt >= 0) {
            if (now >= job.readyAt) {
                job.commitBlock();
                host.markCpuDirty();
            }
            return;
        }
        var matrices = BigGraphExport.matrices(host.getGrid());
        var block = job.current();
        // One certificate can represent arbitrarily many alternating firings. Validation/dispatch
        // work is bounded by recipe steps and provider-call quotas, never by the quantity.
        int calls = 0;
        while (job.acceptedStep < block.steps().size() && calls++ < 128) {
            var step = block.steps().get(job.acceptedStep);
            var recipe = (BigMatrixRecipe) step.recipe().source();
            var matrix =
                    matrices.stream()
                            .filter(m -> m.validateBigRecipe(recipe))
                            .findFirst()
                            .orElse(null);
            if (matrix == null) {
                job.status = "recipe_unavailable";
                return;
            }
            var required = BigAmounts.nonNegative(step.copies().multiply(block.repetitions()));
            var received = matrix.acceptBigCrafting(recipe, required.subtract(job.acceptedCopies));
            if (received.signum() > 0) {
                job.acceptedCopies = job.acceptedCopies.add(received);
                host.markCpuDirty();
            }
            if (job.acceptedCopies.compareTo(required) < 0) {
                job.status = "dispatch";
                return;
            }
            job.acceptedCopies = BigInteger.ZERO;
            job.acceptedStep++;
            host.markCpuDirty();
        }
        if (job.acceptedStep == block.steps().size()) {
            // Recheck every member before accepting the atomic compressed batch.
            for (var step : block.steps())
                if (matrices.stream()
                        .noneMatch(
                                m ->
                                        m.validateBigRecipe(
                                                (BigMatrixRecipe) step.recipe().source()))) {
                    job.status = "recipe_unavailable";
                    return;
                }
            job.readyAt = now + 5;
            job.status = "running";
            host.markCpuDirty();
        }
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
        job = tag.contains("job") ? BigCraftingJob.load(tag.getCompound("job"), registries) : null;
        status = "idle";
        cpuListChanged = true;
        nextReturn = 0;
    }
}
