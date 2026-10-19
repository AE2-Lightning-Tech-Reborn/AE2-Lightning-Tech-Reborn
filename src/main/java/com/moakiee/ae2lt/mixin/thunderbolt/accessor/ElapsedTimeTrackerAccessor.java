package com.moakiee.ae2lt.mixin.thunderbolt.accessor;
import org.spongepowered.asm.mixin.*;
import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;
import com.moakiee.ae2lt.compat.LegacyTimeTracker;

@Mixin(value = ElapsedTimeTracker.class, remap = false)
public abstract class ElapsedTimeTrackerAccessor implements LegacyTimeTracker {
    @Shadow @Final @Mutable private long startItemCount;
    @Shadow private long remainingItemCount;
    @Shadow protected abstract void decrementItems(long amount);
    public void ae2lt$decrementItems(long amount, AEKeyType type) { decrementItems(amount); }
    public void ae2lt$addMaxItems(long amount, AEKeyType type) {
        if (amount <= 0) return;
        startItemCount = startItemCount > Long.MAX_VALUE - amount ? Long.MAX_VALUE : startItemCount + amount;
        remainingItemCount = remainingItemCount > Long.MAX_VALUE - amount ? Long.MAX_VALUE : remainingItemCount + amount;
    }
}
