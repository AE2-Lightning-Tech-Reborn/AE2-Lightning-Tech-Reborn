package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.service.EnergyService;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Two actual JVMs; persistent controller lies outside the reset template. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class WirelessAeFallbackRestartGameTests {
    @GameTest(template = "wireless_io_empty", timeoutTicks = 180)
    public static void convertedAeAndUnresolvedWithdrawalKeepTheirNodeAcrossRestart(GameTestHelper h) {
        WirelessEnergySettings.runningNetworkFeFallback.set(true);
        String phase = System.getProperty("ae2lt.energy.aeRestartPhase", "");
        h.assertTrue(phase.equals("prepare") || phase.equals("verify"), "prepare/verify phase required");
        var level = h.getLevel(); var pos = new BlockPos(6400, 64, 6400);
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        level.getChunkAt(pos);
        if (phase.equals("prepare")) {
            level.setBlockAndUpdate(pos, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
            level.setBlockAndUpdate(pos.east(), AEBlocks.DRIVE.block().defaultBlockState());
            var drive = (DriveBlockEntity) level.getBlockEntity(pos.east());
            drive.getInternalInventory().setItemDirect(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_1k_cell"))));
        }
        // A restarted, unpowered grid must not bootstrap from FE. Supply independent
        // normal AE in both phases before testing the persisted fallback account.
        ((AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(pos)).setInternalCurrentPower(1000000);
        h.runAfterDelay(65, () -> {
            var c = (AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(pos);
            var node = c.getMainNode().getNode(); var grid = node.getGrid();
            var service = grid.getService(WirelessEnergyGridService.class);
            var energy = (EnergyService) grid.getEnergyService();
            var inventory = grid.getStorageService().getInventory();
            var balance = service.aeBalance(node);
            double rate = PowerUnit.FE.convertTo(PowerUnit.AE, 1);
            h.assertTrue(energy.isNetworkPowered() && node.isActive(), "persistent fixture not independently running");
            if (phase.equals("prepare")) {
                h.assertTrue(balance.available() == 0 && balance.pendingFeWithdrawal() == 0, "prepare requires fresh fixture");
                inventory.insert(AppFluxBridge.FE_KEY, 50000, Actionable.MODULATE, IActionSource.empty());
                energy.extractProviderPower(Double.MAX_VALUE, Actionable.MODULATE);
                double paid = energy.extractAEPower(rate / 4, Actionable.MODULATE, PowerMultiplier.ONE);
                h.assertTrue(Math.abs(paid - rate / 4) < 1e-10 && Math.abs(balance.available() - rate * .75) < 1e-10,
                        "real FE payment did not leave fractional AE");
                // Save exactly the production pre-withdrawal journal state. No receipt may be invented after restart.
                balance.beginWithdrawal(17);
                System.out.println("AE_FALLBACK_RESTART_PREPARE remainder=" + balance.saved() + " unresolved=17");
            } else {
                h.assertTrue(balance.pendingFeWithdrawal() == 17 && Math.abs(balance.available() - rate * .75) < 1e-10,
                        "node claim lost converted AE or unresolved journal");
                long before = inventory.extract(AppFluxBridge.FE_KEY, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
                h.assertTrue(before == 49999, "persistent real FE cell lost or repeated the original withdrawal");
                energy.extractProviderPower(Double.MAX_VALUE, Actionable.MODULATE);
                double paid = energy.extractAEPower(rate, Actionable.MODULATE, PowerMultiplier.ONE);
                h.assertTrue(paid == 0 && inventory.extract(AppFluxBridge.FE_KEY, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()) == before
                        && balance.pendingFeWithdrawal() == 17, "restart replayed unresolved extraction");
                System.out.println("AE_FALLBACK_RESTART_VERIFY remainder=" + balance.saved() + " cellFE=" + before + " unresolved=17");
                level.removeBlock(pos.east(), false); level.removeBlock(pos, false);
                level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
            }
            h.succeed();
        });
    }
}
