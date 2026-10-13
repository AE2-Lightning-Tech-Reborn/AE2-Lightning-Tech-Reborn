package com.moakiee.ae2lt.logic.energy;

import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Run prepare then verify in separate JVMs with the same dedicated test world. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class WirelessEnergyRestartGameTests {
    @GameTest(template = "wireless_io_empty", timeoutTicks = 120)
    public static void worldLedgerSurvivesProcessRestart(GameTestHelper helper) {
        var id = UUID.fromString("00fe0000-2026-1013-0000-000000007331");
        var account = WirelessEnergyLedger.get(helper.getLevel().getServer()).account(id);
        String phase = System.getProperty("ae2lt.energy.restartPhase", "");
        if (phase.equals("prepare")) {
            helper.assertTrue(account.available() == 0 && account.uncertain() == 0, "restart fixture must start empty");
            account.credit(7331); account.quarantine(17);
        } else if (phase.equals("verify")) {
            helper.assertTrue(account.available() == 7331 && account.uncertain() == 17, "world ledger failed process restart");
            account.debit(7331); account.settle(17, 17);
        } else throw new AssertionError("Run with -PwirelessRestartPhase=prepare then verify");
        // Use a source outside the template reset area to test the real managed-node NBT as well.
        var level = helper.getLevel();
        var pos = new net.minecraft.core.BlockPos(1600, 64, 1600);
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        level.getChunkAt(pos);
        if (phase.equals("prepare")) {
            level.setBlockAndUpdate(pos, com.moakiee.ae2lt.registry.ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
        }
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(level.getBlockEntity(pos) instanceof com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity,
                    "persistent source block missing");
            var controller = (com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(pos);
            var node = controller.getMainNode().getNode();
            helper.assertTrue(node != null, "source node not restored");
            var owned = node.getGrid().getService(WirelessEnergyGridService.class).escrow(node);
            if (phase.equals("prepare")) {
                helper.assertTrue(owned.available() == 0, "source must start empty");
                owned.credit(991);
            } else {
                helper.assertTrue(owned.available() == 991, "node lost its claim during process restart");
                owned.debit(991);
                level.removeBlock(pos, false);
            }
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
            System.out.println("WIRELESS_ENERGY_RESTART_" + phase.toUpperCase() + "_PASS");
            helper.succeed();
        });
    }
}
