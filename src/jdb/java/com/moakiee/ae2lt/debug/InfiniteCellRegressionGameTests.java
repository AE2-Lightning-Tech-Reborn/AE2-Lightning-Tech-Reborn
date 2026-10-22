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
import com.moakiee.ae2lt.item.InfiniteStorageCellItem;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.thunderbolt.core.storage.cell.IndexedCellSummary;
import com.moakiee.thunderbolt.core.storage.cell.IndexedCellStorageSavedData;
import com.moakiee.thunderbolt.core.storage.cell.IndexedStorage;
import com.moakiee.thunderbolt.core.storage.cell.IndexedStorageCellInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Forge NBT summaries, real powered-drive routing and world-persistence regressions. */
@GameTestHolder("ae2lt_infinite_cell")
@PrefixGameTestTemplate(false)
public final class InfiniteCellRegressionGameTests {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final ReloadableType RELOADABLE = new ReloadableType();
    public static void registerTestChannel() { AEKeyTypes.register(RELOADABLE); }

    // The GTO baseline does not install Applied Flux. A registered test channel
    // exercises the same mutable amount-per-byte contract without adding a mod.
    private static final class ReloadableType extends AEKeyType {
        int units = 8;
        ReloadableType() {
            super(new net.minecraft.resources.ResourceLocation("ae2lt", "qa_reloadable"),
                    MutableUnitKey.class, net.minecraft.network.chat.Component.literal("QA reloadable units"));
        }
        @Override public int getAmountPerByte() { return units; }
        @Override public AEKey loadKeyFromTag(CompoundTag tag) { return new MutableUnitKey(tag.getInt("value")); }
        @Override public AEKey readFromPacket(net.minecraft.network.FriendlyByteBuf input) { return new MutableUnitKey(input.readInt()); }
    }

    private static final class MutableUnitKey extends AEKey {
        private final int value;
        MutableUnitKey(int value) { this.value = value; }
        @Override public AEKeyType getType() { return RELOADABLE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public Object getPrimaryKey() { return value; }
        @Override public net.minecraft.resources.ResourceLocation getId() { return RELOADABLE.getId(); }
        @Override public CompoundTag toTag() { var tag = new CompoundTag(); tag.putInt("value", value); return tag; }
        @Override public void writeToPacket(net.minecraft.network.FriendlyByteBuf output) { output.writeInt(value); }
        @Override public void addDrops(long amount, List<ItemStack> drops, net.minecraft.world.level.Level level, BlockPos pos) {}
        @Override protected net.minecraft.network.chat.Component computeDisplayName() { return net.minecraft.network.chat.Component.literal("QA " + value); }
        @Override public boolean equals(Object other) { return other instanceof MutableUnitKey key && key.value == value; }
        @Override public int hashCode() { return value; }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 1200)
    public static void poweredDriveSummaryAndPersistence(GameTestHelper helper) {
        var pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, AEBlocks.DRIVE.block());
        helper.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        DriveBlockEntity drive = (DriveBlockEntity) helper.getBlockEntity(pos);
        require(drive.getInternalInventory().insertItem(0,
                new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()), false).isEmpty(), "Drive rejected infinite cell");
        var fixture = new Fixture(helper, drive);
        helper.startSequence().thenIdle(40).thenExecute(fixture::initialize)
                .thenExecute(fixture::prepareActiveCells).thenIdle(10)
                .thenExecute(fixture::exerciseNetwork).thenExecute(fixture::verifyCompatibility).thenSucceed();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private static AEKey nbtKey(int variant) {
        var stack = new ItemStack(Items.IRON_INGOT);
        var tag = new CompoundTag();
        tag.putInt("variant", variant);
        var bytes = new byte[1024];
        new Random(variant).nextBytes(bytes);
        tag.putByteArray("payload", bytes);
        stack.setTag(tag);
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
        final AEKey[] custom = {new MutableUnitKey(0), new MutableUnitKey(1),
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
            var type = new net.minecraft.resources.ResourceLocation("ae2lt", "infinite_cell");
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
            var expectedGlobal = new HashMap<AEKey, BigInteger>(ledger);
            for (var active : activeCells) active.ledger().forEach((key, amount) -> expectedGlobal.merge(key, amount, BigInteger::add));
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
            // AE2-GTO can insert into one mounted cell and extract from another.
            // Check the independent total before accepting the resulting ownership,
            // then preserve each cell's settled ledger through all save/reload checks.
            var actualGlobal = new HashMap<AEKey, BigInteger>();
            var settled = new ArrayList<Map<AEKey, BigInteger>>();
            for (int slot = 0; slot < activeCellCount; slot++) {
                var handler = (IndexedStorageCellInventory) StorageCells.getCellInventory(drive.getInternalInventory().getStackInSlot(slot), null);
                var exact = handler.storage().snapshotExact();
                settled.add(exact);
                exact.forEach((key, amount) -> actualGlobal.merge(key, amount, BigInteger::add));
            }
            require(expectedGlobal.equals(actualGlobal), "GTO routing changed the independent global ledger");
            ledger.clear();
            ledger.putAll(settled.get(0));
            for (var active : activeCells) {
                active.ledger().clear();
                active.ledger().putAll(settled.get(active.slot()));
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
                    .save(new CompoundTag());
            var id = cell.getTag().getUUID("ae2lt:cell_id");
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
                var activeId = activeStack.getTag().getUUID("ae2lt:cell_id");
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
            // Exercise mixed native, LT and synthetic keys across the primitive/exact boundary.
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
            int previous = RELOADABLE.units;
            try {
                RELOADABLE.units = 17;
                require(inv.usedBytes() == expectedUsedBytes(), "Existing handler retained stale channel units");
                verifyLedger();
                inv.insert(custom[0], 123, Actionable.MODULATE, SOURCE);
                inv.extract(custom[0], 123, Actionable.MODULATE, SOURCE);
                require(inv.usedBytes() == expectedUsedBytes(), "Custom-channel transfer used stale byte remainders");
                verifyLedger();
            } finally { RELOADABLE.units = previous; }
            require(inv.usedBytes() == expectedUsedBytes(), "Existing handler did not restore channel units");
            verifyLedger();
        }

        void verifyGenericKeyFallbacks() {
            var registries = helper.getLevel().registryAccess();
            var enchantedTool = new ItemStack(Items.DIAMOND_SWORD);
            enchantedTool.getOrCreateTag().putInt("Damage", 17);
            for (var key : List.of(nbtKey(19), AEItemKey.of(enchantedTool), AEFluidKey.of(Fluids.WATER), custom[0], custom[2])) {
                var keyTag = key.toTagGeneric();
                require(key.equals(AEKey.fromTagGeneric(keyTag)), "Generic key failed Forge decoding");
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
                require(Map.of(key, BigInteger.valueOf(37)).equals(storage.snapshotExact()), "Generic key lost its amount");
                require(keyTag.equals(storage.persist(null, registries).getList("keys", 10).getCompound(0).getCompound("key")),
                        "Generic key lost its original encoded data");
            }
            // AE2-GTO 1.20.1 has no 1.21 missing-content placeholder or components codec.
            var missingChannel = nbtKey(19).toTagGeneric();
            missingChannel.putString("#c", "absent_mod:channel");
            require(AEKey.fromTagGeneric(missingChannel) == null, "Unknown Forge channel unexpectedly decoded");
        }

        void verifySummaryCompatibility() {
            var stack = new ItemStack(ModItems.INFINITE_STORAGE_CELL.get());
            var item = (InfiniteStorageCellItem) stack.getItem();
            require(InfiniteStorageCellItem.readSummary(stack) == null, "Fresh shell gained a tooltip summary");
            require(!stack.hasTag(), "Reading a fresh shell created NBT");
            var id = UUID.randomUUID();
            var legacy = new CompoundTag();
            legacy.putUUID("ae2lt:cell_id", id);
            legacy.putInt("ae2lt:types", 7);
            legacy.putLong("ae2lt:bytes", 888);
            legacy.putString("unrelated", "preserved");
            stack.setTag(legacy);
            require(new IndexedCellSummary(7, 888).equals(InfiniteStorageCellItem.readSummary(stack)), "Legacy summary is unreadable");
            require(legacy.equals(stack.getTag()), "Reading changed legacy data");
            var before = legacy.copy();
            var tooltips = new ArrayList<net.minecraft.network.chat.Component>();
            item.appendHoverText(stack, new com.moakiee.ae2lt.item.AE2LTItem.TooltipContext(helper.getLevel()),
                    tooltips, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
            require(tooltips.size() == 2 && before.equals(stack.getTag()), "Tooltip changed the cell NBT");
            for (var summary : List.of(new IndexedCellSummary(0, 0),
                    new IndexedCellSummary(Integer.MAX_VALUE, Long.MAX_VALUE))) {
                item.writeSummary(stack, summary);
                require(stack.getTag() == legacy, "Summary update copied Forge NBT");
                require(id.equals(legacy.getUUID("ae2lt:cell_id")) && legacy.getString("unrelated").equals("preserved"),
                        "Summary update changed identity or unrelated NBT");
                var copy = stack.copy();
                require(summary.equals(InfiniteStorageCellItem.readSummary(copy)), "Copied item lost summary");
                var decoded = ItemStack.of(stack.save(new CompoundTag()));
                require(summary.equals(InfiniteStorageCellItem.readSummary(decoded)), "Summary persistence round trip failed");
                require(id.equals(decoded.getTag().getUUID("ae2lt:cell_id")), "Item persistence changed UUID");
                var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                try {
                    buffer.writeItem(stack);
                    var networkCopy = buffer.readItem();
                    require(buffer.readableBytes() == 0 && summary.equals(InfiniteStorageCellItem.readSummary(networkCopy)),
                            "Summary network round trip failed");
                    require(id.equals(networkCopy.getTag().getUUID("ae2lt:cell_id")), "Item network round trip changed UUID");
                } finally { buffer.release(); }
                item.writeSummary(stack, new IndexedCellSummary(1, 1));
                require(summary.equals(InfiniteStorageCellItem.readSummary(copy)), "Old item copy changed summary");
            }
            var liveStack = new ItemStack(ModItems.INFINITE_STORAGE_CELL.get());
            var live = new IndexedStorageCellInventory(liveStack, item, helper.getLevel().registryAccess(), () -> {});
            var key = AEItemKey.of(Items.IRON_INGOT);
            require(live.insert(key, 64, Actionable.MODULATE, SOURCE) == 64, "Summary fixture insertion failed");
            require(new IndexedCellSummary(1, live.usedBytes()).equals(InfiniteStorageCellItem.readSummary(liveStack)),
                    "Insert deferred summary until persist");
            var beforeSimulation = liveStack.save(new CompoundTag());
            var modCount = live.storage().getModCount();
            live.insert(key, 64, Actionable.SIMULATE, SOURCE);
            live.extract(key, 64, Actionable.SIMULATE, SOURCE);
            require(beforeSimulation.equals(liveStack.save(new CompoundTag())) && modCount == live.storage().getModCount(),
                    "Simulation changed summary, identity or storage");
            require(live.extract(key, 32, Actionable.MODULATE, SOURCE) == 32, "Summary fixture extraction failed");
            require(new IndexedCellSummary(1, live.usedBytes()).equals(InfiniteStorageCellItem.readSummary(liveStack)),
                    "Extract deferred summary until persist");
            live.extract(key, 32, Actionable.MODULATE, SOURCE);
            require(new IndexedCellSummary(0, 0).equals(InfiniteStorageCellItem.readSummary(liveStack)), "Empty summary stale");
        }
    }
}
