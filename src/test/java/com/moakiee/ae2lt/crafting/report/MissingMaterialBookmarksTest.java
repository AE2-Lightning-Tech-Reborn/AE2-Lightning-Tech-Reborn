package com.moakiee.ae2lt.crafting.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MissingMaterialBookmarksTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void absentAndEmptyPlansHaveNoBookmarks() {
        assertFalse(MissingMaterialBookmarks.hasMissing(null));
        assertTrue(MissingMaterialBookmarks.keys(null).isEmpty());
        var plan = new CraftingPlanSummary(0, true, List.of());
        assertFalse(MissingMaterialBookmarks.hasMissing(plan));
        assertTrue(MissingMaterialBookmarks.keys(plan).isEmpty());
    }

    @Test
    void ordinaryPlanSelectsOnlyMissingItemsAndFluidsAndDeduplicates() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var plan = new CraftingPlanSummary(0, true, List.of(
                new CraftingPlanSummaryEntry(iron, 3, 10, 0),
                new CraftingPlanSummaryEntry(gold, 0, 0, 100),
                new CraftingPlanSummaryEntry(water, 1, 0, 0),
                new CraftingPlanSummaryEntry(iron, 2, 0, 0)));

        assertTrue(MissingMaterialBookmarks.hasMissing(plan));
        assertEquals(List.of(iron, water), MissingMaterialBookmarks.keys(plan));
    }

    @Test
    void storedAndCraftedMaterialsAloneDoNotEnableBookmarks() {
        var plan = new CraftingPlanSummary(0, false, List.of(
                new CraftingPlanSummaryEntry(AEItemKey.of(Items.IRON_INGOT), 0, 100, 200)));

        assertFalse(MissingMaterialBookmarks.hasMissing(plan));
        assertTrue(MissingMaterialBookmarks.keys(plan).isEmpty());
    }

    @Test
    void nonItemAndNonFluidResourcesDoNotEnableBookmarks() {
        var plan = new CraftingPlanSummary(0, true, List.of(
                new CraftingPlanSummaryEntry(LightningKey.HIGH_VOLTAGE, 10, 0, 0)));

        assertFalse(MissingMaterialBookmarks.hasMissing(plan));
        assertTrue(MissingMaterialBookmarks.keys(plan).isEmpty());
        ExactPlanReports.attach(plan, new ExactPlanReport(BigInteger.ZERO,
                Map.of(LightningKey.HIGH_VOLTAGE, amounts(BigInteger.ONE.shiftLeft(128))), false));
        assertFalse(MissingMaterialBookmarks.hasMissing(plan));
        assertTrue(MissingMaterialBookmarks.keys(plan).isEmpty());
    }

    @Test
    void exactMissingAmountsTakePrecedenceOverProjectedLongs() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var plan = new CraftingPlanSummary(0, true, List.of(
                new CraftingPlanSummaryEntry(iron, 0, 0, 0),
                new CraftingPlanSummaryEntry(gold, Long.MAX_VALUE, 0, 0)));
        ExactPlanReports.attach(plan, new ExactPlanReport(BigInteger.ZERO, Map.of(
                iron, amounts(BigInteger.ONE.shiftLeft(200)),
                gold, amounts(BigInteger.ZERO)), false));

        assertTrue(MissingMaterialBookmarks.hasMissing(plan));
        assertEquals(List.of(iron), MissingMaterialBookmarks.keys(plan));
    }

    @Test
    void exactDiagnosticReportCanSupplyMissingKeysWithoutSummaryEntries() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var plan = new CraftingPlanSummary(0, true, List.of());
        ExactPlanReports.attach(plan, new ExactPlanReport(BigInteger.ZERO, Map.of(
                iron, amounts(BigInteger.ONE),
                water, amounts(BigInteger.ONE.shiftLeft(128))), true));

        assertTrue(MissingMaterialBookmarks.hasMissing(plan));
        assertEquals(Set.of(iron, water), Set.copyOf(MissingMaterialBookmarks.keys(plan)));
    }

    @Test
    void exactZeroMissingAmountsDisableBookmarks() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var plan = new CraftingPlanSummary(0, true, List.of(
                new CraftingPlanSummaryEntry(iron, 10, 0, 0)));
        ExactPlanReports.attach(plan, new ExactPlanReport(BigInteger.ZERO,
                Map.of(iron, amounts(BigInteger.ZERO)), false));

        assertFalse(MissingMaterialBookmarks.hasMissing(plan));
        assertTrue(MissingMaterialBookmarks.keys(plan).isEmpty());
    }

    private static ExactPlanReport.Amounts amounts(BigInteger missing) {
        return new ExactPlanReport.Amounts(BigInteger.ZERO, missing, BigInteger.ZERO);
    }
}
