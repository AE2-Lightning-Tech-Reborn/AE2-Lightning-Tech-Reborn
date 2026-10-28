package com.moakiee.ae2lt.mixin;

import java.util.Map;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import com.moakiee.ae2lt.compat.gto.GtoCompatibleCraftingCpuLogic;
import org.spongepowered.asm.mixin.Mixin;

/** The optional interface is added by the plugin only for the two supported EAEP releases. */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class GtoEaepManualStateMixin {
    public void eap$setManualWaiting(KeyCounter missing) {
        if ((Object) this instanceof GtoCompatibleCraftingCpuLogic compatible) compatible.setEaepManualWaiting(missing);
    }
    public long eap$getManualWaitingAmount(AEKey key) { return eap$getManualWaitingSnapshot().getOrDefault(key, 0L); }
    public Map<AEKey, Long> eap$getManualWaitingSnapshot() {
        return (Object) this instanceof GtoCompatibleCraftingCpuLogic compatible
                ? compatible.eaepManualWaitingSnapshot() : Map.of();
    }
}
