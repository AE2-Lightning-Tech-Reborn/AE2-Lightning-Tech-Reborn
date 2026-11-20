package com.moakiee.ae2lt.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class OverloadedPatternCatalogGameTests {
    private OverloadedPatternCatalogGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void overloadedProviderAdvertisesItsCurrentPatterns(GameTestHelper helper) {
        verifyCatalog(helper, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get(), 36);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void extendedProviderAdvertisesPatternsBeyondTheFirstPage(GameTestHelper helper) {
        verifyCatalog(helper, ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get(), 37);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void vanillaProviderStillRebuildsItsCatalog(GameTestHelper helper) {
        verifyCatalog(helper, AEBlocks.PATTERN_PROVIDER.block(), 9);
    }

    private static void verifyCatalog(GameTestHelper helper, Block block, int minimumSlots) {
        var position = new BlockPos(2, 2, 2);
        helper.setBlock(position, block);
        var provider = (PatternProviderBlockEntity) helper.getBlockEntity(position);
        helper.runAfterDelay(2, () -> {
            var logic = provider.getLogic();
            var inventory = logic.getPatternInv();
            helper.assertTrue(inventory.size() >= minimumSlots, "Provider must expose the expected inventory");
            int slot = inventory.size() - 1;
            var input = new GenericStack[] { new GenericStack(AEItemKey.of(Items.COBBLESTONE), 2) };
            var output = new GenericStack(AEItemKey.of(Items.STONE), 3);
            inventory.setItemDirect(slot, PatternDetailsHelper.encodeProcessingPattern(
                    input, new GenericStack[] { output }));
            logic.updatePatterns();
            assertPattern(helper, logic, output);

            var replacement = new GenericStack(AEItemKey.of(Items.GLASS), 5);
            inventory.setItemDirect(slot, PatternDetailsHelper.encodeProcessingPattern(
                    input, new GenericStack[] { replacement }));
            logic.updatePatterns();
            assertPattern(helper, logic, replacement);

            inventory.setItemDirect(slot, ItemStack.EMPTY);
            logic.updatePatterns();
            helper.assertTrue(logic.getAvailablePatterns().isEmpty(), "Removed patterns must stop being advertised");
            helper.succeed();
        });
    }

    private static void assertPattern(GameTestHelper helper, PatternProviderLogic logic, GenericStack expected) {
        var patterns = logic.getAvailablePatterns();
        helper.assertTrue(patterns.size() == 1, "Provider must advertise exactly one current pattern");
        var output = patterns.get(0).getPrimaryOutput();
        helper.assertTrue(expected.equals(output), "Advertised output key and amount must match the current pattern");
    }
}
