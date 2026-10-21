package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CraftingMaterialReadinessTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), new net.minecraftforge.fml.loading.EarlyLoadingException("test", null, List.of()));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void alternativesAndNewComponentVariantsCanSatisfyOneSlotWithoutNativeMatching() {
        var oak = AEItemKey.of(Items.OAK_PLANKS);
        var birch = AEItemKey.of(Items.BIRCH_PLANKS);
        var pattern = pattern(input(2, new GenericStack(oak, 1), new GenericStack(birch, 1)));
        var inventory = inventory();
        inventory.insert(oak, 1, Actionable.MODULATE);
        assertEquals(Set.of(oak, birch), inventory.readiness.missing(pattern));
        var named = new ItemStack(Items.BIRCH_PLANKS);
        named.setHoverName(Component.literal("new physical variant"));
        inventory.insert(AEItemKey.of(named), 1, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(pattern).isEmpty());
        assertEquals(1, inventory.list.get(oak), "probe must not consume anything");
    }

    @Test
    void repeatedAnchorsDoNotMultiplyStockAndFixedSlotsShareTheSameStock() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var inventory = inventory();
        inventory.insert(iron, 1, Actionable.MODULATE);
        var duplicate = pattern(input(2, new GenericStack(iron, 1), new GenericStack(iron, 1)));
        assertEquals(Set.of(iron), inventory.readiness.missing(duplicate));
        var twoSlots = pattern(input(1, new GenericStack(iron, 1)), input(1, new GenericStack(iron, 1)));
        assertEquals(Set.of(iron), inventory.readiness.missing(twoSlots));
        inventory.insert(iron, 1, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(twoSlots).isEmpty());
    }

    @Test
    void fluidsUseTheirPhysicalUnitSize() {
        var water = AEFluidKey.of(Fluids.WATER);
        var pattern = pattern(input(2, new GenericStack(water, 1000)));
        var inventory = inventory();
        inventory.insert(water, 1999, Actionable.MODULATE);
        assertEquals(Set.of(water), inventory.readiness.missing(pattern));
        inventory.insert(water, 1, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(pattern).isEmpty());
    }

    @Test
    void optimisticReadinessDoesNotClaimNativeValidityAndUnknownInputsNeedRefresh() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var pattern = pattern(input(1, new GenericStack(iron, 1)));
        assertFalse(CraftingMaterialReadiness.stableInputs(pattern));
        var inventory = inventory();
        assertEquals(Set.of(iron), inventory.readiness.missing(pattern));
        inventory.insert(iron, 1, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(pattern).isEmpty(),
                "full native validity must be checked by the original dispatch path");
    }

    @Test
    void largeQuantitiesCannotWrapIntoAFalseShortage() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var inventory = inventory();
        inventory.insert(iron, Long.MAX_VALUE, Actionable.MODULATE);
        assertTrue(inventory.readiness.missing(
                pattern(input(Long.MAX_VALUE, new GenericStack(iron, 1)))).isEmpty());
    }

    @Test
    void waitingTasksShareOneFamilyCountUntilThatFamilyChanges() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var inventory = inventory();
        var tasks = new IPatternDetails[1000];
        for (int i = 0; i < tasks.length; i++) {
            tasks[i] = pattern(input(2, new GenericStack(iron, 1)));
            assertEquals(Set.of(iron), inventory.readiness.missing(tasks[i]));
        }
        assertEquals(1, inventory.fuzzyQueries, "one physical family lookup shared by 1000 tasks");
        inventory.insert(gold, 10, Actionable.MODULATE);
        for (var task : tasks) assertEquals(Set.of(iron), inventory.readiness.missing(task));
        assertEquals(1, inventory.fuzzyQueries, "unrelated material must not invalidate cached shortage");

        var named = new ItemStack(Items.IRON_INGOT);
        named.setHoverName(Component.literal("previously unseen variant"));
        inventory.insert(AEItemKey.of(named), 2, Actionable.MODULATE);
        for (var task : tasks) assertTrue(inventory.readiness.missing(task).isEmpty());
        assertEquals(2, inventory.fuzzyQueries, "new components invalidate only their primary family");
        inventory.clear();
        for (var task : tasks) assertEquals(Set.of(iron), inventory.readiness.missing(task));
        assertEquals(3, inventory.fuzzyQueries, "inventory removal must invalidate optimistic stock too");
    }

    private static CountingInventory inventory() {
        var readiness = new CraftingMaterialReadiness[1];
        var inventory = new CountingInventory(key -> readiness[0].onInventoryChange(key));
        readiness[0] = inventory.readiness = new CraftingMaterialReadiness(inventory);
        return inventory;
    }

    private static final class CountingInventory extends ListCraftingInventory {
        private CraftingMaterialReadiness readiness;
        private int fuzzyQueries;

        private CountingInventory(java.util.function.Consumer<AEKey> listener) {
            super(listener::accept);
        }

        @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
            fuzzyQueries++;
            return super.findFuzzyTemplates(key);
        }

        @Override public long extract(AEKey key, long amount, Actionable mode) {
            throw new AssertionError("readiness must not enter native extraction");
        }
    }

    private static IPatternDetails.IInput input(long multiplier, GenericStack... choices) {
        return new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return choices; }
            public long getMultiplier() { return multiplier; }
            public boolean isValid(AEKey key, Level level) {
                throw new AssertionError("shortage proof must not invoke dynamic matching");
            }
            public AEKey getRemainingKey(AEKey key) {
                throw new AssertionError("shortage proof must not compute containers");
            }
        };
    }

    private static IPatternDetails pattern(IPatternDetails.IInput... inputs) {
        return new IPatternDetails() {
            public IInput[] getInputs() { return inputs; }
            public GenericStack[] getOutputs() { return new GenericStack[0]; }
            public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        };
    }
}
