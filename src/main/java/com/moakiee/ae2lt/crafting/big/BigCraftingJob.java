package com.moakiee.ae2lt.crafting.big;

import appeng.api.stacks.AEKey;

import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import com.moakiee.thunderbolt.core.crafting.big.BigLongSlice;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;

import java.math.BigInteger;
import java.util.*;

/** CPU-owned exact escrow, including real in-flight work at legacy providers. */
public final class BigCraftingJob {
    public final UUID id;
    public final AEKey target;
    public final BigInteger amount;
    public final BigExecutionProgram<AEKey> program;
    public final Map<AEKey, BigInteger> escrow = new LinkedHashMap<>();
    public final Map<AEKey, BigInteger> waiting = new LinkedHashMap<>();
    public int blockIndex;
    public int acceptedStep;
    public BigInteger acceptedCopies = BigInteger.ZERO;
    public BigInteger completedRepetitions = BigInteger.ZERO;
    public boolean streamingBlock;
    public boolean dispatchFailed;
    public long readyAt = -1;
    public boolean returning;
    public boolean cancelled;
    public boolean suspended;
    public long elapsedTicks;
    public String status = "running";

    public BigCraftingJob(AEKey target, BigInteger amount, BigExecutionProgram<AEKey> program) {
        this(UUID.randomUUID(), target, amount, program);
    }

    private BigCraftingJob(
            UUID id, AEKey target, BigInteger amount, BigExecutionProgram<AEKey> program) {
        this.id = id;
        this.target = Objects.requireNonNull(target);
        this.amount = BigAmounts.nonNegative(amount);
        this.program = BigExecutionProgram.of(program.blocks());
    }

    public BigExecutionProgram.Block<AEKey> current() {
        return program.blocks().get(blockIndex);
    }

    public void commitBlock() {
        if (streamingBlock) throw new IllegalStateException("Physical work must receive its outputs");
        var block = current();
        for (var e : block.required().entrySet())
            if (escrow.getOrDefault(e.getKey(), BigInteger.ZERO).compareTo(e.getValue()) < 0)
                throw new IllegalStateException("Escrow cannot execute certified block");
        BigExecutionProgram.apply(escrow, block.delta(), BigInteger.ONE);
        finishBlock();
    }

    private void finishBlock() {
        if (!waiting.isEmpty()) throw new IllegalStateException("Unreceived physical outputs");
        blockIndex++;
        acceptedStep = 0;
        acceptedCopies = BigInteger.ZERO;
        completedRepetitions = BigInteger.ZERO;
        streamingBlock = false;
        readyAt = -1;
        returning = blockIndex == program.blocks().size();
    }

    public BigExecutionProgram.Step<AEKey> currentStep() {
        return current().steps().get(acceptedStep);
    }

    public BigInteger remainingStepCopies() {
        return currentStep().copies().subtract(acceptedCopies);
    }

    public void beginStreaming() {
        if (streamingBlock) return;
        if (acceptedStep != 0 || acceptedCopies.signum() != 0 || readyAt >= 0)
            throw new IllegalStateException("Atomic matrix dispatch already started");
        streamingBlock = true;
    }

    /** Register ownership before invoking a provider, so synchronous returns are also real receipts. */
    public void reserve(BigLongSlice<AEKey> slice) {
        if (!streamingBlock || returning || dispatchFailed || slice.copies() <= 0
                || BigInteger.valueOf(slice.copies()).compareTo(remainingStepCopies()) > 0)
            throw new IllegalStateException("Invalid physical dispatch");
        var inventory = new LinkedHashMap<AEKey, BigInteger>();
        slice.inputs().forEach((key, n) -> inventory.put(key,
                BigAmounts.nonNegative(escrow.getOrDefault(key, BigInteger.ZERO).subtract(n))));
        var expected = new LinkedHashMap<AEKey, BigInteger>();
        slice.outputs().forEach((key, n) -> expected.put(key,
                BigAmounts.nonNegative(waiting.getOrDefault(key, BigInteger.ZERO).add(n))));
        inventory.forEach((key, n) -> put(escrow, key, n));
        expected.forEach(waiting::put);
    }

    /** Refund only confirmed rejections; an indeterminate provider result must never be retried. */
    public void settle(BigLongSlice<AEKey> offered, BigLongSlice<AEKey> accepted) {
        if (accepted.copies() < 0 || accepted.copies() > offered.copies())
            throw new IllegalArgumentException("Invalid accepted copies");
        var expected = new LinkedHashMap<AEKey, BigInteger>();
        offered.outputs().forEach((key, n) -> {
            var rejected = n.subtract(accepted.outputs().getOrDefault(key, BigInteger.ZERO));
            if (rejected.signum() < 0) throw new IllegalArgumentException("Invalid accepted outputs");
            expected.put(key, BigAmounts.nonNegative(
                    waiting.getOrDefault(key, BigInteger.ZERO).subtract(rejected)));
        });
        var inventory = new LinkedHashMap<AEKey, BigInteger>();
        offered.inputs().forEach((key, n) -> {
            var rejected = n.subtract(accepted.inputs().getOrDefault(key, BigInteger.ZERO));
            if (rejected.signum() < 0) throw new IllegalArgumentException("Invalid accepted inputs");
            inventory.put(key, BigAmounts.nonNegative(escrow.getOrDefault(key, BigInteger.ZERO).add(rejected)));
        });
        var copies = BigAmounts.nonNegative(acceptedCopies.add(BigInteger.valueOf(accepted.copies())));
        if (copies.compareTo(currentStep().copies()) > 0)
            throw new IllegalStateException("Dispatched more than the exact order");
        expected.forEach((key, n) -> put(waiting, key, n));
        inventory.forEach((key, n) -> put(escrow, key, n));
        acceptedCopies = copies;
    }

    public long receive(AEKey key, long amount, appeng.api.config.Actionable mode) {
        if (returning || amount <= 0) return 0;
        var requested = waiting.getOrDefault(key, BigInteger.ZERO);
        var existing = escrow.getOrDefault(key, BigInteger.ZERO);
        var room = BigInteger.ONE.shiftLeft(BigAmounts.MAX_BITS).subtract(BigInteger.ONE).subtract(existing);
        var accepted = requested.min(BigInteger.valueOf(amount)).min(room).max(BigInteger.ZERO);
        if (mode == appeng.api.config.Actionable.MODULATE && accepted.signum() > 0) {
            put(waiting, key, requested.subtract(accepted));
            escrow.put(key, existing.add(accepted));
        }
        return accepted.longValueExact();
    }

    public boolean advanceStreaming() {
        if (!streamingBlock || !waiting.isEmpty() || readyAt >= 0
                || acceptedCopies.compareTo(currentStep().copies()) != 0) return false;
        acceptedCopies = BigInteger.ZERO;
        acceptedStep++;
        if (acceptedStep == current().steps().size()) {
            acceptedStep = 0;
            completedRepetitions = completedRepetitions.add(BigInteger.ONE);
            if (completedRepetitions.equals(current().repetitions())) finishBlock();
        }
        return true;
    }

    /** Only a matrix with verified assembly may settle without physical return packets. */
    public void commitMatrixStep() {
        if (!streamingBlock || !(currentStep().recipe().source() instanceof BigMatrixRecipe)
                || !acceptedCopies.equals(currentStep().copies()) || !waiting.isEmpty())
            throw new IllegalStateException("Invalid exact matrix step");
        var summary = BigExecutionProgram.repeat(BigExecutionProgram.arcs(currentStep().recipe()), acceptedCopies);
        for (var entry : summary.required().entrySet())
            if (escrow.getOrDefault(entry.getKey(), BigInteger.ZERO).compareTo(entry.getValue()) < 0)
                throw new IllegalStateException("Matrix step is not funded");
        BigExecutionProgram.apply(escrow, summary.delta(), BigInteger.ONE);
        readyAt = -1;
        advanceStreaming();
    }

    public void failDispatch() {
        dispatchFailed = true;
        suspended = true;
        status = "dispatch_failed";
    }

    private static void put(Map<AEKey, BigInteger> values, AEKey key, BigInteger n) {
        if (n.signum() == 0) values.remove(key);
        else values.put(key, n);
    }

    public void cancel() {
        returning = true;
        cancelled = true;
        readyAt = -1;
        // Inputs already handed to a machine are never refunded as if they were still ours.
        // Its later outputs return to network storage through the normal insertion path.
        waiting.clear();
        status = "returning";
    }

    public CompoundTag save(RegistryAccess registries) {
        var tag = new CompoundTag();
        tag.putInt("version", 2);
        tag.putUUID("id", id);
        tag.put("target", target.toTagGeneric());
        tag.putByteArray("amount", amount.toByteArray());
        tag.put("escrow", BigStackCodec.write(escrow, registries));
        tag.put("waiting", BigStackCodec.write(waiting, registries));
        tag.putInt("block", blockIndex);
        tag.putInt("acceptedStep", acceptedStep);
        tag.putByteArray("accepted", acceptedCopies.toByteArray());
        tag.putByteArray("completedRepetitions", completedRepetitions.toByteArray());
        tag.putBoolean("streamingBlock", streamingBlock);
        tag.putBoolean("dispatchFailed", dispatchFailed);
        tag.putLong("readyAt", readyAt);
        tag.putBoolean("returning", returning);
        tag.putBoolean("cancelled", cancelled);
        tag.putBoolean("suspended", suspended);
        tag.putLong("elapsedTicks", elapsedTicks);
        var blocks = new ListTag();
        for (var block : program.blocks()) {
            var b = new CompoundTag();
            b.putByteArray("repetitions", block.repetitions().toByteArray());
            var steps = new ListTag();
            for (var step : block.steps()) {
                var s = new CompoundTag();
                s.putByteArray("copies", step.copies().toByteArray());
                s.put("recipe", ((BigCraftingRecipe) step.recipe().source()).save(registries));
                steps.add(s);
            }
            b.put("steps", steps);
            blocks.add(b);
        }
        tag.put("blocks", blocks);
        return tag;
    }

    public static BigCraftingJob load(CompoundTag tag, RegistryAccess registries) {
        int version = tag.getInt("version");
        if (version != 1 && version != 2)
            throw new IllegalArgumentException("Unsupported exact crafting job version");
        var blocks = new ArrayList<BigExecutionProgram.Block<AEKey>>();
        for (var value : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            var b = (CompoundTag) value;
            var steps = new ArrayList<BigExecutionProgram.Step<AEKey>>();
            for (var entry : b.getList("steps", Tag.TAG_COMPOUND)) {
                var s = (CompoundTag) entry;
                steps.add(
                        new BigExecutionProgram.Step<>(
                                BigCraftingRecipe.load(s.getCompound("recipe"), registries).pattern(),
                                number(s, "copies")));
            }
            blocks.add(BigExecutionProgram.block(steps, number(b, "repetitions")));
        }
        var job =
                new BigCraftingJob(
                        tag.getUUID("id"),
                        AEKey.fromTagGeneric(tag.getCompound("target")),
                        number(tag, "amount"),
                        BigExecutionProgram.of(blocks));
        job.escrow.putAll(BigStackCodec.read(tag.getList("escrow", Tag.TAG_COMPOUND), registries));
        job.waiting.putAll(BigStackCodec.read(tag.getList("waiting", Tag.TAG_COMPOUND), registries));
        job.blockIndex = tag.getInt("block");
        job.acceptedStep = tag.getInt("acceptedStep");
        job.acceptedCopies = number(tag, "accepted");
        if (version >= 2) {
            job.completedRepetitions = number(tag, "completedRepetitions");
            job.streamingBlock = tag.getBoolean("streamingBlock");
            job.dispatchFailed = tag.getBoolean("dispatchFailed");
        }
        job.readyAt = tag.getLong("readyAt");
        job.returning = tag.getBoolean("returning");
        job.cancelled = tag.getBoolean("cancelled");
        job.suspended = tag.getBoolean("suspended");
        job.elapsedTicks = tag.getLong("elapsedTicks");
        if (job.dispatchFailed && !job.returning) job.status = "dispatch_failed";
        if (job.blockIndex < 0 || job.blockIndex > blocks.size())
            throw new IllegalArgumentException("Invalid exact job cursor");
        if (!job.returning
                && (job.blockIndex == blocks.size()
                        || job.acceptedStep < 0
                        || job.acceptedStep > job.current().steps().size()))
            throw new IllegalArgumentException("Invalid exact acceptance cursor");
        if (!job.returning && job.streamingBlock
                && (job.acceptedStep == job.current().steps().size()
                    || job.completedRepetitions.compareTo(job.current().repetitions()) >= 0
                    || job.acceptedCopies.compareTo(job.currentStep().copies()) > 0))
            throw new IllegalArgumentException("Invalid physical dispatch cursor");
        return job;
    }

    private static BigInteger number(CompoundTag tag, String name) {
        return BigAmounts.nonNegative(new BigInteger(tag.getByteArray(name)));
    }
}
