package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** One server-menu receipt. Undo returns a paid pattern, never another blank or a client-supplied item. */
public final class TianshuPatternUploadUndo {
    private static final Logger LOG = LogUtils.getLogger();
    private Receipt last;
    private long revision;
    private boolean undoInProgress;

    public enum Result { SUCCESS, QUEUED, EMPTY, OCCUPIED, CHANGED, UNAVAILABLE }

    public void provider(PatternContainer target, int slot, ItemStack uploaded) {
        // Keep the live slot identity as well as its contents. An equal replacement inserted
        // by another player is a different item and must not satisfy the old receipt.
        replace(new Provider(target, target.getGrid(), slot,
                target.getTerminalPatternInventory().getStackInSlot(slot), uploaded.copy()));
    }

    public void closedLoop(TianshuSupercomputerPortBlockEntity target, ClosedLoopPatternRepository repository,
            ClosedLoopPatternPayload payload, ItemStack uploaded) {
        replace(new ClosedLoop(target, target.getGrid(), repository, payload, uploaded.copy()));
    }

    public boolean available() { return last != null; }
    public void clear() { replace(null); }

    public Result undo(IGrid grid, InternalInventory destination, List<PatternContainer> activeTargets,
            TianshuPatternReturnBuffer returns) {
        if (undoInProgress) return Result.UNAVAILABLE;
        if (last == null) return Result.EMPTY;
        if (grid == null) return Result.UNAVAILABLE;
        var receipt = last;
        undoInProgress = true;
        try {
            // The menu's PatternEncodingLogic creates this exact native storage contract. An
            // arbitrary overridden setter can throw before or after accepting custody; do not
            // extract anything when that boundary cannot be established in advance.
            if (!usesNativeStorage(destination) || destination.size() < 1) return Result.UNAVAILABLE;
            if (!destination.getStackInSlot(0).isEmpty()) return Result.OCCUPIED;
            if (receipt instanceof Provider provider) {
                var target = provider.target();
                if (activeTargets.stream().noneMatch(active -> active == target)
                        || provider.grid() != grid || target.getGrid() != grid || !target.isVisibleInTerminal())
                    return Result.UNAVAILABLE;
                var inventory = target.getTerminalPatternInventory();
                if (provider.uploaded().getCount() != 1 || provider.slot() < 0 || provider.slot() >= inventory.size()
                        || inventory.getStackInSlot(provider.slot()) != provider.liveStack()
                        || !ItemStack.matches(inventory.getStackInSlot(provider.slot()), provider.uploaded())) {
                    if (last == receipt) clear();
                    return Result.CHANGED;
                }
                // Serialize while the provider still owns the real item. A failed preparation
                // leaves the source and receipt intact, including when capabilities throw.
                var preparedReturn = returns.prepare(provider.liveStack());
                if (last != receipt) return Result.UNAVAILABLE;
                if (inventory.getStackInSlot(provider.slot()) != provider.liveStack()
                        || !ItemStack.matches(provider.liveStack(), provider.uploaded())) return Result.CHANGED;
                // The receipt grants permission; it never owns an item after this call returns.
                // Retire it before extraction listeners can re-enter this menu.
                if (last != receipt) return Result.UNAVAILABLE;
                clear();
                long ticket = revision;
                ItemStack removed;
                if (usesNativeStorage(inventory)
                        && nativeMethod(inventory, "extractItem", int.class, int.class, boolean.class)) {
                    ItemStack simulated;
                    try {
                        // Native simulate executes the same allowExtract check before returning
                        // a copy. It leaves the physical single-item source untouched.
                        simulated = inventory.extractItem(provider.slot(), 1, true);
                    } catch (RuntimeException failure) {
                        if (inventory.getStackInSlot(provider.slot()) == provider.liveStack()
                                && ItemStack.matches(provider.liveStack(), provider.uploaded())) {
                            restoreReceipt(ticket, provider);
                        }
                        saveProvider(provider, inventory);
                        return Result.UNAVAILABLE;
                    }
                    // A filter may call back into the menu or change its source. Validate again
                    // before invoking the native setter's known physical commit boundary.
                    if (revision != ticket || last != null) return Result.UNAVAILABLE;
                    if (inventory.getStackInSlot(provider.slot()) != provider.liveStack()
                            || !ItemStack.matches(provider.liveStack(), provider.uploaded())) return Result.CHANGED;
                    if (!ItemStack.matches(simulated, provider.uploaded())) {
                        restoreReceipt(ticket, provider);
                        return Result.UNAVAILABLE;
                    }
                    removed = provider.liveStack();
                    try {
                        // A valid native slot is assigned before any callback. Keep its actual
                        // old object even if a listener throws without a useful exception stack.
                        inventory.setItemDirect(provider.slot(), ItemStack.EMPTY);
                    } catch (RuntimeException failure) {
                        return restoreProvider(provider, inventory, destination, removed, ticket, preparedReturn);
                    }
                } else {
                    // Respect a normal generic provider's extraction contract without assuming
                    // that an exception after mutation supplies an extraction receipt.
                    try {
                        var simulated = inventory.extractItem(provider.slot(), 1, true);
                        if (revision != ticket || last != null) return Result.UNAVAILABLE;
                        if (inventory.getStackInSlot(provider.slot()) != provider.liveStack()
                                || !ItemStack.matches(provider.liveStack(), provider.uploaded())) return Result.CHANGED;
                        if (!ItemStack.matches(simulated, provider.uploaded())) {
                            restoreReceipt(ticket, provider);
                            return Result.UNAVAILABLE;
                        }
                    } catch (RuntimeException failure) {
                        if (inventory.getStackInSlot(provider.slot()) == provider.liveStack()
                                && ItemStack.matches(provider.liveStack(), provider.uploaded())) {
                            restoreReceipt(ticket, provider);
                        }
                        saveProvider(provider, inventory);
                        return Result.UNAVAILABLE;
                    }
                    try {
                        removed = inventory.extractItem(provider.slot(), 1, false);
                    } catch (RuntimeException failure) {
                        // The generic API has no commit receipt on an exception. Retain authority
                        // only while its original actual item is still physically in the source;
                        // an empty slot cannot authorize reconstructing a snapshot elsewhere.
                        if (inventory.getStackInSlot(provider.slot()) == provider.liveStack()
                                && ItemStack.matches(provider.liveStack(), provider.uploaded())) {
                            restoreReceipt(ticket, provider);
                        }
                        saveProvider(provider, inventory);
                        return Result.UNAVAILABLE;
                    }
                }
                if (removed.isEmpty()) {
                    var after = inventory.getStackInSlot(provider.slot());
                    if (after == provider.liveStack() && ItemStack.matches(after, provider.uploaded())) {
                        restoreReceipt(ticket, provider);
                        return Result.UNAVAILABLE;
                    }
                    return Result.CHANGED;
                }
                // A successful nonempty return transfers the extracted item to this call.
                // A listener may already have refilled the source with an equal or different
                // replacement; that replacement neither invalidates nor owns this returned item.
                boolean paidItem = ItemStack.matches(removed, provider.uploaded());
                if (writeDestination(destination, removed)) {
                    saveProvider(provider, inventory);
                    return paidItem ? Result.SUCCESS : Result.CHANGED;
                }
                return restoreProvider(provider, inventory, destination, removed, ticket, preparedReturn);
            }
            var loop = (ClosedLoop) receipt;
            if (!loop.target().isLinkActive() || loop.grid() != grid || loop.target().getGrid() != grid
                    || loop.target().getClosedLoopPatternRepository() != loop.repository()) return Result.UNAVAILABLE;
            // Identity matching rejects a removed or subsequently edited logical pattern.
            if (last != receipt) return Result.UNAVAILABLE;
            if (loop.uploaded().getCount() != 1) {
                clear();
                return Result.CHANGED;
            }
            var returned = loop.uploaded().copy();
            var preparedReturn = returns.prepare(returned);
            if (last != receipt) return Result.UNAVAILABLE;
            clear();
            long ticket = revision;
            if (!loop.repository().remove(loop.payload())) {
                return Result.CHANGED;
            }
            if (writeDestination(destination, returned)) {
                saveClosedLoop(loop);
                return Result.SUCCESS;
            }
            // No destination receipt exists. Restore the actual repository before returning,
            // so closing the menu or replacing its receipt cannot discard the paid pattern.
            if (loop.repository().indexOf(loop.payload()) >= 0
                    || loop.repository().add(loop.payload()) == ClosedLoopPatternRepository.PutResult.ADDED) {
                saveClosedLoop(loop);
                restoreReceipt(ticket, loop);
                return Result.UNAVAILABLE;
            }
            // A callback may have filled the freed repository slot. Try the still-empty physical
            // destination once more; every attempted write is audited before changing ownership.
            if (writeDestination(destination, returned)) {
                saveClosedLoop(loop);
                return Result.SUCCESS;
            }
            preparedReturn.enqueue(returned);
            saveClosedLoop(loop);
            return Result.QUEUED;
        } catch (RuntimeException unavailable) {
            return Result.UNAVAILABLE;
        } finally {
            undoInProgress = false;
        }
    }

    private Result restoreProvider(Provider provider, InternalInventory inventory,
            InternalInventory destination, ItemStack held, long ticket,
            TianshuPatternReturnBuffer.PreparedReturn preparedReturn) {
        // A source listener may already have delivered the extracted item before throwing.
        // Require its actual identity; an equal replacement is another physical item.
        if (destination.getStackInSlot(0) == held) {
            saveInventory(destination);
            saveProvider(provider, inventory);
            return ItemStack.matches(held, provider.uploaded()) ? Result.SUCCESS : Result.CHANGED;
        }
        if (inventory.getStackInSlot(provider.slot()) == held) {
            saveProvider(provider, inventory);
            if (ItemStack.matches(held, provider.uploaded())) {
                restoreReceipt(ticket, new Provider(provider.target(), provider.grid(), provider.slot(),
                        held, provider.uploaded()));
            }
            return Result.UNAVAILABLE;
        }
        // setItemDirect is the physical rollback, bypassing normal insertion admission. Native
        // setters accept the actual held object before invoking any callbacks. Once committed,
        // a listener can move it again: never issue a second copy or credit an equal replacement.
        if (inventory.getStackInSlot(provider.slot()).isEmpty() && usesNativeStorage(inventory)) {
            try {
                inventory.setItemDirect(provider.slot(), held);
            } catch (RuntimeException ignored) { }
            saveProvider(provider, inventory);
            var restored = inventory.getStackInSlot(provider.slot());
            if (restored == held && ItemStack.matches(held, provider.uploaded())) {
                restoreReceipt(ticket, new Provider(provider.target(), provider.grid(), provider.slot(),
                        restored, provider.uploaded()));
            }
            if (destination.getStackInSlot(0) == held) {
                saveInventory(destination);
                return ItemStack.matches(held, provider.uploaded()) ? Result.SUCCESS : Result.CHANGED;
            }
            return Result.UNAVAILABLE;
        }
        // Preserve a callback's replacement. An uncommitted rollback must not overwrite it.
        if (writeDestination(destination, held)) {
            saveProvider(provider, inventory);
            return ItemStack.matches(held, provider.uploaded()) ? Result.SUCCESS : Result.CHANGED;
        }
        boolean paidItem = ItemStack.matches(held, provider.uploaded());
        preparedReturn.enqueue(held);
        saveProvider(provider, inventory);
        return paidItem ? Result.QUEUED : Result.CHANGED;
    }

    private static boolean writeDestination(InternalInventory destination, ItemStack stack) {
        if (!usesNativeStorage(destination) || !destination.getStackInSlot(0).isEmpty()) return false;
        try {
            destination.setItemDirect(0, stack);
        } catch (RuntimeException failure) {
            // A valid native slot is assigned before any event/client/host callback can throw.
            // The listener may already have moved the item to another real inventory; an empty
            // destination after this exception does not revoke that committed custody transfer.
        }
        saveInventory(destination);
        return true;
    }

    private static boolean usesNativeStorage(InternalInventory inventory) {
        return inventory instanceof AppEngInternalInventory
                && nativeMethod(inventory, "getStackInSlot", int.class)
                && nativeMethod(inventory, "setItemDirect", int.class, ItemStack.class)
                && nativeMethod(inventory, "size");
    }

    private static boolean nativeMethod(InternalInventory inventory, String name, Class<?>... parameters) {
        try {
            return inventory.getClass().getMethod(name, parameters).getDeclaringClass() == AppEngInternalInventory.class;
        } catch (ReflectiveOperationException | SecurityException unavailable) {
            return false;
        }
    }

    private static void saveProvider(Provider provider, InternalInventory inventory) {
        saveInventory(inventory);
        try {
            if (provider.target() instanceof PatternProviderLogicHost host
                    && (!(inventory instanceof AppEngInternalInventory nativeInventory) || nativeInventory.getHost() != host)) {
                host.saveChanges();
            }
        } catch (RuntimeException failure) {
            LOG.warn("Unable to notify the provider after reconciling upload undo", failure);
        }
    }

    private static void saveInventory(InternalInventory inventory) {
        // AppEngInternalInventory's notifyingChanges guard can remain set when a listener throws.
        // Mark its persistent owner explicitly rather than relying on another inventory event.
        if (inventory instanceof AppEngInternalInventory nativeInventory) {
            try {
                var host = nativeInventory.getHost();
                if (host != null) host.saveChanges();
            } catch (RuntimeException failure) {
                LOG.warn("Unable to save an inventory after reconciling upload undo", failure);
            }
        }
    }

    private static void saveClosedLoop(ClosedLoop loop) {
        try {
            loop.target().closedLoopPatternsChanged();
        } catch (RuntimeException failure) {
            // Publication/persistence is idempotent; retrying it does not create another item.
            try {
                loop.target().closedLoopPatternsChanged();
            } catch (RuntimeException retryFailure) {
                if (retryFailure != failure) retryFailure.addSuppressed(failure);
                LOG.warn("Unable to persist reconciled closed-loop upload undo", retryFailure);
            }
        }
    }

    private void replace(Receipt receipt) {
        last = receipt;
        revision++;
    }

    private void restoreReceipt(long ticket, Receipt receipt) {
        // A close or a newer upload during a callback revoked the old menu authorization.
        if (revision == ticket && last == null) replace(receipt);
    }

    private sealed interface Receipt permits Provider, ClosedLoop { }
    private record Provider(PatternContainer target, IGrid grid, int slot, ItemStack liveStack, ItemStack uploaded)
            implements Receipt { }
    private record ClosedLoop(TianshuSupercomputerPortBlockEntity target, IGrid grid, ClosedLoopPatternRepository repository,
            ClosedLoopPatternPayload payload, ItemStack uploaded) implements Receipt { }
}
