package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayloadTagCodec;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternUploadUndo.Result;
import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;
import com.moakiee.ae2lt.testing.MinecraftTestBootstrap;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** R7: callback exceptions must preserve the one paid physical item and consume its receipt once. */
class TianshuPatternUploadUndoCallbackTest {
    private static Field keyTypeRegistry;
    private static Object previousKeyTypeRegistry;

    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        MinecraftTestBootstrap.bootstrap();
        // The isolated JUnit bootstrap registers vanilla items, but not AE2's key types.
        // Repository decoding must use the real AEItemKey codec rather than silently dropping
        // a nonempty payload when GenericStack.readTag cannot resolve its key type.
        keyTypeRegistry = Class.forName("appeng.api.stacks.AEKeyTypesInternal").getDeclaredField("registry");
        keyTypeRegistry.setAccessible(true);
        previousKeyTypeRegistry = keyTypeRegistry.get(null);
        var name = new ResourceLocation("ae2lt", "r7_upload_undo_test_keys");
        var create = RegistryManager.class.getDeclaredMethod(
                "createRegistry", ResourceLocation.class, RegistryBuilder.class);
        create.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registry = (IForgeRegistry<AEKeyType>) create.invoke(
                new RegistryManager("r7-upload-undo-test"), name,
                new RegistryBuilder<AEKeyType>().setName(name).disableSaving().disableSync());
        registry.register(AEKeyType.items().getId(), AEKeyType.items());
        keyTypeRegistry.set(null, (Supplier<IForgeRegistry<AEKeyType>>) () -> registry);
    }

    @AfterAll
    static void restoreKeyTypes() throws IllegalAccessException {
        if (keyTypeRegistry != null) keyTypeRegistry.set(null, previousKeyTypeRegistry);
    }

    @Test
    void ordinaryUndoReturnsTheOriginalItemAndCannotReplayIntoAnEmptyDestination() {
        var f = new Fixture();
        var first = f.attempt();
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();
        var repeatedReplay = f.attempt();

        assertAll(
                () -> assertEquals(Result.SUCCESS, first.result(), first.toString()),
                () -> assertEquals(Result.EMPTY, retry.result(), retry.toString()),
                () -> assertEquals(1, first.total(), first.toString()),
                () -> assertReturnedExactlyOnce(f, retry, replay, repeatedReplay));
    }

    @Test
    void ordinaryProviderWrapperStillReturnsItsActualExtractedItemToTheNativeDestination() {
        var f = new Fixture();
        var wrapper = new BaseInternalInventory() {
            @Override public int size() { return f.source.size(); }
            @Override public ItemStack getStackInSlot(int slot) { return f.source.getStackInSlot(slot); }
            @Override public void setItemDirect(int slot, ItemStack stack) { f.source.setItemDirect(slot, stack); }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return f.source.extractItem(slot, amount, simulate);
            }
        };
        var wrappedTarget = target(f.grid, wrapper);
        f.undo.provider(wrappedTarget, 0, f.paid);
        var first = f.undo.undo(f.grid, f.destination, List.of(wrappedTarget), f.returns);
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();
        var repeatedReplay = f.attempt();

        assertAll(
                () -> assertEquals(Result.SUCCESS, first),
                () -> assertReturnedExactlyOnce(f, retry, replay, repeatedReplay));
    }

    @Test
    void extractionFilterThrowBeforeRemovalKeepsThePaidItemAndReceiptForRetry() {
        var f = new Fixture();
        var liveBefore = f.source.getStackInSlot(0);
        f.extractGate.armed = true;
        var first = f.attempt();
        var liveAfterFailure = f.source.getStackInSlot(0);
        f.clearFaults();
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();
        var repeatedReplay = f.attempt();

        assertAll(
                () -> assertEquals(1, f.extractGate.exceptions),
                () -> assertEquals(1, f.extractGate.physicalCountAtException),
                () -> assertEquals(Result.UNAVAILABLE, first.result(), first.toString()),
                () -> assertEquals(1, first.provider(), first.toString()),
                () -> assertEquals(0, first.destination(), first.toString()),
                () -> assertTrue(first.receiptAvailable(), first.toString()),
                () -> assertSame(liveBefore, liveAfterFailure),
                () -> assertEquals(Result.SUCCESS, retry.result(), retry.toString()),
                () -> assertReturnedExactlyOnce(f, retry, replay, repeatedReplay));
    }

    @Test
    void extractionListenerThrowAfterPhysicalRemovalKeepsThePaidItemRecoverableOnRetry() {
        var f = new Fixture();
        f.sourceHost.armed = true;
        var first = f.attempt();
        f.clearFaults();
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();
        var repeatedReplay = f.attempt();

        assertAll(
                () -> assertEquals(1, f.sourceHost.exceptions),
                () -> assertEquals(0, f.sourceHost.physicalCountAtException,
                        "The real AE2 inventory was already empty inside its throwing listener"),
                () -> assertTrue(first.total() <= 1, first.toString()),
                () -> assertReturnedExactlyOnce(f, retry, replay, repeatedReplay));
    }

    @Test
    void unknownDestinationThrowBeforePhysicalWriteIsRejectedBeforeExtractionAndNativeRetryReturnsItem() {
        var f = new Fixture();
        var unknown = new BeforeWriteInventory(f.destinationHost);
        unknown.failBeforeNextWrite = true;
        f.destination = unknown;
        var live = f.source.getStackInSlot(0);
        var first = f.attempt();
        var sourceAfterFailure = f.source.getStackInSlot(0);
        f.destination = new AppEngInternalInventory(f.destinationHost, 1);
        f.clearFaults();
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();
        var repeatedReplay = f.attempt();

        assertAll(
                () -> assertEquals(0, unknown.exceptionsBeforeWrite,
                        "An overridden setter has no provable commit contract and must not be called"),
                () -> assertEquals(Result.UNAVAILABLE, first.result(), first.toString()),
                () -> assertEquals(1, first.provider(), first.toString()),
                () -> assertEquals(0, first.destination(), first.toString()),
                () -> assertEquals(1, first.total(), first.toString()),
                () -> assertSame(live, sourceAfterFailure),
                () -> assertTrue(first.receiptAvailable(), first.toString()),
                () -> assertEquals(Result.SUCCESS, retry.result(), retry.toString()),
                () -> assertReturnedExactlyOnce(f, retry, replay, repeatedReplay));
    }

    @Test
    void destinationListenerThrowAfterPhysicalWriteAcknowledgesTheCommittedReturnWithoutReplay() {
        var f = new Fixture();
        f.destinationHost.armed = true;
        var first = f.attempt();
        f.clearFaults();
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();
        var repeatedReplay = f.attempt();

        assertAll(
                () -> assertEquals(1, f.destinationHost.exceptions),
                () -> assertEquals(1, f.destinationHost.physicalCountAtException,
                        "The real AE2 inventory already contained the paid item inside its throwing listener"),
                () -> assertEquals(Result.SUCCESS, first.result(),
                        "A physically committed return must not be reported as unavailable: " + first),
                () -> assertEquals(0, first.provider(), first.toString()),
                () -> assertEquals(1, first.destination(), first.toString()),
                () -> assertFalse(first.receiptAvailable(), first.toString()),
                () -> assertEquals(Result.EMPTY, retry.result(), retry.toString()),
                () -> assertReturnedExactlyOnce(f, retry, replay, repeatedReplay));
    }

    @Test
    void providerRemovalCallbackFailureRestoresPersistentSlotBeforeMenuClose() {
        var f = new Fixture();
        f.sourceHost.armed = true;
        var failure = f.attempt();
        var saved = f.sourceHost.savedCount();
        f.undo.clear(); // Same ownership boundary used by TianshuPatternEncodingTermMenu.removed.
        var afterClose = f.attempt();

        assertAll(
                () -> assertEquals(1, failure.provider(), "Recovery completes before returning: " + failure),
                () -> assertEquals(0, failure.destination(), failure.toString()),
                () -> assertEquals(1, saved, "The native host saved the restored provider slot"),
                () -> assertEquals(1, afterClose.total(), afterClose.toString()),
                () -> assertEquals(1, afterClose.provider(), afterClose.toString()),
                () -> assertEquals(Result.EMPTY, afterClose.result(), afterClose.toString()),
                () -> assertFalse(afterClose.receiptAvailable(), afterClose.toString()),
                () -> assertEquals(1, f.roundTripPhysicalInventories().total()));
    }

    @Test
    void providerRollbackListenerFailureStillSavesItsPhysicalRestoration() {
        var f = new Fixture();
        // eventsEnabled runs after native removal but before notifyingChanges is raised.
        // Its failure lets the subsequent real rollback invoke another throwing native listener.
        f.sourceHost.failBeforeNextEvents = true;
        f.sourceHost.beforeEventsFailure = () -> f.sourceHost.armed = true;
        var failure = f.attempt();
        var saved = f.sourceHost.savedCount();
        f.undo.clear();
        var afterClose = f.attempt();

        assertAll(
                () -> assertEquals(1, f.sourceHost.eventsExceptions),
                () -> assertEquals(1, f.sourceHost.exceptions),
                () -> assertEquals(1, f.sourceHost.physicalCountAtException,
                        "Rollback's native listener threw after the provider slot was restored"),
                () -> assertEquals(1, failure.provider(), failure.toString()),
                () -> assertEquals(1, failure.total(), failure.toString()),
                () -> assertEquals(1, saved, "Explicit save must run after the rollback listener throws"),
                () -> assertEquals(1, afterClose.provider(), afterClose.toString()),
                () -> assertEquals(Result.EMPTY, afterClose.result(), afterClose.toString()));
    }

    @Test
    void aNewUploadReplacesOnlyAuthorityAfterTheOldItemWasPhysicallyRestored() {
        var f = new Fixture();
        f.sourceHost.armed = true;
        var failure = f.attempt();
        var second = new AppEngInternalInventory(1);
        var secondPaid = secondPaidPatternToken();
        second.setItemDirect(0, secondPaid.copy());
        var secondTarget = target(f.grid, second);
        f.undo.provider(secondTarget, 0, secondPaid);
        var result = f.undo.undo(f.grid, f.destination, List.of(f.target, secondTarget), f.returns);
        var returned = f.destination.getStackInSlot(0).copy();
        var afterNewUndoTotal = f.physicalCount(f.source) + f.physicalCount(second) + f.physicalCount(f.destination);
        f.collectReturnedItem();
        var replay = f.undo.undo(f.grid, f.destination, List.of(f.target, secondTarget), f.returns);

        assertAll(
                () -> assertEquals(1, failure.provider(), failure.toString()),
                () -> assertEquals(Result.SUCCESS, result),
                () -> assertTrue(ItemStack.matches(secondPaid, returned)),
                () -> assertEquals(2, afterNewUndoTotal),
                () -> assertTrue(ItemStack.matches(f.paid, f.source.getStackInSlot(0))),
                () -> assertEquals(0, f.physicalCount(second)),
                () -> assertEquals(Result.EMPTY, replay),
                () -> assertEquals(0, f.physicalCount(f.destination)),
                () -> assertEquals(2, f.physicalCount(f.source) + f.physicalCount(f.collected)),
                () -> assertEquals(1, f.sourceHost.savedCount()));
    }

    @Test
    void menuCloseInsideExtractionCallbackCannotReviveTheOldReceipt() {
        var f = new Fixture();
        f.sourceHost.armed = true;
        f.sourceHost.onThrow = f.undo::clear;
        var failure = f.attempt();
        var replay = f.attempt();

        assertAll(
                () -> assertEquals(1, failure.provider(), failure.toString()),
                () -> assertEquals(1, failure.total(), failure.toString()),
                () -> assertFalse(failure.receiptAvailable(), failure.toString()),
                () -> assertEquals(Result.EMPTY, replay.result(), replay.toString()),
                () -> assertEquals(1, f.sourceHost.savedCount()));
    }

    @Test
    void newUploadInsideExtractionCallbackKeepsItsReceiptDuringOldRollback() {
        var f = new Fixture();
        var second = new AppEngInternalInventory(1);
        var secondPaid = secondPaidPatternToken();
        second.setItemDirect(0, secondPaid.copy());
        var secondTarget = target(f.grid, second);
        f.sourceHost.armed = true;
        f.sourceHost.onThrow = () -> f.undo.provider(secondTarget, 0, secondPaid);
        var failure = f.attempt();
        var result = f.undo.undo(f.grid, f.destination, List.of(f.target, secondTarget), f.returns);

        assertAll(
                () -> assertEquals(1, failure.provider(), failure.toString()),
                () -> assertTrue(failure.receiptAvailable(), "The callback installed a newer receipt"),
                () -> assertEquals(Result.SUCCESS, result),
                () -> assertTrue(ItemStack.matches(secondPaid, f.destination.getStackInSlot(0)),
                        "Old rollback must not replace the new upload's authority"),
                () -> assertTrue(ItemStack.matches(f.paid, f.source.getStackInSlot(0))),
                () -> assertEquals(0, f.physicalCount(second)),
                () -> assertEquals(2, f.physicalCount(f.source) + f.physicalCount(f.destination)),
                () -> assertFalse(f.undo.available()));
    }

    @Test
    void closedLoopOrdinaryUndoReturnsOneItemAndPersistsTheRemovedDefinition() throws Exception {
        var f = new ClosedLoopFixture();
        var first = f.attempt();
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();

        assertAll(
                () -> assertEquals(Result.SUCCESS, first.result(), first.toString()),
                () -> assertEquals(1, first.total(), first.toString()),
                () -> assertLoopReturnedOnce(f, retry, replay));
    }

    @Test
    void closedLoopUnknownDestinationIsRejectedWithoutRemovingTheRealRepositoryBeforeNativeRetry() throws Exception {
        var f = new ClosedLoopFixture();
        var unknown = new BeforeWriteInventory(f.destinationHost);
        unknown.failBeforeNextWrite = true;
        f.destination = unknown;
        var failure = f.attempt();
        var restoredPayload = f.repository.get(0);
        var savedAfterFailure = f.savedRepositoryCount();
        f.destination = new AppEngInternalInventory(f.destinationHost, 1);
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();

        assertAll(
                () -> assertEquals(0, unknown.exceptionsBeforeWrite),
                () -> assertEquals(Result.UNAVAILABLE, failure.result(), failure.toString()),
                () -> assertEquals(1, failure.provider(), "The repository owns the item before return: " + failure),
                () -> assertEquals(0, failure.destination(), failure.toString()),
                () -> assertSame(f.payload, restoredPayload, "Restore the original logical identity"),
                () -> assertEquals(1, savedAfterFailure, "The restored real repository was serialized"),
                () -> assertEquals(Result.SUCCESS, retry.result(), retry.toString()),
                () -> assertLoopReturnedOnce(f, retry, replay));
    }

    @Test
    void closedLoopDestinationOccupiedAfterInitialCheckRestoresTheRealRepositoryBeforeRetry() throws Exception {
        var f = new ClosedLoopFixture();
        var blocker = secondPaidPatternToken();
        var blockerStore = new AppEngInternalInventory(1);
        f.port.beforeRepositoryAccess = () -> f.destination.setItemDirect(0, blocker);
        var failure = f.attempt();
        var restoredPayload = f.repository.get(0);
        var savedAfterFailure = f.savedRepositoryCount();
        blockerStore.setItemDirect(0, f.destination.extractItem(0, 1, false));
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();

        assertAll(
                () -> assertEquals(Result.UNAVAILABLE, failure.result(), failure.toString()),
                () -> assertEquals(1, failure.provider(), "The removed logical item is restored: " + failure),
                () -> assertTrue(failure.receiptAvailable(), failure.toString()),
                () -> assertSame(f.payload, restoredPayload),
                () -> assertEquals(1, savedAfterFailure),
                () -> assertSame(blocker, blockerStore.getStackInSlot(0)),
                () -> assertEquals(Result.SUCCESS, retry.result(), retry.toString()),
                () -> assertLoopReturnedOnce(f, retry, replay),
                () -> assertEquals(2, replay.total() + blockerStore.getStackInSlot(0).getCount()));
    }

    @Test
    void extractionListenerRefillingSourceWithEqualReplacementStillReturnsOnlyTheExtractedPaidItem() {
        assertRefillDoesNotConsumeReplacement(paidPatternToken());
    }

    @Test
    void extractionListenerRefillingSourceWithDifferentReplacementStillReturnsOnlyTheExtractedPaidItem() {
        assertRefillDoesNotConsumeReplacement(secondPaidPatternToken());
    }

    @Test
    void nativeDestinationListenerMovingCommittedItemToThirdInventoryThenThrowingCannotRefundAgain() {
        var f = new Fixture();
        var actualPaid = f.source.getStackInSlot(0);
        f.destinationHost.armed = true;
        f.destinationHost.onThrow = f::collectReturnedItem;
        var first = f.attempt();
        var retry = f.attempt();
        var replay = f.attempt();
        var persisted = f.roundTripPhysicalInventories();

        assertAll(
                () -> assertEquals(1, f.destinationHost.exceptions),
                () -> assertEquals(1, f.destinationHost.physicalCountAtException),
                () -> assertEquals(Result.SUCCESS, first.result(), first.toString()),
                () -> assertEquals(0, first.provider(), first.toString()),
                () -> assertEquals(0, first.destination(), first.toString()),
                () -> assertEquals(1, first.collected(), first.toString()),
                () -> assertEquals(1, first.total(), first.toString()),
                () -> assertSame(actualPaid, f.collected.getStackInSlot(0)),
                () -> assertFalse(first.receiptAvailable(), first.toString()),
                () -> assertEquals(Result.EMPTY, retry.result(), retry.toString()),
                () -> assertEquals(Result.EMPTY, replay.result(), replay.toString()),
                () -> assertEquals(1, replay.total(), replay.toString()),
                () -> assertEquals(1, persisted.total(), persisted.toString()),
                () -> assertEquals(1, persisted.collected(), persisted.toString()),
                () -> assertTrue(ItemStack.matches(f.paid, f.collected.getStackInSlot(0))));
    }

    @Test
    void prebuiltStacklessSourceListenerFailureStillRestoresItsActualItemBeforeRetry() {
        var f = new Fixture();
        var prebuilt = new CallbackFailure("prebuilt stackless source listener", false);
        var actualPaid = f.source.getStackInSlot(0);
        f.sourceHost.failureToThrow = prebuilt;
        f.sourceHost.armed = true;
        var first = f.attempt();
        var restored = f.source.getStackInSlot(0);
        var savedAfterFailure = f.sourceHost.savedCount();
        f.clearFaults();
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();
        var repeatedReplay = f.attempt();

        assertAll(
                () -> assertEquals(0, prebuilt.getStackTrace().length),
                () -> assertSame(prebuilt, f.sourceHost.lastThrown),
                () -> assertEquals(Result.UNAVAILABLE, first.result(), first.toString()),
                () -> assertEquals(1, first.provider(), first.toString()),
                () -> assertEquals(0, first.destination(), first.toString()),
                () -> assertEquals(1, first.total(), first.toString()),
                () -> assertSame(actualPaid, restored),
                () -> assertTrue(first.receiptAvailable(), first.toString()),
                () -> assertEquals(1, savedAfterFailure),
                () -> assertEquals(Result.SUCCESS, retry.result(), retry.toString()),
                () -> assertReturnedExactlyOnce(f, retry, replay, repeatedReplay));
    }

    @Test
    void prebuiltStacklessDestinationListenerMovingCommittedItemCannotRefundAgain() {
        var f = new Fixture();
        var prebuilt = new CallbackFailure("prebuilt stackless destination listener", false);
        var actualPaid = f.source.getStackInSlot(0);
        f.destinationHost.failureToThrow = prebuilt;
        f.destinationHost.armed = true;
        f.destinationHost.onThrow = f::collectReturnedItem;
        var first = f.attempt();
        var replay = f.attempt();
        var repeatedReplay = f.attempt();
        var persisted = f.roundTripPhysicalInventories();

        assertAll(
                () -> assertEquals(0, prebuilt.getStackTrace().length),
                () -> assertSame(prebuilt, f.destinationHost.lastThrown),
                () -> assertEquals(Result.SUCCESS, first.result(), first.toString()),
                () -> assertEquals(0, first.provider(), first.toString()),
                () -> assertEquals(0, first.destination(), first.toString()),
                () -> assertEquals(1, first.collected(), first.toString()),
                () -> assertEquals(1, first.total(), first.toString()),
                () -> assertSame(actualPaid, f.collected.getStackInSlot(0)),
                () -> assertFalse(first.receiptAvailable(), first.toString()),
                () -> assertEquals(Result.EMPTY, replay.result(), replay.toString()),
                () -> assertEquals(Result.EMPTY, repeatedReplay.result(), repeatedReplay.toString()),
                () -> assertEquals(0, repeatedReplay.destination(), repeatedReplay.toString()),
                () -> assertEquals(1, repeatedReplay.total(), repeatedReplay.toString()),
                () -> assertEquals(1, persisted.total(), persisted.toString()),
                () -> assertEquals(1, persisted.collected(), persisted.toString()));
    }

    @Test
    void nativeSourceListenerDeliveringActualPaidItemBeforeThrowCannotCreateAnotherReturn() {
        var f = new Fixture();
        var actualPaid = f.source.getStackInSlot(0);
        f.sourceHost.armed = true;
        f.sourceHost.onThrow = () -> f.destination.setItemDirect(0, actualPaid);
        var first = f.attempt();
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();
        var repeatedReplay = f.attempt();

        assertAll(
                () -> assertEquals(Result.SUCCESS, first.result(), first.toString()),
                () -> assertEquals(0, first.provider(), first.toString()),
                () -> assertEquals(1, first.destination(), first.toString()),
                () -> assertEquals(1, first.total(), first.toString()),
                () -> assertFalse(first.receiptAvailable(), first.toString()),
                () -> assertSame(actualPaid, f.collected.getStackInSlot(0)),
                () -> assertReturnedExactlyOnce(f, retry, replay, repeatedReplay));
    }

    @Test
    void unknownSourceExtractionMovingItemElsewhereThenThrowingIsNeverReconstructed() {
        var f = new Fixture();
        var actualPaid = f.source.extractItem(0, 1, false);
        var unknown = new TransferThenThrowInventory(f.sourceHost, f.collected);
        unknown.setItemDirect(0, actualPaid);
        f.source = unknown;
        f.undo.provider(f.target, 0, f.paid);
        var first = f.attempt();
        var replay = f.attempt();
        var persisted = f.roundTripPhysicalInventories();

        assertAll(
                () -> assertEquals(1, unknown.exceptions),
                () -> assertEquals(Result.UNAVAILABLE, first.result(), first.toString()),
                () -> assertEquals(0, first.provider(), first.toString()),
                () -> assertEquals(0, first.destination(), first.toString()),
                () -> assertEquals(1, first.collected(), first.toString()),
                () -> assertEquals(1, first.total(), first.toString()),
                () -> assertSame(actualPaid, f.collected.getStackInSlot(0)),
                () -> assertFalse(first.receiptAvailable(), first.toString()),
                () -> assertEquals(Result.EMPTY, replay.result(), replay.toString()),
                () -> assertEquals(1, replay.total(), replay.toString()),
                () -> assertEquals(1, persisted.total(), persisted.toString()));
    }

    @Test
    void rollbackListenerReplacingItsCommittedItemWithEqualDifferentIdentityCannotReviveOldReceipt() {
        var f = new Fixture();
        var actualPaid = f.source.getStackInSlot(0);
        var replacement = paidPatternToken();
        f.sourceHost.failBeforeNextEvents = true;
        f.sourceHost.beforeEventsFailure = () -> {
            f.sourceHost.armed = true;
            f.sourceHost.onThrow = () -> {
                f.collected.setItemDirect(0, f.source.extractItem(0, 1, false));
                f.source.setItemDirect(0, replacement);
            };
        };
        var first = f.attempt();
        var replay = f.attempt();
        var persisted = f.roundTripPhysicalInventories();

        assertAll(
                () -> assertEquals(1, f.sourceHost.eventsExceptions),
                () -> assertEquals(1, f.sourceHost.exceptions),
                () -> assertEquals(1, first.provider(), first.toString()),
                () -> assertEquals(0, first.destination(), first.toString()),
                () -> assertEquals(1, first.collected(), first.toString()),
                () -> assertEquals(2, first.total(), first.toString()),
                () -> assertSame(actualPaid, f.collected.getStackInSlot(0)),
                () -> assertSame(replacement, f.source.getStackInSlot(0)),
                () -> assertFalse(first.receiptAvailable(),
                        "Equal contents do not identify this call's restored physical item: " + first),
                () -> assertEquals(Result.EMPTY, replay.result(), replay.toString()),
                () -> assertEquals(0, replay.destination(), replay.toString()),
                () -> assertEquals(2, replay.total(), replay.toString()),
                () -> assertEquals(2, persisted.total(), persisted.toString()),
                () -> assertEquals(1, f.sourceHost.savedCount()));
    }

    @Test
    void listenerRefillingBothSlotsReturnsTheActualPaidItemThroughPersistentPlayerStorage() {
        assertBothRefilledUsesPersistentPlayerStorage(false);
    }

    @Test
    void throwingListenerRefillingBothSlotsReturnsTheActualPaidItemThroughPersistentPlayerStorage() {
        assertBothRefilledUsesPersistentPlayerStorage(true);
    }

    private static void assertBothRefilledUsesPersistentPlayerStorage(boolean listenerThrows) {
        var f = new Fixture();
        var original = f.source.getStackInSlot(0);
        var sourceReplacement = secondPaidPatternToken();
        var destinationReplacement = paidPatternToken();
        destinationReplacement.getOrCreateTag().putString("paid-pattern-id", "r7-third-physical-paid-pattern");
        var external = new AppEngInternalInventory(2);
        external.setItemDirect(0, sourceReplacement);
        external.setItemDirect(1, destinationReplacement);
        assertEquals(3, f.physicalCount(f.source) + f.physicalCount(f.destination) + f.physicalCount(external),
                "The callback's replacements must start in real external storage");
        Runnable refillBoth = () -> {
            f.source.setItemDirect(0, external.extractItem(0, 1, false));
            f.destination.setItemDirect(0, external.extractItem(1, 1, false));
        };
        if (listenerThrows) {
            f.sourceHost.armed = true;
            f.sourceHost.onThrow = refillBoth;
        } else {
            f.sourceHost.onNextChange = refillBoth;
        }
        var first = f.attempt();
        assertAll(
                () -> assertEquals(Result.QUEUED, first.result(), first.toString()),
                () -> assertEquals(listenerThrows ? 1 : 0, f.sourceHost.exceptions),
                () -> assertEquals(1, first.provider(), first.toString()),
                () -> assertEquals(1, first.destination(), first.toString()),
                () -> assertEquals(0, f.physicalCount(external)),
                () -> assertEquals(1, f.returns.pendingCount()),
                () -> assertEquals(3, first.total() + storedReturnCount(f.playerData)),
                () -> assertTrue(original.isEmpty(), "The actual held stack surrendered custody after persistence"),
                () -> assertTrue(ItemStack.matches(sourceReplacement, f.source.getStackInSlot(0))),
                () -> assertTrue(ItemStack.matches(destinationReplacement, f.destination.getStackInSlot(0))),
                () -> assertFalse(first.receiptAvailable(), first.toString()),
                () -> assertEquals(1, f.sourceHost.savedCount()),
                () -> assertEquals(1, f.destinationHost.savedCount()));

        // A newer upload and menu close change authority, never the player's owned returns.
        f.undo.provider(f.target, 0, sourceReplacement);
        f.undo.clear();
        var playerInventory = new Inventory(null);
        for (int slot = 0; slot < playerInventory.items.size(); slot++) {
            playerInventory.items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        var savedInventory = playerInventory.save(new ListTag());
        var savedPlayerData = f.playerData.copy();
        var reopenedReturns = new TianshuPatternReturnBuffer(savedPlayerData);
        var reopenedInventory = new Inventory(null);
        reopenedInventory.load(savedInventory);
        assertAll(
                () -> assertFalse(reopenedReturns.deliver(reopenedInventory)),
                () -> assertEquals(1, reopenedReturns.pendingCount()),
                () -> assertEquals(1, storedReturnCount(savedPlayerData)),
                () -> assertEquals(3, first.total() + storedReturnCount(savedPlayerData)));

        var unknownInventory = new Inventory(null) {
            @Override
            public void setItem(int slot, ItemStack stack) {
                throw new AssertionError("Unknown inventory setters cannot be a delivery receipt");
            }
        };
        assertFalse(reopenedReturns.deliver(unknownInventory));
        assertEquals(1, reopenedReturns.pendingCount());
        assertTrue(unknownInventory.items.stream().allMatch(ItemStack::isEmpty));
        // Move a real filler stack elsewhere before opening an actual player slot.
        var fillerStorage = new AppEngInternalInventory(1);
        fillerStorage.setItemDirect(0, reopenedInventory.removeItemNoUpdate(10));
        assertTrue(reopenedReturns.deliver(reopenedInventory));
        assertAll(
                () -> assertEquals(0, reopenedReturns.pendingCount()),
                () -> assertEquals(0, storedReturnCount(savedPlayerData)),
                () -> assertTrue(ItemStack.matches(f.paid, reopenedInventory.items.get(10))),
                () -> assertEquals(1, reopenedInventory.getTimesChanged()),
                () -> assertFalse(reopenedReturns.deliver(reopenedInventory)),
                () -> assertEquals(1, physicalPaidPatternCount(reopenedInventory)));

        var deliveredInventory = new Inventory(null);
        deliveredInventory.load(reopenedInventory.save(new ListTag()));
        var deliveredReturns = new TianshuPatternReturnBuffer(savedPlayerData.copy());
        assertFalse(deliveredReturns.deliver(deliveredInventory));
        // Empty the terminal before replay so OCCUPIED cannot hide a reusable old receipt.
        f.collectReturnedItem();
        var replay = f.attempt();
        var reopenedUndo = new TianshuPatternUploadUndo();
        var reopenedReplay = reopenedUndo.undo(f.grid, f.destination, List.of(f.target), deliveredReturns);
        var persisted = f.roundTripPhysicalInventories();
        assertAll(
                () -> assertEquals(Result.EMPTY, replay.result(), replay.toString()),
                () -> assertEquals(Result.EMPTY, reopenedReplay),
                () -> assertEquals(0, f.physicalCount(f.destination)),
                () -> assertEquals(3, replay.total() + physicalPaidPatternCount(deliveredInventory)),
                () -> assertEquals(3, persisted.total() + physicalPaidPatternCount(deliveredInventory)),
                () -> assertTrue(ItemStack.matches(sourceReplacement, f.source.getStackInSlot(0))),
                () -> assertTrue(ItemStack.matches(destinationReplacement, f.collected.getStackInSlot(0))),
                () -> assertTrue(ItemStack.matches(f.paid, deliveredInventory.items.get(10))),
                () -> assertEquals(0, deliveredReturns.pendingCount()));
    }

    private static int storedReturnCount(CompoundTag playerData) {
        var returns = playerData.getCompound(Player.PERSISTED_NBT_TAG)
                .getList("ae2ltTianshuPatternReturns", Tag.TAG_COMPOUND);
        int count = 0;
        for (int i = 0; i < returns.size(); i++) count += ItemStack.of(returns.getCompound(i)).getCount();
        return count;
    }

    private static int physicalPaidPatternCount(Inventory inventory) {
        int count = 0;
        for (var stack : inventory.items) {
            if (stack.hasTag() && stack.getTag().contains("paid-pattern-id")) count += stack.getCount();
        }
        return count;
    }

    private static void assertRefillDoesNotConsumeReplacement(ItemStack replacement) {
        var f = new Fixture();
        var actualPaid = f.source.getStackInSlot(0);
        f.sourceHost.onNextChange = () -> f.source.setItemDirect(0, replacement);
        var first = f.attempt();
        var returned = f.destination.getStackInSlot(0);
        f.collectReturnedItem();
        var replay = f.attempt();
        var repeatedReplay = f.attempt();
        var persisted = f.roundTripPhysicalInventories();

        assertAll(
                () -> assertEquals(Result.SUCCESS, first.result(), first.toString()),
                () -> assertEquals(2, first.total(), first.toString()),
                () -> assertEquals(1, first.provider(), first.toString()),
                () -> assertEquals(1, first.destination(), first.toString()),
                () -> assertSame(actualPaid, returned, "Deliver the item returned by the native extraction"),
                () -> assertSame(actualPaid, f.collected.getStackInSlot(0)),
                () -> assertSame(replacement, f.source.getStackInSlot(0)),
                () -> assertTrue(ItemStack.matches(f.paid, f.collected.getStackInSlot(0))),
                () -> assertFalse(first.receiptAvailable(), first.toString()),
                () -> assertEquals(Result.EMPTY, replay.result(), replay.toString()),
                () -> assertEquals(Result.EMPTY, repeatedReplay.result(), repeatedReplay.toString()),
                () -> assertEquals(0, replay.destination(), replay.toString()),
                () -> assertEquals(2, repeatedReplay.total(), repeatedReplay.toString()),
                () -> assertEquals(2, persisted.total(), persisted.toString()),
                () -> assertEquals(1, f.sourceHost.savedCount()));
    }

    @Test
    void closedLoopDestinationFailureAfterWriteCommitsOnceDespitePublicationFailure() throws Exception {
        var f = new ClosedLoopFixture();
        f.destinationHost.armed = true;
        f.port.throwAfterNextSave = true;
        var first = f.attempt();
        var publications = f.port.publications;
        var savedAfterCommit = f.savedRepositoryCount();
        var retry = f.attempt();
        f.collectReturnedItem();
        var replay = f.attempt();

        assertAll(
                () -> assertEquals(1, f.destinationHost.exceptions),
                () -> assertEquals(Result.SUCCESS, first.result(), first.toString()),
                () -> assertEquals(0, first.provider(), first.toString()),
                () -> assertEquals(1, first.destination(), first.toString()),
                () -> assertFalse(first.receiptAvailable(), first.toString()),
                () -> assertEquals(2, publications, "Idempotent publication retries do not issue another item"),
                () -> assertEquals(0, savedAfterCommit),
                () -> assertLoopReturnedOnce(f, retry, replay));
    }

    private static void assertLoopReturnedOnce(ClosedLoopFixture f, Observation retry, Observation replay) {
        var repeatedReplay = f.attempt();
        assertAll(
                () -> assertEquals(1, retry.total(), retry.toString()),
                () -> assertEquals(0, retry.provider(), retry.toString()),
                () -> assertEquals(1, retry.destination(), retry.toString()),
                () -> assertFalse(retry.receiptAvailable(), retry.toString()),
                () -> assertEquals(Result.EMPTY, replay.result(), replay.toString()),
                () -> assertEquals(1, replay.total(), replay.toString()),
                () -> assertEquals(0, replay.provider(), replay.toString()),
                () -> assertEquals(0, replay.destination(), replay.toString()),
                () -> assertEquals(1, replay.collected(), replay.toString()),
                () -> assertFalse(replay.receiptAvailable(), replay.toString()),
                () -> assertEquals(Result.EMPTY, repeatedReplay.result(), repeatedReplay.toString()),
                () -> assertEquals(1, repeatedReplay.total(), repeatedReplay.toString()),
                () -> assertEquals(0, repeatedReplay.destination(), repeatedReplay.toString()),
                () -> assertTrue(ItemStack.matches(f.paid, f.collected.getStackInSlot(0))),
                () -> assertEquals(0, f.savedRepositoryCount()),
                () -> assertEquals(1, f.savedCollectedCount()));
    }

    private static void assertReturnedExactlyOnce(
            Fixture f, Observation retry, Observation replay, Observation repeatedReplay) {
        // Observe physical slots, never the receipt's uploaded/liveStack copies or callback arguments.
        var persisted = f.roundTripPhysicalInventories();
        assertAll(
                () -> assertEquals(1, retry.total(), "Paid item conserved after retry: " + retry),
                () -> assertEquals(0, retry.provider(), retry.toString()),
                () -> assertEquals(1, retry.destination(), "The paid item is reclaimable after retry: " + retry),
                () -> assertFalse(retry.receiptAvailable(), retry.toString()),
                () -> assertEquals(1, replay.total(), "Paid item conserved after collection and replay: " + replay),
                () -> assertEquals(0, replay.provider(), replay.toString()),
                () -> assertEquals(0, replay.destination(), "Empty destination cannot expose another refund: " + replay),
                () -> assertEquals(1, replay.collected(), replay.toString()),
                () -> assertEquals(Result.EMPTY, replay.result(), replay.toString()),
                () -> assertFalse(replay.receiptAvailable(), replay.toString()),
                () -> assertEquals(1, repeatedReplay.total(), repeatedReplay.toString()),
                () -> assertEquals(0, repeatedReplay.destination(), repeatedReplay.toString()),
                () -> assertEquals(Result.EMPTY, repeatedReplay.result(), repeatedReplay.toString()),
                () -> assertFalse(repeatedReplay.receiptAvailable(), repeatedReplay.toString()),
                () -> assertTrue(ItemStack.matches(f.paid, f.collected.getStackInSlot(0)),
                        "Returned physical item keeps every paid-pattern tag"),
                () -> assertEquals(1, persisted.total(), "Native inventory NBT round trip: " + persisted),
                () -> assertEquals(1, persisted.collected(), persisted.toString()));
    }

    private record Observation(Result result, int provider, int destination, int collected, boolean receiptAvailable) {
        int total() { return provider + destination + collected; }
    }

    private static PatternContainer target(IGrid grid, InternalInventory inventory) {
        return new PatternContainer() {
            @Override public IGrid getGrid() { return grid; }
            @Override public boolean isVisibleInTerminal() { return true; }
            @Override public InternalInventory getTerminalPatternInventory() { return inventory; }
            @Override public PatternContainerGroup getTerminalGroup() { return null; }
        };
    }

    private static final class ClosedLoopFixture {
        final IGrid grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "R7 closed-loop identity-only grid";
                    default -> throw new AssertionError(method);
                });
        final ClosedLoopPatternRepository repository = new ClosedLoopPatternRepository(() -> 4);
        final ClosedLoopPatternPayload payload = new ClosedLoopPatternPayload(
                List.of(new ClosedLoopMemberPattern(new SourcePatternSnapshot(
                        new ResourceLocation("ae2", "processing_pattern"), null, null), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)), List.of(),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)), 1, 1, true);
        final ItemStack paid = paidPatternToken();
        final ThrowingHost destinationHost = new ThrowingHost("closed-loop destination after write");
        AppEngInternalInventory destination = new AppEngInternalInventory(destinationHost, 1);
        final AppEngInternalInventory collected = new AppEngInternalInventory(1);
        final TianshuPatternUploadUndo undo = new TianshuPatternUploadUndo();
        final CompoundTag playerData = new CompoundTag();
        final TianshuPatternReturnBuffer returns = new TianshuPatternReturnBuffer(playerData);
        final RepositoryPort port;

        ClosedLoopFixture() throws Exception {
            var encoded = ClosedLoopPatternPayloadTagCodec.write(payload);
            var decoded = ClosedLoopPatternPayloadTagCodec.read(encoded);
            assertEquals(encoded, ClosedLoopPatternPayloadTagCodec.write(decoded),
                    "The nonempty real payload codec must round trip before any undo callback");
            repository.add(payload);
            // Only the port's grid/repository/publication boundary is a stub. Allocate it without
            // invoking block registration or constructing a server; all storage/codec code is real.
            var unsafeType = Class.forName("sun.misc.Unsafe");
            var field = unsafeType.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = field.get(null);
            port = (RepositoryPort) unsafeType.getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, RepositoryPort.class);
            port.testGrid = grid;
            port.repository = repository;
            port.closedLoopPatternsChanged();
            assertEquals(1, savedRepositoryCount(),
                    "The initial real repository must persist before any undo callback");
            port.publications = 0;
            undo.closedLoop(port, repository, payload, paid);
        }

        Observation attempt() {
            var result = undo.undo(grid, destination, List.of(), returns);
            return new Observation(result, repository.size(), destination.getStackInSlot(0).getCount(),
                    collected.getStackInSlot(0).getCount(), undo.available());
        }

        void collectReturnedItem() {
            collected.setItemDirect(0, destination.extractItem(0, 1, false));
        }

        int savedRepositoryCount() {
            var restored = new ClosedLoopPatternRepository(() -> 4);
            restored.readFrom(port.persisted);
            return restored.size();
        }

        int savedCollectedCount() {
            var saved = new CompoundTag();
            collected.writeToNBT(saved, "collected");
            var restored = new AppEngInternalInventory(1);
            restored.readFromNBT(saved, "collected");
            return restored.getStackInSlot(0).getCount();
        }
    }

    private static final class RepositoryPort extends TianshuSupercomputerPortBlockEntity {
        IGrid testGrid;
        ClosedLoopPatternRepository repository;
        CompoundTag persisted;
        int publications;
        boolean throwAfterNextSave;
        Runnable beforeRepositoryAccess;

        private RepositoryPort() {
            super(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            throw new AssertionError("Allocate the boundary stub without constructing a registered block entity");
        }

        @Override public IGrid getGrid() { return testGrid; }
        @Override public boolean isLinkActive() { return true; }
        @Override
        public ClosedLoopPatternRepository getClosedLoopPatternRepository() {
            var callback = beforeRepositoryAccess;
            beforeRepositoryAccess = null;
            if (callback != null) callback.run();
            return repository;
        }

        @Override
        public void closedLoopPatternsChanged() {
            publications++;
            persisted = new CompoundTag();
            repository.writeTo(persisted);
            if (throwAfterNextSave) {
                throwAfterNextSave = false;
                throw new CallbackFailure("closed-loop publication after persistence");
            }
        }
    }

    private static final class Fixture {
        // Undo treats the uploaded item opaquely. A registered vanilla carrier avoids initializing
        // AE2's mod registry; its full tagged identity is the paid item under test, never a blank.
        final ItemStack paid = paidPatternToken();
        final ThrowingHost sourceHost = new ThrowingHost("provider after removal");
        final ThrowingHost destinationHost = new ThrowingHost("destination after write");
        final ExtractGate extractGate = new ExtractGate();
        AppEngInternalInventory source = new AppEngInternalInventory(sourceHost, 1);
        AppEngInternalInventory destination = new AppEngInternalInventory(destinationHost, 1);
        final AppEngInternalInventory collected = new AppEngInternalInventory(1);
        final IGrid grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "R7 identity-only grid";
                    default -> throw new AssertionError("Undo must not call grid services: " + method);
                });
        final PatternContainer target = new PatternContainer() {
            @Override public IGrid getGrid() { return grid; }
            @Override public boolean isVisibleInTerminal() { return true; }
            @Override public InternalInventory getTerminalPatternInventory() { return source; }
            @Override public PatternContainerGroup getTerminalGroup() { return null; }
        };
        final TianshuPatternUploadUndo undo = new TianshuPatternUploadUndo();
        final CompoundTag playerData = new CompoundTag();
        final TianshuPatternReturnBuffer returns = new TianshuPatternReturnBuffer(playerData);

        Fixture() {
            source.setItemDirect(0, paid.copy());
            source.setFilter(extractGate);
            undo.provider(target, 0, paid);
        }

        Observation attempt() {
            Result result = undo.undo(grid, destination, List.of(target), returns);
            return observe(result, source, destination, collected);
        }

        Observation observe(Result result, InternalInventory a, InternalInventory b, InternalInventory c) {
            return new Observation(result, physicalCount(a), physicalCount(b), physicalCount(c), undo.available());
        }

        int physicalCount(InternalInventory inventory) {
            int count = 0;
            for (int slot = 0; slot < inventory.size(); slot++) {
                // Count every physical stack, including an injected replacement or incorrect
                // item. Full tagged identity and reference identity are asserted separately.
                count += inventory.getStackInSlot(slot).getCount();
            }
            return count;
        }

        void clearFaults() {
            extractGate.armed = false;
            sourceHost.armed = false;
            destinationHost.armed = false;
            sourceHost.failBeforeNextEvents = false;
        }

        void collectReturnedItem() {
            // Move the actual returned stack out of the terminal before replay, so OCCUPIED
            // cannot conceal a reusable receipt. This is a third real physical inventory.
            collected.setItemDirect(0, destination.extractItem(0, 1, false));
        }

        Observation roundTripPhysicalInventories() {
            var tag = new CompoundTag();
            source.writeToNBT(tag, "provider");
            destination.writeToNBT(tag, "terminal");
            collected.writeToNBT(tag, "collected");
            var restoredSource = new AppEngInternalInventory(1);
            var restoredDestination = new AppEngInternalInventory(1);
            var restoredCollected = new AppEngInternalInventory(1);
            restoredSource.readFromNBT(tag, "provider");
            restoredDestination.readFromNBT(tag, "terminal");
            restoredCollected.readFromNBT(tag, "collected");
            return observe(null, restoredSource, restoredDestination, restoredCollected);
        }
    }

    private static ItemStack paidPatternToken() {
        var stack = new ItemStack(Items.PAPER);
        var tag = new CompoundTag();
        tag.putString("paid-pattern-id", "r7-one-physical-paid-pattern");
        tag.putString("recipe", "ae2lt:r7_callback_undo");
        tag.putBoolean("substitute", true);
        tag.putLong("encoded-setting", 100_000_000_000L);
        stack.setTag(tag);
        return stack;
    }

    private static ItemStack secondPaidPatternToken() {
        var stack = paidPatternToken();
        stack.getOrCreateTag().putString("paid-pattern-id", "r7-second-physical-paid-pattern");
        return stack;
    }

    private static final class CallbackFailure extends RuntimeException {
        CallbackFailure(String phase) { super("R7 injected callback failure: " + phase); }
        CallbackFailure(String phase, boolean writableStackTrace) {
            super("R7 injected callback failure: " + phase, null, true, writableStackTrace);
        }
    }

    private static final class ExtractGate implements IAEItemFilter {
        boolean armed;
        int exceptions;
        int physicalCountAtException = -1;

        @Override
        public boolean allowExtract(InternalInventory inventory, int slot, int amount) {
            if (armed) {
                armed = false;
                exceptions++;
                physicalCountAtException = inventory.getStackInSlot(slot).getCount();
                throw new CallbackFailure("provider before removal");
            }
            return true;
        }
    }

    private static final class ThrowingHost implements InternalInventoryHost {
        final String phase;
        boolean armed;
        int exceptions;
        int physicalCountAtException = -1;
        InternalInventory observedInventory;
        CompoundTag saved = new CompoundTag();
        Runnable onThrow = () -> { };
        Runnable onNextChange = () -> { };
        boolean failBeforeNextEvents;
        int eventsExceptions;
        Runnable beforeEventsFailure = () -> { };
        RuntimeException failureToThrow;
        RuntimeException lastThrown;

        ThrowingHost(String phase) { this.phase = phase; }
        @Override
        public void saveChanges() {
            if (observedInventory instanceof AppEngInternalInventory nativeInventory) {
                saved = new CompoundTag();
                nativeInventory.writeToNBT(saved, "inventory");
            }
        }

        int savedCount() {
            var restored = new AppEngInternalInventory(1);
            restored.readFromNBT(saved, "inventory");
            return restored.getStackInSlot(0).getCount();
        }

        @Override
        public boolean isClientSide() {
            if (failBeforeNextEvents) {
                failBeforeNextEvents = false;
                eventsExceptions++;
                beforeEventsFailure.run();
                throw new CallbackFailure(phase + " during post-write event admission");
            }
            return false;
        }

        @Override
        public void onChangeInventory(InternalInventory inventory, int slot) {
            observedInventory = inventory;
            var callback = onNextChange;
            onNextChange = () -> { };
            callback.run();
            if (armed) {
                armed = false;
                exceptions++;
                physicalCountAtException = inventory.getStackInSlot(slot).getCount();
                onThrow.run();
                lastThrown = failureToThrow != null ? failureToThrow : new CallbackFailure(phase);
                throw lastThrown;
            }
        }
    }

    private static final class TransferThenThrowInventory extends AppEngInternalInventory {
        final InternalInventory otherCustody;
        int exceptions;

        TransferThenThrowInventory(InternalInventoryHost host, InternalInventory otherCustody) {
            super(host, 1);
            this.otherCustody = otherCustody;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var removed = super.extractItem(slot, amount, simulate);
            if (!simulate && !removed.isEmpty()) {
                otherCustody.setItemDirect(0, removed);
                exceptions++;
                throw new CallbackFailure("unknown extraction moved the item before throwing");
            }
            return removed;
        }
    }

    private static final class BeforeWriteInventory extends AppEngInternalInventory {
        boolean failBeforeNextWrite;
        int exceptionsBeforeWrite;
        int physicalCountBeforeThrow = -1;
        Runnable beforeFailure = () -> { };

        BeforeWriteInventory(InternalInventoryHost host) { super(host, 1); }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            if (failBeforeNextWrite) {
                failBeforeNextWrite = false;
                exceptionsBeforeWrite++;
                physicalCountBeforeThrow = getStackInSlot(slot).getCount();
                beforeFailure.run();
                throw new CallbackFailure("destination before write");
            }
            super.setItemDirect(slot, stack);
        }
    }
}
