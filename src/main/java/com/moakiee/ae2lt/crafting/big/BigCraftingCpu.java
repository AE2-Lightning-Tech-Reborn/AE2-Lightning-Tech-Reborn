package com.moakiee.ae2lt.crafting.big;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.GenericStack;

import com.moakiee.thunderbolt.core.storage.big.BigAmounts;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** A normal task row in AE2's CPU monitor, backed by one exact escrow. */
public final class BigCraftingCpu implements ICraftingCPU, ICraftingLink {
    private final BigCraftingService service;
    private final BigCraftingJob job;

    BigCraftingCpu(BigCraftingService service, BigCraftingJob job) {
        this.service = service;
        this.job = job;
    }

    public BigCraftingJob job() {
        return job;
    }

    public boolean isBusy() {
        return service.job() == job;
    }

    public CraftingJobStatus getJobStatus() {
        return isBusy()
                ? new CraftingJobStatus(
                        BigDisplayAmounts.attach(
                                new GenericStack(job.target, BigAmounts.project(job.amount)),
                                job.amount),
                        job.program.blocks().size(),
                        job.blockIndex,
                        job.elapsedTicks * 50_000_000L)
                : null;
    }

    public void cancelJob() {
        if (isBusy()) service.cancel();
    }

    public long getAvailableStorage() {
        return Long.MAX_VALUE;
    }

    public int getCoProcessors() {
        return Integer.MAX_VALUE;
    }

    public Component getName() {
        return service.name();
    }

    public CpuSelectionMode getSelectionMode() {
        return CpuSelectionMode.PLAYER_ONLY;
    }

    public void toggleSuspended() {
        if (isBusy()) service.toggleSuspended();
    }

    public boolean isCanceled() {
        return job.cancelled;
    }

    public boolean isDone() {
        return !isBusy() && !job.cancelled;
    }

    public void cancel() {
        cancelJob();
    }

    public boolean isStandalone() {
        return true;
    }

    @Override
    public void writeToNBT(net.minecraft.world.level.storage.ValueOutput output) {
        output.putIntArray("CraftID", net.minecraft.core.UUIDUtil.uuidToIntArray(job.id));
        output.putBoolean("standalone", true);
        output.putBoolean("canceled", isCanceled());
        output.putBoolean("done", isDone());
    }

    public UUID getCraftingID() {
        return job.id;
    }
}
