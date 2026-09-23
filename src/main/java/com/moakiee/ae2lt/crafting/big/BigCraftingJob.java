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
        com.moakiee.ae2lt.recipe.compat.LegacyNbtUuid.put(tag, "id", id);
        tag.put("target", com.moakiee.ae2lt.recipe.compat.LegacyAeStackTags.writeKey(registries, target));
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
        if (tag.getIntOr("version", 0) != 1)
            throw new IllegalArgumentException("Unsupported exact crafting job version");
        var blocks = new ArrayList<BigExecutionProgram.Block<AEKey>>();
        for (var value : tag.getListOrEmpty("blocks")) {
            var b = (CompoundTag) value;
            var steps = new ArrayList<BigExecutionProgram.Step<AEKey>>();
            for (var entry : b.getListOrEmpty("steps")) {
                var s = (CompoundTag) entry;
                steps.add(
                        new BigExecutionProgram.Step<>(
                                BigMatrixRecipe.load(s.getCompoundOrEmpty("recipe"), registries).pattern(),
                                number(s, "copies")));
            }
            blocks.add(BigExecutionProgram.block(steps, number(b, "repetitions")));
        }
        var job =
                new BigCraftingJob(
                        com.moakiee.ae2lt.recipe.compat.LegacyNbtUuid.get(tag, "id"),
                        com.moakiee.ae2lt.recipe.compat.LegacyAeStackTags.readKey(registries, tag.getCompoundOrEmpty("target")),
                        number(tag, "amount"),
                        BigExecutionProgram.of(blocks));
        job.escrow.putAll(BigStackCodec.read(tag.getListOrEmpty("escrow"), registries));
        job.blockIndex = tag.getIntOr("block", 0);
        job.acceptedStep = tag.getIntOr("acceptedStep", 0);
        job.acceptedCopies = number(tag, "accepted");
        job.readyAt = tag.getLongOr("readyAt", 0L);
        job.returning = tag.getBooleanOr("returning", false);
        job.cancelled = tag.getBooleanOr("cancelled", false);
        job.suspended = tag.getBooleanOr("suspended", false);
        job.elapsedTicks = tag.getLongOr("elapsedTicks", 0L);
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
        return BigAmounts.nonNegative(new BigInteger(tag.getByteArray(name)
                .orElseThrow(() -> new IllegalArgumentException("Missing amount: " + name))));
    }
}
