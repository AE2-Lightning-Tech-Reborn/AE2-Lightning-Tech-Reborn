package com.moakiee.ae2lt.api.crafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuInsertSupport;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadClaimResult;
import com.moakiee.ae2lt.overload.runtime.cpu.PendingOverloadClaim;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadConsumerCredit;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/** Stable, implementation-neutral integration surface for external crafting engines. */
public final class Ae2LtCraftingIntegration {
    private Ae2LtCraftingIntegration() {}

    public static OverloadRegistration prepareOverload(Object cpuLogic, IPatternDetails pattern,
            UUID craftingId, @Nullable AEKey finalOutput) {
        var providerPattern = CraftingPatternDelegates.forProviderLookup(pattern);
        if (!(providerPattern instanceof OverloadedProviderOnlyPatternDetails overload)) {
            return OverloadRegistration.ordinary();
        }
        var details = overload.overloadPatternDetailsView();
        var reference = new OverloadPatternReference(overload.overloadPatternIdentity(), details.sourcePattern());
        if (OverloadCpuStateManager.INSTANCE.hasAmbiguousOutputRegistration(cpuLogic, reference, details)) {
            return OverloadRegistration.blocked();
        }
        return new OverloadRegistration(true, cpuLogic, craftingId, reference, details,
                java.util.Arrays.asList(pattern.getOutputs()), finalOutput);
    }

    /** Also validates collisions against an external CPU's exact waiting ledger before inputs move. */
    public static OverloadRegistration prepareOverload(Object cpuLogic, IPatternDetails pattern,
            UUID craftingId, @Nullable AEKey finalOutput, KeyCounter waiting) {
        var providerPattern = CraftingPatternDelegates.forProviderLookup(pattern);
        if (providerPattern instanceof OverloadedProviderOnlyPatternDetails overload) {
            if (OverloadCpuInsertSupport.hasStrictCollisionWithOverloadPattern(
                    cpuLogic, pattern, overload, waiting)) return OverloadRegistration.blocked();
        } else if (OverloadCpuInsertSupport.hasPendingCollisionWithOrdinaryPattern(cpuLogic, pattern)) {
            return OverloadRegistration.blocked();
        }
        return prepareOverload(cpuLogic, pattern, craftingId, finalOutput);
    }

    /** Keeps exact ID_ONLY templates out of ordinary matching so both ledgers retire together. */
    public static long strictOutputMatch(Object owner, AEKey actual, long rawMatch, long exactWaiting) {
        return OverloadCpuStateManager.INSTANCE.hasExactPending(owner, actual)
                ? OverloadCpuInsertSupport.nativeStrictMatch(owner, actual, rawMatch, exactWaiting) : rawMatch;
    }

    /**
     * Claims only registered outputs of this job. The CPU callback receives the reserved expected
     * key; the actual key is supplied by the caller. SIMULATE is pure. MODULATE must accept the
     * probed amount and retain any delivery remainder before invoking other callbacks.
     * Pending state is retired before physical delivery, preventing reentrant duplicate claims.
     */
    public static long claimOverloadOutput(Object owner, UUID craftingId, AEKey actual, long amount,
            Actionable mode, BiFunction<GenericStack, Actionable, Long> receiver) {
        var states = OverloadCpuStateManager.INSTANCE;
        var preview = states.claim(owner, actual, amount, Actionable.SIMULATE);
        long accepted = 0;
        for (var claim : preview.claims()) {
            if (!craftingId.equals(claim.key().craftingId())) continue;
            long limit = receiver.apply(new GenericStack(claim.exactExpectedKey(), claim.claimedAmount()),
                    Actionable.SIMULATE);
            if (limit < 0 || limit > claim.claimedAmount())
                throw new IllegalStateException("Invalid external CPU claim preview");
            if (limit == 0) continue;
            if (mode == Actionable.SIMULATE) {
                accepted = Math.addExact(accepted, limit);
                continue;
            }
            var credits = OverloadConsumerCredit.limit(claim.consumerCredits(), limit);
            var limited = new PendingOverloadClaim(claim.key(), limit, claim.routesToRequester(),
                    Math.min(claim.requesterAmount(), limit - OverloadConsumerCredit.total(credits)),
                    claim.exactExpectedKey(), credits, claim.sharedReusableSeedPool());
            var committed = states.commitPreview(owner, new OverloadClaimResult(limit, List.of(limited)));
            if (!committed.claimedAnything()) continue;
            long received = receiver.apply(new GenericStack(claim.exactExpectedKey(), committed.claimedAmount()), mode);
            if (received != committed.claimedAmount())
                throw new IllegalStateException("External CPU changed an accepted output claim");
            accepted = Math.addExact(accepted, received);
        }
        return accepted;
    }

    /** Public per-job persistence; external engines do not inspect implementation fields. */
    public static CompoundTag saveOverloadState(Object owner, UUID craftingId, HolderLookup.Provider registries) {
        java.util.Objects.requireNonNull(registries, "registries");
        return saveOverloadState(owner, craftingId);
    }

    /** Forge 1.20.1 NBT uses the built-in registries; NeoECO's public attachment API calls this overload. */
    public static CompoundTag saveOverloadState(Object owner, UUID craftingId) {
        for (var pending : OverloadCpuStateManager.INSTANCE.snapshotPending(owner)) {
            if (!craftingId.equals(pending.key().craftingId()))
                throw new IllegalStateException("Overload state belongs to another crafting job");
        }
        var result = new CompoundTag();
        result.putUUID("craftingId", craftingId);
        var state = OverloadCpuStateManager.INSTANCE.writeToTag(owner);
        if (state != null) result.put("state", state);
        return result;
    }

    public static void loadOverloadState(Object owner, UUID craftingId, CompoundTag data,
            HolderLookup.Provider registries) {
        java.util.Objects.requireNonNull(registries, "registries");
        loadOverloadState(owner, craftingId, data);
    }

    public static void loadOverloadState(Object owner, UUID craftingId, CompoundTag data) {
        if (data.hasUUID("craftingId") && !craftingId.equals(data.getUUID("craftingId")))
            throw new IllegalArgumentException("Overload checkpoint belongs to another crafting job");
        OverloadCpuStateManager.INSTANCE.readFromTag(owner, craftingId, data.getCompound("state"));
    }

    public static void clearOverloadState(Object owner) {
        OverloadCpuStateManager.INSTANCE.clear(owner);
    }

    public static long insertCpuOutput(ICraftingCPU cpu, UUID craftingId, AEKey key,
            long amount, Actionable mode) {
        if (amount <= 0L) return 0L;
        if (cpu instanceof com.moakiee.ae2lt.crafting.big.BigCraftingCpu big) {
            return big.insertForJob(craftingId, key, amount, mode);
        }
        if (!(cpu instanceof TimeWheelCraftingCPU timeWheel)) return 0L;
        var logic = timeWheel.getCraftingLogic();
        var link = logic.getLastLink();
        if (link == null || !craftingId.equals(link.getCraftingID())) return 0L;
        long before = logic.getWaitingFor(key);
        long offered = Math.min(amount, before);
        if (offered <= 0L) return 0L;
        long inserted = logic.insert(key, offered, mode);
        if (mode == Actionable.MODULATE && inserted < offered
                && before - logic.getWaitingFor(key) >= offered) {
            logic.getInventory().insert(key, offered - inserted, mode);
            return offered;
        }
        return inserted;
    }

    public static java.math.BigInteger insertExactCpuOutput(ICraftingCPU cpu, UUID craftingId,
            AEKey key, java.math.BigInteger amount, Actionable mode) {
        return cpu instanceof com.moakiee.ae2lt.crafting.big.BigCraftingCpu big
                ? big.insertExactForJob(craftingId, key, amount, mode) : java.math.BigInteger.ZERO;
    }

    public static final class OverloadRegistration {
        private final boolean canDispatch;
        private final Object cpuLogic;
        private final UUID craftingId;
        private final OverloadPatternReference reference;
        private final com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternDetails details;
        private final List<GenericStack> outputs;
        private final AEKey finalOutput;

        private OverloadRegistration(boolean canDispatch, @Nullable Object cpuLogic,
                @Nullable UUID craftingId, @Nullable OverloadPatternReference reference,
                @Nullable com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternDetails details,
                @Nullable List<GenericStack> outputs, @Nullable AEKey finalOutput) {
            this.canDispatch = canDispatch;
            this.cpuLogic = cpuLogic;
            this.craftingId = craftingId;
            this.reference = reference;
            this.details = details;
            this.outputs = outputs;
            this.finalOutput = finalOutput;
        }

        public boolean canDispatch() {
            return canDispatch;
        }

        private static OverloadRegistration ordinary() {
            return new OverloadRegistration(true, null, null, null, null, null, null);
        }

        private static OverloadRegistration blocked() {
            return new OverloadRegistration(false, null, null, null, null, null, null);
        }

        public boolean registerAccepted(long acceptedCrafts) {
            if (!canDispatch || acceptedCrafts <= 0L) return false;
            if (cpuLogic == null || craftingId == null || reference == null || details == null) return true;
            OverloadCpuStateManager.INSTANCE.registerExpectedOutputs(cpuLogic, craftingId, reference, details,
                    outputs == null ? List.of() : outputs, finalOutput, acceptedCrafts, Map.of());
            return true;
        }
    }
}
