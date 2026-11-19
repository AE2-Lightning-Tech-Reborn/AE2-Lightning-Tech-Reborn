package com.moakiee.ae2lt.crafting.big;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
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
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BigCraftingPlanBytesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
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
