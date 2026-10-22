package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import com.moakiee.thunderbolt.core.crafting.big.BigCraftingPlanner;
import com.moakiee.thunderbolt.core.crafting.planner.*;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Summary and packet path in the actual GTO ModLauncher environment. */
@GameTestHolder("ae2lt_report")
@PrefixGameTestTemplate(false)
public final class BigCraftingReportGameTests {
    @GameTest(templateNamespace = "ae2lt_global_batch", template = "empty")
    public static void stockedPlansKeepExactReportBytes(GameTestHelper helper) { check(helper, true); }

    @GameTest(templateNamespace = "ae2lt_global_batch", template = "empty")
    public static void simulatedPlansKeepExactReportBytes(GameTestHelper helper) { check(helper, false); }

    private static void check(GameTestHelper helper, boolean stocked) {
        var raw = AEItemKey.of(Items.OAK_LOG);
        var out = AEItemKey.of(Items.OAK_PLANKS);
        var recipe = new Recipe(raw, out);
        for (var n : List.of(BigInteger.ONE, BigInteger.valueOf(Long.MAX_VALUE - 1),
                BigInteger.valueOf(Long.MAX_VALUE), BigInteger.ONE.shiftLeft(63),
                BigInteger.TEN.pow(100).add(BigInteger.valueOf(12345)))) {
            var have = stocked ? n : BigInteger.ZERO;
            var graph = CraftGraph.<AEKey>builder().stockExact(raw, have).pattern(recipe.pattern()).build();
            var exact = BigCraftingPlanner.plan(graph, out, n.multiply(BigInteger.valueOf(4)), Map.of(raw, have));
            var plan = new BigCraftingPlan(out, n.multiply(BigInteger.valueOf(4)), exact, "");
            var summary = plan.summary();
            var report = ExactPlanReports.get(summary);
            var expected = n.multiply(BigInteger.valueOf(6)).add(BigInteger.valueOf(16));
            helper.assertTrue(plan.bytes() == 0, "unbounded CPU reservation stays zero");
            helper.assertTrue(report != null && report.bytes().equals(expected), "exact report byte cost");
            helper.assertTrue(!report.previewOnly() && summary.isSimulation() == !stocked, "submission and simulation flags");
            helper.assertTrue(summary.getUsedBytes() == expected.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(), "saturated native summary");
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                report.write(buffer);
                var decoded = ExactPlanReport.read(buffer);
                helper.assertTrue(decoded.equals(report), "exact report packet round trip");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    private record Recipe(AEKey raw, AEKey out) implements BigCraftingRecipe {
        public AEItemKey definition() { return AEItemKey.of(Items.PAPER); }
        public List<Map<AEKey, Long>> slots() { return List.of(Map.of(raw, 1L)); }
        public CraftPattern<AEKey> pattern() { return new CraftPattern<>(out, 4, List.of(CraftInput.of(raw, 1)), this); }
        public boolean matches(IPatternDetails current, Level level) { return false; }
        public CompoundTag save(HolderLookup.Provider registries) { throw new UnsupportedOperationException(); }
    }
}
