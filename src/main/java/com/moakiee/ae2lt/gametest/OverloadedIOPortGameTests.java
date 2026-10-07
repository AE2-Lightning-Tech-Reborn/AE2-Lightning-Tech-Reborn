package com.moakiee.ae2lt.gametest;

import java.util.HashMap;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.storage.StorageCells;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEBlocks;
import appeng.util.SettingsFrom;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedIOPortBlockEntity;
import com.moakiee.ae2lt.item.OverloadedFilterComponentItem;
import com.moakiee.ae2lt.logic.transfer.OverloadedIOTransfer;
import com.moakiee.ae2lt.menu.OverloadedIOPortMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class OverloadedIOPortGameTests {
    private static final AEKey STONE = AEItemKey.of(Items.STONE);
    private static final IActionSource SOURCE = IActionSource.empty();

    private OverloadedIOPortGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void registryAndMatrixCapacity(GameTestHelper helper) {
        var recipeId = new ResourceLocation(AE2LightningTech.MODID,
                "lightning_assembly/overloaded_io_port");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(recipeId).isPresent(),
                "overloaded IO port assembly recipe must load");
        helper.assertTrue(ModMenuTypes.OVERLOADED_IO_PORT.get() == OverloadedIOPortMenu.TYPE,
                "overloaded IO port menu must register");
        var position = new BlockPos(1, 1, 1);
        helper.setBlock(position, ModBlocks.OVERLOADED_IO_PORT.get());
        var port = (OverloadedIOPortBlockEntity) helper.getBlockEntity(position);
        helper.assertTrue(port.getBatchLimit() == 4 && port.getTransferCap() == 8_388_608L,
                "base throughput must match the upstream port");
        port.getMatrixInventory().setItemDirect(0,
                new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get(), 8));
        helper.assertTrue(port.getMatrixCount() == 8 && port.getBatchLimit() == 16
                && port.getTransferCap() == Long.MAX_VALUE,
                "eight matrices must saturate the transfer amount safely");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void transferCapsAndRollbackConserveResources(GameTestHelper helper) {
        var source = new Store();
        var destination = new Store();
        source.amounts.put(STONE, 20_000_000L);
        int[] payments = {0};
        var moved = OverloadedIOTransfer.move(source, destination, STONE, SOURCE,
                () -> { payments[0]++; return true; }, 8_388_608L);
        helper.assertTrue(moved.inserted() == 8_388_608L && moved.remainder() == 0
                && source.amounts.get(STONE) == 11_611_392L && payments[0] == 1,
                "one capped batch must move exactly once and charge once");

        destination.rejectActual = true;
        var refunded = OverloadedIOTransfer.move(source, destination, STONE, SOURCE,
                () -> true, 8_388_608L);
        helper.assertTrue(refunded.inserted() == 0 && refunded.remainder() == 0
                && source.amounts.get(STONE) == 11_611_392L,
                "a rejected actual insert must refund to its source");

        source.rejectRefund = true;
        var pending = OverloadedIOTransfer.move(source, destination, STONE, SOURCE,
                () -> true, 8_388_608L);
        helper.assertTrue(pending.inserted() == 0 && pending.remainder() == 8_388_608L
                && source.amounts.get(STONE) == 3_222_784L,
                "unrefundable resources must remain owned by the port");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void filterMatcherUsesConfiguredKeysAndInversion(GameTestHelper helper) {
        var stack = new ItemStack(ModItems.OVERLOADED_FILTER_COMPONENT.get());
        var item = (OverloadedFilterComponentItem) stack.getItem();
        var dirt = AEItemKey.of(Items.DIRT);
        item.getConfigInventory(stack).setStack(0, new GenericStack(STONE, 1));
        var allow = item.createMatcher(stack);
        helper.assertTrue(allow.test(STONE) && !allow.test(dirt),
                "configured whitelist must match only permitted keys");
        item.getUpgrades(stack).setItemDirect(0, AEItems.INVERTER_CARD.stack());
        var deny = item.createMatcher(stack);
        helper.assertTrue(!deny.test(STONE) && deny.test(dirt),
                "inverter upgrade must reverse the configured keys");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 160)
    public static void realCellTransfersToPoweredDrive(GameTestHelper helper) {
        var position = new BlockPos(2, 2, 2);
        helper.setBlock(position.west(), AEBlocks.DRIVE.block());
        helper.setBlock(position.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(position, ModBlocks.OVERLOADED_IO_PORT.get());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getBlockEntity(position.west());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_256K.stack());
        var port = (OverloadedIOPortBlockEntity) helper.getBlockEntity(position);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(port.getMainNode().isActive(),
                        "port must join a powered ME grid"))
                .thenExecute(() -> {
                    var input = AEItems.ITEM_CELL_256K.stack();
                    var cell = StorageCells.getCellInventory(input, null);
                    helper.assertTrue(cell != null && cell.insert(STONE, 1_000_000L,
                            Actionable.MODULATE, SOURCE) == 1_000_000L,
                            "real input cell must accept one million items");
                    cell.persist();
                    port.getInternalInventory().setItemDirect(0, input);
                    port.tickingRequest(port.getMainNode().getNode(), 1);
                    long stored = port.getMainNode().getGrid().getStorageService().getInventory()
                            .extract(STONE, Long.MAX_VALUE, Actionable.SIMULATE, SOURCE);
                    helper.assertTrue(stored == 1_000_000L, "one batch must reach the physical drive");
                    helper.assertTrue(port.getInternalInventory().getStackInSlot(0).isEmpty(),
                            "finished input cell must eject");
                    var output = port.getInternalInventory().getStackInSlot(6);
                    var outputCell = StorageCells.getCellInventory(output, null);
                    helper.assertTrue(outputCell != null && outputCell.getStatus() == CellState.EMPTY,
                            "ejected cell must persist its empty state");
                    helper.succeed();
                });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void matricesPersistWithoutMemoryCardDuplication(GameTestHelper helper) {
        var position = new BlockPos(1, 1, 1);
        helper.setBlock(position, ModBlocks.OVERLOADED_IO_PORT.get());
        var port = (OverloadedIOPortBlockEntity) helper.getBlockEntity(position);
        var matrix = ModItems.LIGHTNING_COLLAPSE_MATRIX.get();
        port.getMatrixInventory().setItemDirect(0, new ItemStack(matrix, 8));

        var restored = new OverloadedIOPortBlockEntity(port.getBlockPos(), port.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadTag(port.saveWithFullMetadata());
        helper.assertTrue(restored.getMatrixCount() == 8 && restored.getTransferCap() == Long.MAX_VALUE,
                "all matrices must survive block entity persistence");

        var copied = new OverloadedIOPortBlockEntity(port.getBlockPos(), port.getBlockState());
        copied.setLevel(helper.getLevel());
        var settings = new CompoundTag();
        port.exportSettings(SettingsFrom.MEMORY_CARD, settings, null);
        copied.importSettings(SettingsFrom.MEMORY_CARD, settings, null);
        helper.assertTrue(copied.getMatrixInventory().isEmpty(),
                "memory cards must not duplicate installed matrices");

        helper.getLevel().destroyBlock(port.getBlockPos(), true);
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(port.getBlockPos()).inflate(1)).stream()
                .map(ItemEntity::getItem).filter(stack -> stack.is(matrix))
                .mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(dropped == 8, "breaking must drop matrices exactly once");
        helper.succeed();
    }

    private static final class Store implements StorageCell {
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private boolean rejectActual;
        private boolean rejectRefund;
        private boolean insert = true;

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
            if (!insert || (mode == Actionable.MODULATE && rejectActual)) {
                return 0;
            }
            if (mode == Actionable.MODULATE) {
                amounts.merge(key, amount, Long::sum);
            }
            return amount;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource actionSource) {
            long taken = Math.min(amount, amounts.getOrDefault(key, 0L));
            if (mode == Actionable.MODULATE && taken > 0) {
                amounts.compute(key, (ignored, oldAmount) -> oldAmount == taken ? null : oldAmount - taken);
                if (rejectRefund) {
                    insert = false;
                }
            }
            return taken;
        }

        @Override
        public void getAvailableStacks(KeyCounter counter) {
            amounts.forEach(counter::add);
        }

        @Override
        public Component getDescription() {
            return Component.literal("IO test storage");
        }

        @Override
        public CellState getStatus() {
            return amounts.isEmpty() ? CellState.EMPTY : CellState.NOT_EMPTY;
        }

        @Override
        public double getIdleDrain() {
            return 0;
        }

        @Override
        public void persist() {
        }
    }
}
