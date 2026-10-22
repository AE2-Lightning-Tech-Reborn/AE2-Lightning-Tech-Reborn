package com.moakiee.ae2lt.crafting.big;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import com.moakiee.thunderbolt.core.crafting.big.BigCraftingPlanner;
import com.moakiee.thunderbolt.core.crafting.planner.*;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BigCraftingPlanBytesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), null);
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void failedCertificationStillReportsExactDiagnosticDemand() {
        AEKey raw = AEItemKey.of(Items.GRAVEL);
        AEKey out = AEItemKey.of(Items.DIAMOND);
        var n = BigInteger.TEN.pow(100);
        var diagnostic = new ExactCraftPlan<AEKey>(Map.of(), Map.of(), Map.of(raw, n),
                Map.of(raw, n, out, BigInteger.ONE), false);
        var result = new BigCraftingPlanner.Result<AEKey>(null, Map.of(raw, n), false, diagnostic);
        var plan = new BigCraftingPlan(out, BigInteger.ONE, result, "uncertified");
        var report = ExactPlanReports.get(plan.summary());
        assertNotNull(report);
        assertEquals(n.add(BigInteger.valueOf(17)), report.bytes());
        assertTrue(plan.simulation());
        assertEquals(0, plan.bytes());
    }

    @Test
    void legacyResultWithoutDiagnosticKeepsItsExecutionBasedEstimate() {
        var raw = AEItemKey.of(Items.OAK_LOG);
        var out = AEItemKey.of(Items.OAK_PLANKS);
        var recipe = new TestRecipe(raw, out);
        var graph = CraftGraph.<AEKey>builder().stock(raw, 1).pattern(recipe.pattern()).build();
        var current = BigCraftingPlanner.plan(graph, out, BigInteger.valueOf(4), Map.of(raw, BigInteger.ONE));
        var legacy = new BigCraftingPlanner.Result<>(current.program(), current.missing(), current.verified());
        assertNull(legacy.diagnostic());
        assertEquals(BigInteger.valueOf(22), new BigCraftingPlan(out, BigInteger.valueOf(4), legacy, "").reportBytes());
    }

    @Test
    void confirmationUsesExactCostWhileUnboundedCpuReservesZeroBytes() {
        var raw = AEItemKey.of(Items.OAK_LOG);
        var out = AEItemKey.of(Items.OAK_PLANKS);
        var recipe = new TestRecipe(raw, out);
        for (var n : List.of(BigInteger.ONE, BigInteger.valueOf(Long.MAX_VALUE - 1),
                BigInteger.valueOf(Long.MAX_VALUE), BigInteger.ONE.shiftLeft(63),
                BigInteger.TEN.pow(100).add(BigInteger.valueOf(12345)))) {
            for (boolean stocked : List.of(true, false)) {
                var available = stocked ? n : BigInteger.ZERO;
                var graph = CraftGraph.<AEKey>builder().stockExact(raw, available).pattern(recipe.pattern()).build();
                var result = BigCraftingPlanner.plan(graph, out, n.multiply(BigInteger.valueOf(4)), Map.of(raw, available));
                var plan = new BigCraftingPlan(out, n.multiply(BigInteger.valueOf(4)), result, "");
                // AE2 items use eight items per byte: 4N output + N input + N firings + 16 overhead.
                assertEquals(8, raw.getType().getAmountPerByte());
                var expected = n.multiply(BigInteger.valueOf(6)).add(BigInteger.valueOf(16));
                assertEquals(0, plan.bytes(), "unbounded CPU reservation must not change");
                var summary = plan.summary();
                var report = ExactPlanReports.get(summary);
                assertNotNull(report);
                assertEquals(expected, report.bytes());
                assertEquals(expected.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(), summary.getUsedBytes());
                assertEquals(!stocked, summary.isSimulation());
                assertFalse(report.previewOnly());
                assertEquals(n.multiply(BigInteger.valueOf(4)), report.entries().get(out).crafting());
            }
        }
    }

    @Test
    void fluidFractionsRoundUpOnceAfterCombiningResources() {
        AEKey water = AEFluidKey.of(Fluids.WATER);
        AEKey lava = AEFluidKey.of(Fluids.LAVA);
        AEKey out = AEItemKey.of(Items.STONE);
        assertEquals(8000, water.getType().getAmountPerByte());
        var recipe = new ExactRecipe(out, List.of(CraftInput.of(water, 1), CraftInput.of(lava, 1)));
        for (var n : List.of(BigInteger.ONE, BigInteger.valueOf(499), BigInteger.valueOf(500),
                BigInteger.valueOf(501), BigInteger.TEN.pow(100).add(BigInteger.valueOf(7)))) {
            var stock = Map.of(water, n, lava, n);
            var graph = CraftGraph.<AEKey>builder().stockExact(water, n).stockExact(lava, n)
                    .pattern(recipe.pattern()).build();
            var result = BigCraftingPlanner.plan(graph, out, n, stock);
            assertTrue(result.executable());
            // N items + N firings + ceil(2N millibuckets / 1000) + three key headers.
            var expected = n.multiply(BigInteger.TWO)
                    .add(n.add(BigInteger.valueOf(499)).divide(BigInteger.valueOf(500)))
                    .add(BigInteger.valueOf(24));
            assertEquals(expected, new BigCraftingPlan(out, n, result, "").reportBytes());
        }
    }

    @Test
    void unchangedCatalystIsCountedOnceAcrossTwoRecipeStages() {
        AEKey raw = AEItemKey.of(Items.GRAVEL);
        AEKey intermediate = AEItemKey.of(Items.STONE);
        AEKey out = AEItemKey.of(Items.DIAMOND);
        AEKey catalyst = AEItemKey.of(Items.STICK);
        var first = new ExactRecipe(intermediate, List.of(CraftInput.of(raw, 1), CraftInput.returned(catalyst, 1)));
        var second = new ExactRecipe(out, List.of(CraftInput.of(intermediate, 1), CraftInput.returned(catalyst, 1)));
        for (var n : List.of(BigInteger.ONE, BigInteger.TEN.pow(100).add(BigInteger.ONE))) {
            var graph = CraftGraph.<AEKey>builder().stockExact(raw, n).stockExact(catalyst, BigInteger.ONE)
                    .pattern(first.pattern()).pattern(second.pattern()).build();
            var result = BigCraftingPlanner.plan(graph, out, n, Map.of(raw, n, catalyst, BigInteger.ONE));
            assertTrue(result.executable());
            // Three N-item demands, two N-firing stages, two N catalyst handbacks, one seed, four headers.
            assertEquals(n.multiply(BigInteger.valueOf(7)).add(BigInteger.valueOf(33)),
                    new BigCraftingPlan(out, n, result, "").reportBytes());
        }
    }

    private record ExactRecipe(AEKey out, List<CraftInput<AEKey>> inputs) implements BigCraftingRecipe {
        public AEItemKey definition() { return AEItemKey.of(Items.PAPER); }
        public List<Map<AEKey, Long>> slots() {
            return inputs.stream().map(input -> Map.of(input.key(), input.amount())).toList();
        }
        public CraftPattern<AEKey> pattern() { return new CraftPattern<>(out, 1, inputs, this); }
        public boolean matches(IPatternDetails current, Level level) { return false; }
        public CompoundTag save(HolderLookup.Provider registries) { throw new UnsupportedOperationException(); }
    }

    private record TestRecipe(AEKey raw, AEKey out) implements BigCraftingRecipe {
        public AEItemKey definition() { return AEItemKey.of(Items.PAPER); }
        public List<Map<AEKey, Long>> slots() { return List.of(Map.of(raw, 1L)); }
        public CraftPattern<AEKey> pattern() {
            return new CraftPattern<>(out, 4, List.of(CraftInput.of(raw, 1)), this);
        }
        public boolean matches(IPatternDetails current, Level level) { return false; }
        public CompoundTag save(HolderLookup.Provider registries) { throw new UnsupportedOperationException(); }
    }
}
