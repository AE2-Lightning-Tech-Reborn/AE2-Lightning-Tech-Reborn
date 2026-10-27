package com.moakiee.ae2lt.compat.gto;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.crafting.execution.*;
import appeng.me.service.CraftingService;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class GtoCpuJobAccessTest {
    @Test void remainingOutputUsesTheNativePersistedLedgerThroughTheDelegate() {
        var nativeLogic = new FakeLogic();
        var wrapper = new GtoCompatibleCraftingCpuLogic(null, nativeLogic);
        assertEquals(30, GtoCpuJobAccess.remaining(wrapper));
        wrapper.tickCraftingLogic(null, null);
        assertEquals(1, nativeLogic.ticks);
        wrapper.cancel();
        assertEquals(0, GtoCpuJobAccess.remaining(wrapper));
        assertSame(nativeLogic, wrapper.nativeDelegate());
    }

    private static class FakeLogic extends CraftingCpuLogic {
        boolean active = true;
        int ticks;
        FakeLogic() { super(null); }
        public ICraftingSubmitResult trySubmitJob(IGrid g, ICraftingPlan p, IActionSource s, ICraftingRequester r) {
            return CraftingSubmitResult.NO_CPU_FOUND;
        }
        public void tickCraftingLogic(IEnergyService e, CraftingService c) { ticks++; }
        public long insert(AEKey k, long n, Actionable mode) { return 0; }
        public void cancel() { active = false; }
        protected void postChange(AEKey k) {}
        public boolean hasJob() { return active; }
        public GenericStack getFinalJobOutput() { return null; }
        public ElapsedTimeTracker getElapsedTimeTracker() { return null; }
        public ICraftingLink getLastLink() { return null; }
        public void readFromNBT(CompoundTag tag) {}
        public void writeToNBT(CompoundTag tag) {
            var job = new CompoundTag(); job.putLong("remainingAmount", 30); tag.put("job", job);
        }
        public void addListener(Consumer<AEKey> l) {}
        public void removeListener(Consumer<AEKey> l) {}
        public long getWaitingFor(AEKey k) { return 0; }
        public void getAllWaitingFor(Set<AEKey> k) {}
        public long getPendingOutputs(AEKey k) { return 0; }
        public void getAllItems(KeyCounter k) {}
    }
}
