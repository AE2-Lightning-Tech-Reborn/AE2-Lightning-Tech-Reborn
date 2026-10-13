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
    @GameTest(template = "wireless_io_empty")
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
        System.out.println("WIRELESS_ENERGY_RESTART_" + phase.toUpperCase() + "_PASS");
        helper.succeed();
    }
}
