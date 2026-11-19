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
    @GameTest(template = "wireless_io_empty", timeoutTicks = 200)
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
            var probeControllerPos = pos.east(8);
            var targetPos = pos.east(11);
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(UUID.fromString("00fe0000-2026-1013-0000-000000000041"), "RestartProbe"));
            player.setPos(targetPos.getX(), targetPos.getY(), targetPos.getZ() - 1);
            if (phase.equals("prepare")) {
                level.setBlockAndUpdate(probeControllerPos, com.moakiee.ae2lt.registry.ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
                level.setBlockAndUpdate(probeControllerPos.east(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                level.setBlockAndUpdate(probeControllerPos.west(), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
                var drive = (appeng.blockentity.storage.DriveBlockEntity) level.getBlockEntity(probeControllerPos.west());
                drive.getInternalInventory().setItemDirect(0, new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.parse("appflux:fe_1k_cell"))));
                level.setBlockAndUpdate(targetPos, WirelessEnergyTestMachine.BLOCK.defaultBlockState());
                var c = (com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(probeControllerPos);
                var frequency = com.moakiee.ae2lt.grid.WirelessFrequencyManager.get().createFrequency(player, "Restart", 0xffffff,
                        com.moakiee.ae2lt.grid.FrequencySecurityLevel.PRIVATE, "");
                c.setFrequency(frequency.getId());
                helper.runAfterDelay(35, () -> {
                    c.getMainNode().getGrid().getStorageService().getInventory().insert(AppFluxBridge.FE_KEY, 50000,
                            appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
                    var target = (WirelessEnergyTestMachine) level.getBlockEntity(targetPos);
                    target.afterReceive = () -> {
                        helper.assertTrue(WirelessEnergyAdmissions.get(level.getServer()).inspect(level, targetPos, player.getUUID()).state()
                                == WirelessEnergyAdmissions.State.WAITING, "prepare did not stop during observation");
                        target.setChanged();
                        System.out.println("WIRELESS_ENERGY_RESTART_PREPARE_PASS pendingProbe=100");
                        helper.succeed(); // GameTestServer saves and exits before the next machine tick.
                    };
                    WirelessEnergyAdmissions.get(level.getServer()).queue(player, frequency.getId(), level, targetPos, net.minecraft.core.Direction.NORTH);
                });
            } else {
                var c = (com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(probeControllerPos);
                var target = (WirelessEnergyTestMachine) level.getBlockEntity(targetPos);
                var admissions = WirelessEnergyAdmissions.get(level.getServer());
                var attempt = admissions.inspect(level, targetPos, player.getUUID());
                helper.assertTrue(attempt != null && attempt.state() == WirelessEnergyAdmissions.State.UNVERIFIED
                        && attempt.offered() == 100 && attempt.accepted() == 100, "restart replayed or approved pending probe");
                admissions.queue(player, c.getFrequencyId(), level, targetPos, net.minecraft.core.Direction.NORTH);
                helper.runAfterDelay(10, () -> {
                    helper.assertTrue(target.received == 100 && !WirelessEnergyEndpoints.get(level.getServer()).isBound(level, targetPos, c.getFrequencyId()),
                            "restart sent a second probe or continuous power");
                    level.removeBlock(targetPos, false); level.removeBlock(probeControllerPos, false);
                    level.removeBlock(probeControllerPos.east(), false); level.removeBlock(probeControllerPos.west(), false);
                    level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
                    System.out.println("WIRELESS_ENERGY_RESTART_VERIFY_PASS pendingProbeNotReplayed=100");
                    helper.succeed();
                });
            }
        });
    }
}
