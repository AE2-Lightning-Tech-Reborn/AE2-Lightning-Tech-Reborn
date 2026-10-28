package com.moakiee.ae2lt.gametest;

import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedIOPortBlockEntity;
import com.moakiee.ae2lt.item.OverloadedFilterComponentItem;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class OverloadedIOPortUpgradeGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void matrixLimitsAndInventoryFilters(GameTestHelper helper) {
        var port = port(helper, new BlockPos(2, 2, 2));
        helper.assertTrue(port.getBatchLimit() == 4 && port.getTransferCap() == 8_388_608,
                "Default transfer must have the upstream bounded capacity");
        helper.assertTrue(!port.getMatrixInventory().addItems(new ItemStack(Items.STONE)).isEmpty(),
                "Matrix slot must reject unrelated items");
        helper.assertTrue(!port.getFilterInventory().addItems(new ItemStack(Items.STONE)).isEmpty(),
                "Filter slot must reject unrelated items");
        for (int count = 1; count <= OverloadedIOPortBlockEntity.MAX_MATRICES; count++) {
            port.getMatrixInventory().setItemDirect(0, new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get(), count));
            long expected = count == 8 ? Long.MAX_VALUE : 1L << (23 + 5 * count);
            helper.assertTrue(port.getBatchLimit() == Math.min(16, 4 + 2 * count)
                            && port.getTransferCap() == expected && port.getTransferCap() > 0,
                    "Matrix throughput must saturate without overflowing at " + count);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void filterAndMatricesSurviveSaveAndRemoval(GameTestHelper helper) {
        var port = port(helper, new BlockPos(2, 2, 2));
        var stack = new ItemStack(ModItems.OVERLOADED_FILTER_COMPONENT.get());
        var item = (OverloadedFilterComponentItem) stack.getItem();
        item.getConfigInventory(stack).setStack(0, new appeng.api.stacks.GenericStack(AEItemKey.of(Items.STONE), 1));
        port.getFilterInventory().setItemDirect(0, stack);
        port.getMatrixInventory().setItemDirect(0, new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get(), 3));
        var data = new CompoundTag();
        port.saveAdditional(data);
        var restored = port(helper, new BlockPos(4, 2, 2));
        restored.loadTag(data);
        helper.assertTrue(restored.getMatrixCount() == 3 && restored.getBatchLimit() == 10,
                "Matrix count must survive NBT restoration");
        var matcher = item.createMatcher(restored.getFilterInventory().getStackInSlot(0));
        helper.assertTrue(matcher.test(AEItemKey.of(Items.STONE)) && !matcher.test(AEItemKey.of(Items.DIRT)),
                "Filter rules must survive NBT restoration");
        var drops = new java.util.ArrayList<ItemStack>();
        restored.addAdditionalDrops(helper.getLevel(), restored.getBlockPos(), drops);
        helper.assertTrue(drops.stream().filter(s -> s.is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()))
                        .mapToInt(ItemStack::getCount).sum() == 3
                        && drops.stream().filter(s -> s.is(ModItems.OVERLOADED_FILTER_COMPONENT.get())).count() == 1,
                "Removal must return both upgrade inventories once");
        restored.clearContent();
        helper.assertTrue(restored.getMatrixCount() == 0 && restored.getFilterInventory().isEmpty(),
                "Cleared contents must not be dropped a second time");
        helper.succeed();
    }

    private static OverloadedIOPortBlockEntity port(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.OVERLOADED_IO_PORT.get());
        return (OverloadedIOPortBlockEntity) helper.getBlockEntity(pos);
    }

    private OverloadedIOPortUpgradeGameTests() {}
}
