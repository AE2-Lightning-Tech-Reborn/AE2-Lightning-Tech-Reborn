package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_neoeco")
@PrefixGameTestTemplate(false)
public final class NeoEcoMigrationGameTests {
    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty")
    public static void actualCoordinatorExcludesCompetingMigrationAndReleasesOnClose(GameTestHelper h) {
        Object grid = new Object(), first = new Object(), second = new Object();
        var lease = EcoMigrationLease.acquire(grid, first);
        h.assertTrue(lease != null && lease.owned(), "real NeoECO migration lease acquired");
        h.assertTrue(EcoMigrationLease.acquire(grid, second) == null, "competing operation excluded");
        lease.close();
        h.assertTrue(!lease.owned(), "closed lease is no longer held");
        try (var next = EcoMigrationLease.acquire(grid, second)) {
            h.assertTrue(next != null && next.owned(), "real coordinator allows next operation");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty")
    public static void actualPatternBusExposesPhysicalSlotsAndPreservesEncodedPattern(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 1, 1), BuiltInRegistries.BLOCK.get(new ResourceLocation("neoecoae:crafting_pattern_bus")));
        var bus = h.getBlockEntity(new BlockPos(1, 1, 1));
        h.assertTrue(bus != null && PatternMigrationSources.priority(bus.getClass()) == 0, "real ECO bus has explicit adapter");
        h.assertTrue(PatternMigrationSources.ecoBusy(bus), "unformed bus cannot be migrated");
        var inventory = (InternalInventory) MigrationReflection.call(bus, "getPatternSlotInventory");
        var physical = (InternalInventory) MigrationReflection.field(bus, "inventory");
        var raw = (CraftingRecipe) h.getLevel().getRecipeManager().byKey(new ResourceLocation("minecraft:oak_planks")).orElseThrow();
        var slots = new ItemStack[9]; Arrays.fill(slots, ItemStack.EMPTY); slots[0] = new ItemStack(Items.OAK_LOG);
        var encoded = PatternDetailsHelper.encodeCraftingPattern(raw, slots, new ItemStack(Items.OAK_PLANKS, 4), false, false);
        physical.setItemDirect(0, encoded.copy());
        h.assertTrue(ItemStack.isSameItemSameTags(encoded, inventory.getStackInSlot(0)), "adapter reads physical encoded pattern");
        MigrationReflection.call(bus, "beginPatternBatch");
        try {
            var preview = inventory.extractItem(0, 1, true);
            h.assertTrue(ItemStack.isSameItemSameTags(encoded, preview) && physical.getStackInSlot(0).getCount() == 1,
                    "simulated extraction is read-only");
            var extracted = inventory.extractItem(0, 1, false);
            h.assertTrue(ItemStack.isSameItemSameTags(encoded, extracted) && physical.getStackInSlot(0).isEmpty(),
                    "extraction transfers one real pattern with NBT");
            h.assertTrue(inventory.insertItem(0, extracted, false).isEmpty(), "rollback restores pattern");
            h.assertTrue(ItemStack.isSameItemSameTags(encoded, physical.getStackInSlot(0)), "rollback preserves NBT");
        } finally { MigrationReflection.call(bus, "endPatternBatch"); }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_machine_recharge", template = "empty")
    public static void ecoSelectsThePublicForgeAccountingContract(GameTestHelper h) throws Exception {
        Class<?> bridge = Class.forName("cn.dancingsnow.neoecoae.compat.thunderbolt.ECOOverloadCpuAccountingBridge");
        var fallback = bridge.getDeclaredField("RELEASED"); fallback.setAccessible(true);
        h.assertTrue(fallback.get(null) == null, "NeoECO uses the public API instead of inspecting released CPU internals");
        for (String name : new String[]{"PREPARE_WITH_WAITING", "STRICT", "CLAIM", "SAVE", "LOAD", "CLEAR"}) {
            var method = bridge.getDeclaredField(name); method.setAccessible(true);
            h.assertTrue(method.get(null) != null, "NeoECO resolved accounting method " + name);
        }
        h.succeed();
    }
}
