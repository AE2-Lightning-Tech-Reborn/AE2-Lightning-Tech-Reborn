package com.moakiee.ae2lt.api.crafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Stable, implementation-neutral integration surface for external crafting engines. */
public final class Ae2LtCraftingIntegration {
    private Ae2LtCraftingIntegration() {}

    public static OverloadRegistration prepareOverload(Object cpuLogic, IPatternDetails pattern,
            UUID craftingId, @Nullable AEKey finalOutput) {
        if (!(pattern instanceof OverloadedProviderOnlyPatternDetails overload)) {
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

    public static long insertCpuOutput(ICraftingCPU cpu, UUID craftingId, AEKey key,
            long amount, Actionable mode) {
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
