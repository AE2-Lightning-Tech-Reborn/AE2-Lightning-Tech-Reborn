package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** One server-menu receipt. Undo returns a paid pattern, never another blank or a client-supplied item. */
public final class TianshuPatternUploadUndo {
    private Receipt last;

    public enum Result { SUCCESS, EMPTY, OCCUPIED, CHANGED, UNAVAILABLE }

    public void provider(PatternContainer target, int slot, ItemStack uploaded) {
        // Keep the live slot identity as well as its contents. An equal replacement inserted
        // by another player is a different item and must not satisfy the old receipt.
        last = new Provider(target, target.getGrid(), slot,
                target.getTerminalPatternInventory().getStackInSlot(slot), uploaded.copy());
    }

    public void closedLoop(TianshuSupercomputerPortBlockEntity target, ClosedLoopPatternRepository repository,
            ClosedLoopPatternPayload payload, ItemStack uploaded) {
        last = new ClosedLoop(target, target.getGrid(), repository, payload, uploaded.copy());
    }

    public boolean available() { return last != null; }
    public void clear() { last = null; }

    public Result undo(IGrid grid, InternalInventory destination, List<PatternContainer> activeTargets) {
        if (last == null) return Result.EMPTY;
        if (grid == null) return Result.UNAVAILABLE;
        if (!destination.getStackInSlot(0).isEmpty()) return Result.OCCUPIED;
        var receipt = last;
        try {
            if (receipt instanceof Provider provider) {
                var target = provider.target();
                if (activeTargets.stream().noneMatch(active -> active == target)
                        || provider.grid() != grid || target.getGrid() != grid || !target.isVisibleInTerminal())
                    return Result.UNAVAILABLE;
                var inventory = target.getTerminalPatternInventory();
                if (provider.slot() < 0 || provider.slot() >= inventory.size()
                        || inventory.getStackInSlot(provider.slot()) != provider.liveStack()
                        || !ItemStack.matches(inventory.getStackInSlot(provider.slot()), provider.uploaded())) {
                    last = null;
                    return Result.CHANGED;
                }
                var removed = inventory.extractItem(provider.slot(), 1, false);
                if (removed.isEmpty()) return Result.UNAVAILABLE;
                // Ownership moved out of the provider. Consume the receipt before callbacks.
                last = null;
                destination.setItemDirect(0, removed);
                if (target instanceof PatternProviderLogicHost host) host.saveChanges();
                return ItemStack.matches(removed, provider.uploaded()) ? Result.SUCCESS : Result.CHANGED;
            }
            var loop = (ClosedLoop) receipt;
            if (!loop.target().isLinkActive() || loop.grid() != grid || loop.target().getGrid() != grid
                    || loop.target().getClosedLoopPatternRepository() != loop.repository()) return Result.UNAVAILABLE;
            // Identity matching rejects a removed or subsequently edited logical pattern.
            if (!loop.repository().remove(loop.payload())) {
                last = null;
                return Result.CHANGED;
            }
            last = null;
            destination.setItemDirect(0, loop.uploaded().copy());
            loop.target().closedLoopPatternsChanged();
            return Result.SUCCESS;
        } catch (RuntimeException unavailable) {
            return Result.UNAVAILABLE;
        }
    }

    private sealed interface Receipt permits Provider, ClosedLoop { }
    private record Provider(PatternContainer target, IGrid grid, int slot, ItemStack liveStack, ItemStack uploaded)
            implements Receipt { }
    private record ClosedLoop(TianshuSupercomputerPortBlockEntity target, IGrid grid, ClosedLoopPatternRepository repository,
            ClosedLoopPatternPayload payload, ItemStack uploaded) implements Receipt { }
}
