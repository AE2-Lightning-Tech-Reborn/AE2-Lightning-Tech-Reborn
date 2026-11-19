package com.moakiee.ae2lt.debug;

import java.math.BigInteger;
import java.util.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.moakiee.ae2lt.item.InfiniteStorageCellItem;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.thunderbolt.core.storage.cell.IndexedCellSummary;
import com.moakiee.thunderbolt.core.storage.cell.IndexedCellStorageSavedData;
import com.moakiee.thunderbolt.core.storage.cell.IndexedStorage;
import com.moakiee.thunderbolt.core.storage.cell.IndexedStorageCellInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered components, real powered-drive routing and world-persistence regressions. */
@GameTestHolder("ae2lt_infinite_cell")
@PrefixGameTestTemplate(false)
public final class InfiniteCellRegressionGameTests {
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void poweredDriveSummaryAndPersistence(GameTestHelper helper) {
        var pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, AEBlocks.DRIVE.block());
        helper.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        DriveBlockEntity drive = helper.getBlockEntity(pos);
        require(drive.getInternalInventory().insertItem(0,
                new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()), false).isEmpty(), "Drive rejected infinite cell");
        var fixture = new Fixture(helper, drive);
        helper.startSequence().thenIdle(40).thenExecute(fixture::initialize)
                .thenExecute(fixture::prepareActiveCells).thenIdle(10)
                .thenExecute(fixture::exerciseNetwork).thenExecute(fixture::verifyCompatibility).thenSucceed();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }

    private static AEKey nbtKey(int variant) {
        var stack = new ItemStack(Items.IRON_INGOT);
        var tag = new CompoundTag();
        tag.putInt("variant", variant);
        var bytes = new byte[1024];
        new Random(variant).nextBytes(bytes);
        tag.putByteArray("payload", bytes);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return AEItemKey.of(stack);
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final DriveBlockEntity drive;
        final Map<AEKey, BigInteger> ledger = new HashMap<>();
        final AEKey[] nbt = new AEKey[16384];
        final AEKey[] churn = new AEKey[1024];
        final List<UUID> extraCellIds = new ArrayList<>();
        final int activeCellCount = 8;
        record ActiveCell(int slot, AEKey[] keys, Map<AEKey, BigInteger> ledger, IndexedStorage storage) {}
        final List<ActiveCell> activeCells = new ArrayList<>();
        final AEKey[] custom = {FluxKey.of(EnergyType.FE), FluxKey.of(EnergyType.GTEU),
                LightningKey.HIGH_VOLTAGE, LightningKey.EXTREME_HIGH_VOLTAGE,
                AEFluidKey.of(Fluids.WATER), AEItemKey.of(Items.GOLD_INGOT)};
        MEStorage network;
        Fixture(GameTestHelper helper, DriveBlockEntity drive) { this.helper = helper; this.drive = drive; }

        void initialize() {
            require(drive.getMainNode().isOnline(), "Powered drive is not online");
            network = drive.getMainNode().getGrid().getStorageService().getInventory();
            for (int i = 0; i < nbt.length; i++) {
                nbt[i] = nbtKey(i);
                seed(nbt[i]);
            }
            for (int i = 0; i < churn.length; i++) churn[i] = nbtKey(20000 + i);
            var data = IndexedCellStorageSavedData.get(helper.getLevel().getServer());
            var type = net.minecraft.resources.ResourceLocation.parse("ae2lt:infinite_cell");
            // Persisted world cells outside the active grid must survive saves of the busy cell.
            {
                var id = UUID.randomUUID();
                extraCellIds.add(id);
                var storage = data.getOrCreateStorage(type, id, helper.getLevel().registryAccess());
                storage.enableArbitraryPrecision();
                for (int k = 0; k < 4096; k++) storage.insert(nbt[k], 1_000_000, Actionable.MODULATE);
                data.markStorageDirty(type, id, storage);
            }
            seed(AEItemKey.of(Items.STONE));
            for (var key : custom) seed(key);
        }

        void seed(AEKey key) {
            require(network.insert(key, 1_000_000, Actionable.MODULATE, SOURCE) == 1_000_000, "Incomplete seed: " + key);
            ledger.put(key, BigInteger.valueOf(1_000_000));
        }

        void prepareActiveCells() {
            for (int slot = 1; slot < activeCellCount; slot++) {
                var cell = new ItemStack(ModItems.INFINITE_STORAGE_CELL.get());
                var inv = (IndexedStorageCellInventory) StorageCells.getCellInventory(cell, null);
                require(inv != null, "Missing active cell handler");
                var keys = new AEKey[4096];
                var expected = new HashMap<AEKey, BigInteger>();
                for (int k = 0; k < keys.length; k++) {
                    keys[k] = nbtKey(50000 + slot * keys.length + k);
                    long amount = 1_000_000L + slot;
                    require(inv.insert(keys[k], amount, Actionable.MODULATE, SOURCE) == amount, "Active cell seed failed");
                    expected.put(keys[k], BigInteger.valueOf(amount));
                }
                inv.persist();
                require(drive.getInternalInventory().insertItem(slot, cell, false).isEmpty(), "Drive rejected active cell");
                activeCells.add(new ActiveCell(slot, keys, expected, inv.storage()));
            }
        }

        void exerciseNetwork() {
            var startMods = activeCells.stream().mapToLong(cell -> cell.storage().getModCount()).toArray();
            for (var key : ledger.keySet()) transfer(key);
            for (var active : activeCells) for (var key : active.keys()) transfer(key);
            for (var key : churn) {
                require(network.insert(key, 64, Actionable.MODULATE, SOURCE) == 64, "New key rejected");
                require(network.extract(key, 64, Actionable.MODULATE, SOURCE) == 64, "New key retained stock");
            }
            for (int i = 0; i < activeCells.size(); i++) {
                require(activeCells.get(i).storage().getModCount() > startMods[i], "Network did not route to every cell");
            }
            verifyLedger();
        }

        void transfer(AEKey key) {
            require(network.insert(key, 64, Actionable.SIMULATE, SOURCE) == 64, "Insertion simulation failed");
            require(network.insert(key, 64, Actionable.MODULATE, SOURCE) == 64, "Insertion failed");
            require(network.extract(key, 64, Actionable.SIMULATE, SOURCE) == 64, "Extraction simulation failed");
            require(network.extract(key, 64, Actionable.MODULATE, SOURCE) == 64, "Extraction failed");
        }

        void verifyLedger() {
            var cell = drive.getInternalInventory().getStackInSlot(0);
            var inv = (IndexedStorageCellInventory) StorageCells.getCellInventory(cell, null);
            require(inv != null, "Missing indexed cell handler");
            require(ledger.equals(inv.storage().snapshotExact()), "Network workload changed the exact ledger");
            inv.persist();
            require(new IndexedCellSummary(inv.storage().getTotalTypes(), inv.usedBytes())
                    .equals(InfiniteStorageCellItem.readSummary(cell)), "Stale main cell summary");
            var data = IndexedCellStorageSavedData.get(helper.getLevel().getServer())
                    .save(new CompoundTag(), helper.getLevel().registryAccess());
            var id = cell.get(DataComponents.CUSTOM_DATA).copyTag().getUUID("ae2lt:cell_id");
            var restored = new IndexedStorage();
            restored.load(data.getCompound("Stores").getCompound("ae2lt:infinite_cell")
                    .getCompound(id.toString()), helper.getLevel().registryAccess());
            require(ledger.equals(restored.snapshotExact()), "SavedData round trip changed custom keys or NBT");
            for (var extraId : extraCellIds) {
                var extra = new IndexedStorage();
                extra.load(data.getCompound("Stores").getCompound("ae2lt:infinite_cell")
                        .getCompound(extraId.toString()), helper.getLevel().registryAccess());
                require(extra.getTotalTypes() == 4096, "Save changed an idle cell's key count");
                for (int k = 0; k < 4096; k++) require(extra.getAmount(nbt[k]) == 1_000_000,
                        "Save changed an idle cell's quantity");
            }
            require(inv.usedBytes() == expectedUsedBytes(), "Incorrect mixed-key byte accounting");
            var ids = new HashSet<UUID>();
            ids.add(id);
            for (var active : activeCells) {
                var activeStack = drive.getInternalInventory().getStackInSlot(active.slot());
                var handler = (IndexedStorageCellInventory) StorageCells.getCellInventory(activeStack, null);
                require(handler != null && handler.storage() == active.storage(), "Mounted active cell changed storage");
                require(active.ledger().equals(handler.storage().snapshotExact()), "Active cell ledger changed");
                require(handler.usedBytes() == expectedUsedBytes(active.ledger()), "Active cell byte summary changed");
                require(new IndexedCellSummary(handler.storage().getTotalTypes(), handler.usedBytes())
                        .equals(InfiniteStorageCellItem.readSummary(activeStack)), "Stale active cell summary");
                var activeId = activeStack.get(DataComponents.CUSTOM_DATA).copyTag().getUUID("ae2lt:cell_id");
                require(ids.add(activeId), "Active cells share a UUID");
                var roundTrip = new IndexedStorage();
                roundTrip.load(data.getCompound("Stores").getCompound("ae2lt:infinite_cell")
                        .getCompound(activeId.toString()), helper.getLevel().registryAccess());
                require(active.ledger().equals(roundTrip.snapshotExact()), "SavedData changed an active cell");
            }
        }

        long expectedUsedBytes() {
            return expectedUsedBytes(ledger);
        }

        long expectedUsedBytes(Map<AEKey, BigInteger> expected) {
            var totals = new HashMap<AEKeyType, BigInteger>();
            expected.forEach((key, amount) -> totals.merge(key.getType(), amount, BigInteger::add));
            var bytes = BigInteger.valueOf(expected.size() * 8L);
            for (var e : totals.entrySet()) {
                var apb = BigInteger.valueOf(e.getKey().getAmountPerByte());
                bytes = bytes.add(e.getValue().add(apb).subtract(BigInteger.ONE).divide(apb));
            }
            return bytes.longValueExact();
        }

        void verifyCompatibility() {
            verifySummaryCompatibility();
            verifyGenericKeyFallbacks();
            // Exercise actual third-party keys across the primitive/exact boundary and back.
            var cell = drive.getInternalInventory().getStackInSlot(0);
            var inv = (IndexedStorageCellInventory) StorageCells.getCellInventory(cell, null);
            var wide = BigInteger.ONE.shiftLeft(512);
            for (var key : custom) {
                inv.insertBig(key, wide, Actionable.MODULATE, SOURCE);
                require(inv.extract(key, 123, Actionable.MODULATE, SOURCE) == 123, "Wide custom-key extraction failed");
                inv.insert(key, 123, Actionable.MODULATE, SOURCE);
                require(inv.extractBig(key, wide, Actionable.MODULATE, SOURCE).equals(wide), "Wide custom-key round trip failed");
            }
            verifyLedger();
            // A reloadable third-party conversion factor must invalidate incremental byte state.
            try {
                var units = com.glodblock.github.appflux.config.AFConfig.class.getDeclaredField("fluxPerByte");
                units.setAccessible(true);
                int previous = units.getInt(null);
                try {
                    units.setInt(null, Math.max(1, previous / 2));
                    require(inv.usedBytes() == expectedUsedBytes(), "Existing handler retained stale FE units");
                    verifyLedger();
                    inv.insert(custom[0], 123, Actionable.MODULATE, SOURCE);
                    inv.extract(custom[0], 123, Actionable.MODULATE, SOURCE);
                    require(inv.usedBytes() == expectedUsedBytes(), "FE transfer used stale byte remainders");
                    verifyLedger();
                } finally { units.setInt(null, previous); }
                require(inv.usedBytes() == expectedUsedBytes(), "Existing handler did not restore FE units");
                verifyLedger();
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }

        void verifyGenericKeyFallbacks() {
            var registries = helper.getLevel().registryAccess();
            var tags = new ArrayList<CompoundTag>();
            tags.add(nbtKey(19).toTagGeneric(registries));
            var removedComponent = new ItemStack(Items.DIAMOND_SWORD);
            removedComponent.remove(DataComponents.ATTRIBUTE_MODIFIERS);
            tags.add(AEItemKey.of(removedComponent).toTagGeneric(registries));
            var missingItem = tags.getFirst().copy();
            missingItem.putString("id", "absent_mod:missing_item");
            tags.add(missingItem);
            var badComponents = tags.getFirst().copy();
            badComponents.putString("components", "not a component map");
            tags.add(badComponents);
            var missingChannel = tags.getFirst().copy();
            missingChannel.putString(AEKey.TYPE_FIELD, "absent_mod:channel");
            tags.add(missingChannel);
            tags.add(AEFluidKey.of(Fluids.WATER).toTagGeneric(registries));
            for (int i = 0; i < tags.size(); i++) {
                var keyTag = tags.get(i);
                var expected = AEKey.CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                        keyTag).getOrThrow();
                if (i >= 2 && i <= 4) require(appeng.core.definitions.AEItems.MISSING_CONTENT.is(expected),
                        "Invalid key lost its missing-content placeholder");
                var root = new CompoundTag();
                var keys = new net.minecraft.nbt.ListTag();
                var entry = new CompoundTag();
                entry.put("key", keyTag);
                keys.add(entry);
                root.put("keys", keys);
                root.putLongArray("lo", new long[] {37});
                root.putLongArray("hi", new long[] {0});
                var storage = new IndexedStorage();
                storage.load(root, registries);
                require(Map.of(expected, BigInteger.valueOf(37)).equals(storage.snapshotExact()), "Generic key lost its amount");
                require(keyTag.equals(storage.persist(null, registries).getList("keys", 10).getCompound(0).getCompound("key")),
                        "Generic key lost its original encoded data");
            }
        }

        void verifySummaryCompatibility() {
            var stack = new ItemStack(ModItems.INFINITE_STORAGE_CELL.get());
            var item = (InfiniteStorageCellItem) stack.getItem();
            require(InfiniteStorageCellItem.readSummary(stack) == null, "Fresh shell gained a tooltip summary");
            var id = UUID.randomUUID();
            var legacy = new CompoundTag();
            legacy.putUUID("ae2lt:cell_id", id);
            legacy.putInt("ae2lt:types", 7);
            legacy.putLong("ae2lt:bytes", 888);
            legacy.putString("unrelated", "preserved");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));
            require(new IndexedCellSummary(7, 888).equals(InfiniteStorageCellItem.readSummary(stack)),
                    "Legacy summary is unreadable");
            require(legacy.equals(stack.get(DataComponents.CUSTOM_DATA).copyTag()), "Reading migrated legacy data");
            item.writeSummary(stack, new IndexedCellSummary(8, 999));
            var preserved = stack.get(DataComponents.CUSTOM_DATA);
            var tag = preserved.copyTag();
            require(!tag.contains("ae2lt:types") && !tag.contains("ae2lt:bytes"), "Legacy summary left stale fields");
            require(id.equals(tag.getUUID("ae2lt:cell_id")) && tag.getString("unrelated").equals("preserved"),
                    "Summary migration changed identity or unrelated NBT");
            var registries = helper.getLevel().registryAccess();
            for (var summary : List.of(new IndexedCellSummary(0, 0),
                    new IndexedCellSummary(Integer.MAX_VALUE, Long.MAX_VALUE))) {
                item.writeSummary(stack, summary);
                require(stack.get(DataComponents.CUSTOM_DATA) == preserved, "Summary update copied CUSTOM_DATA");
                var copy = stack.copy();
                require(summary.equals(InfiniteStorageCellItem.readSummary(copy)), "Copied item lost summary");
                var decoded = ItemStack.parse(registries, stack.save(registries)).orElseThrow();
                require(summary.equals(decoded.get(ModDataComponents.INFINITE_CELL_SUMMARY.get())),
                        "Summary persistence round trip failed");
                require(id.equals(decoded.get(DataComponents.CUSTOM_DATA).copyTag().getUUID("ae2lt:cell_id")),
                        "Item persistence changed UUID");
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
                try {
                    ItemStack.STREAM_CODEC.encode(buffer, stack);
                    var networkCopy = ItemStack.STREAM_CODEC.decode(buffer);
                    require(buffer.readableBytes() == 0 && summary.equals(InfiniteStorageCellItem.readSummary(networkCopy)),
                            "Summary network round trip failed");
                    require(id.equals(networkCopy.get(DataComponents.CUSTOM_DATA).copyTag().getUUID("ae2lt:cell_id")),
                            "Item network round trip changed UUID");
                } finally { buffer.release(); }
                item.writeSummary(stack, new IndexedCellSummary(1, 1));
                require(summary.equals(InfiniteStorageCellItem.readSummary(copy)), "Old item copy changed summary");
            }
            var liveStack = new ItemStack(ModItems.INFINITE_STORAGE_CELL.get());
            var live = new IndexedStorageCellInventory(liveStack, item, registries, () -> {});
            var key = AEItemKey.of(Items.IRON_INGOT);
            require(live.insert(key, 64, Actionable.MODULATE, SOURCE) == 64, "Summary fixture insertion failed");
            require(new IndexedCellSummary(1, live.usedBytes()).equals(InfiniteStorageCellItem.readSummary(liveStack)),
                    "Insert deferred summary until persist");
            var beforeSimulation = liveStack.get(ModDataComponents.INFINITE_CELL_SUMMARY.get());
            live.insert(key, 64, Actionable.SIMULATE, SOURCE);
            live.extract(key, 64, Actionable.SIMULATE, SOURCE);
            require(beforeSimulation.equals(InfiniteStorageCellItem.readSummary(liveStack)), "Simulation changed summary");
            require(live.extract(key, 32, Actionable.MODULATE, SOURCE) == 32, "Summary fixture extraction failed");
            require(new IndexedCellSummary(1, live.usedBytes()).equals(InfiniteStorageCellItem.readSummary(liveStack)),
                    "Extract deferred summary until persist");
            live.extract(key, 32, Actionable.MODULATE, SOURCE);
            require(new IndexedCellSummary(0, 0).equals(InfiniteStorageCellItem.readSummary(liveStack)), "Empty summary stale");
        }

    }
}
