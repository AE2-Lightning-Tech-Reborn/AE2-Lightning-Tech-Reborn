package com.moakiee.ae2lt.gametest;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.sync.BasePacket;
import appeng.core.sync.packets.CraftConfirmPlanPacket;
import appeng.menu.me.crafting.*;
import com.moakiee.ae2lt.crafting.big.BigDisplayAmounts;
import com.moakiee.ae2lt.crafting.big.BigStatusEntry;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;

/** Exercise the transformed Forge packet writers, not just independent codecs. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class BigForgePacketGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void exactStatusAndCpuListRoundTrip(GameTestHelper helper) {
        var n = BigInteger.TEN.pow(100);
        var key = AEItemKey.of(Items.STONE);
        var amounts = new BigStatusEntry.Amounts(n, n.add(BigInteger.ONE), n.add(BigInteger.TWO));
        var entry = new CraftingStatusEntry(7, key, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        ((BigStatusEntry) entry).ae2lt$amounts(amounts);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            entry.write(buffer);
            var restored = CraftingStatusEntry.read(buffer);
            helper.assertTrue(amounts.equals(((BigStatusEntry) restored).ae2lt$amounts()), "Exact status must round-trip");
            helper.assertTrue(buffer.readableBytes() == 0, "Status codec must consume its exact extension");
            var job = new GenericStack(key, Long.MAX_VALUE);
            BigDisplayAmounts.attach(job, n);
            var cpu = new CraftingStatusMenu.CraftingCpuListEntry(3, 1024, 1, null,
                    CpuSelectionMode.ANY, job, 0.5f, 100L);
            cpu.writeToPacket(buffer);
            var read = CraftingStatusMenu.CraftingCpuListEntry.readFromPacket(buffer);
            helper.assertTrue(n.equals(BigDisplayAmounts.get(read.currentJob())), "Exact CPU job amount must round-trip");
            helper.assertTrue(buffer.readableBytes() == 0, "CPU codec must consume its exact extension");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void exactConfirmConstructorRoundTrip(GameTestHelper helper) throws Exception {
        var key = AEItemKey.of(Items.STONE);
        var n = BigInteger.TEN.pow(100);
        var summary = new CraftingPlanSummary(Long.MAX_VALUE, true, List.of());
        var report = new ExactPlanReport(n, Map.of(key, new ExactPlanReport.Amounts(n, n, n)), true);
        ExactPlanReports.attach(summary, report);
        var packet = new CraftConfirmPlanPacket(summary);
        var field = BasePacket.class.getDeclaredField("p");
        field.setAccessible(true);
        var buffer = new FriendlyByteBuf(((FriendlyByteBuf) field.get(packet)).copy());
        try {
            buffer.readInt(); // Native AE2 packet id precedes the summary.
            var decoded = new CraftConfirmPlanPacket(buffer);
            var plan = CraftConfirmPlanPacket.class.getDeclaredField("plan");
            plan.setAccessible(true);
            helper.assertTrue(report.equals(ExactPlanReports.get(plan.get(decoded))), "Exact report must survive Forge constructors");
            helper.assertTrue(buffer.readableBytes() == 0, "Confirmation codec must consume its exact extension");
        } finally { buffer.release(); }
        helper.succeed();
    }
}
