package com.moakiee.ae2lt.crafting.big;

import appeng.api.stacks.AEKey;

import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;

import java.math.BigInteger;
import java.util.*;

/** CPU-owned escrow. A whole certified block commits atomically after its assembly delay. */
public final class BigCraftingJob {
    public final UUID id;
    public final AEKey target;
    public final BigInteger amount;
    public final BigExecutionProgram<AEKey> program;
    public final Map<AEKey, BigInteger> escrow = new LinkedHashMap<>();
    public int blockIndex;
    public int acceptedStep;
    public BigInteger acceptedCopies = BigInteger.ZERO;
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
        var block = current();
        for (var e : block.required().entrySet())
            if (escrow.getOrDefault(e.getKey(), BigInteger.ZERO).compareTo(e.getValue()) < 0)
                throw new IllegalStateException("Escrow cannot execute certified block");
        BigExecutionProgram.apply(escrow, block.delta(), BigInteger.ONE);
        blockIndex++;
        acceptedStep = 0;
        acceptedCopies = BigInteger.ZERO;
        readyAt = -1;
        returning = blockIndex == program.blocks().size();
    }

    public void cancel() {
        returning = true;
        cancelled = true;
        readyAt = -1;
        status = "returning";
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putUUID("id", id);
        tag.put("target", target.toTagGeneric());
        tag.putByteArray("amount", amount.toByteArray());
        tag.put("escrow", BigStackCodec.write(escrow, registries));
        tag.putInt("block", blockIndex);
        tag.putInt("acceptedStep", acceptedStep);
        tag.putByteArray("accepted", acceptedCopies.toByteArray());
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
                s.put("recipe", ((BigMatrixRecipe) step.recipe().source()).save(registries));
                steps.add(s);
            }
            b.put("steps", steps);
            blocks.add(b);
        }
        tag.put("blocks", blocks);
        return tag;
    }

    public static BigCraftingJob load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("version") != 1)
            throw new IllegalArgumentException("Unsupported exact crafting job version");
        var blocks = new ArrayList<BigExecutionProgram.Block<AEKey>>();
        for (var value : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            var b = (CompoundTag) value;
            var steps = new ArrayList<BigExecutionProgram.Step<AEKey>>();
            for (var entry : b.getList("steps", Tag.TAG_COMPOUND)) {
                var s = (CompoundTag) entry;
                steps.add(
                        new BigExecutionProgram.Step<>(
                                BigMatrixRecipe.load(s.getCompound("recipe"), registries).pattern(),
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
        job.blockIndex = tag.getInt("block");
        job.acceptedStep = tag.getInt("acceptedStep");
        job.acceptedCopies = number(tag, "accepted");
        job.readyAt = tag.getLong("readyAt");
        job.returning = tag.getBoolean("returning");
        job.cancelled = tag.getBoolean("cancelled");
        job.suspended = tag.getBoolean("suspended");
        job.elapsedTicks = tag.getLong("elapsedTicks");
        if (job.blockIndex < 0 || job.blockIndex > blocks.size())
            throw new IllegalArgumentException("Invalid exact job cursor");
        if (!job.returning
                && (job.blockIndex == blocks.size()
                        || job.acceptedStep < 0
                        || job.acceptedStep > job.current().steps().size()))
            throw new IllegalArgumentException("Invalid exact acceptance cursor");
        return job;
    }

    private static BigInteger number(CompoundTag tag, String name) {
        return BigAmounts.nonNegative(new BigInteger(tag.getByteArray(name)));
    }
}
