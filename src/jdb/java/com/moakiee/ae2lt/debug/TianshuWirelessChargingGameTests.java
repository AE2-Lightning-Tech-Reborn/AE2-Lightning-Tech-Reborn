package com.moakiee.ae2lt.debug;

import java.util.List;
import appeng.api.config.PowerUnit;
import appeng.api.ids.AEComponents;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Exercises the registered external charging capability and its native simulation contract. */
@net.neoforged.neoforge.gametest.GameTestHolder("ae2lt_wireless_charging")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TianshuWirelessChargingGameTests {
    @net.minecraft.gametest.framework.GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void wirelessTerminalsAcceptFe(GameTestHelper helper) {
        for (var item : List.of(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get(),
                ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get())) {
            var stack = new ItemStack(item);
            var name = Component.literal("Keep terminal data while charging");
            var target = GlobalPos.of(helper.getLevel().dimension(), BlockPos.ZERO);
            stack.set(DataComponents.CUSTOM_NAME, name);
            stack.set(AEComponents.WIRELESS_LINK_TARGET, target);
            stack.set(AEComponents.STORED_ENERGY, 0.0);
            var energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            require(energy != null, "missing FE capability: " + item);
            require(energy.canReceive() && !energy.canExtract(), "terminal must accept charge only");
            require(energy.receiveEnergy(1000, true) == 1000, "simulated charge was rejected");
            require(energy.getEnergyStored() == 0, "simulation mutated terminal charge");
            require(energy.receiveEnergy(1000, false) == 1000, "actual charge was rejected");
            require(energy.getEnergyStored() == 1000, "FE storage did not increase");
            require(Math.abs(item.getAECurrentPower(stack) - PowerUnit.FE.convertTo(PowerUnit.AE, 1000)) < 0.001,
                    "FE was not converted to usable AE terminal energy");
            int remaining = energy.getMaxEnergyStored() - energy.getEnergyStored();
            require(energy.receiveEnergy(Integer.MAX_VALUE, false) == remaining, "overcharge capacity mismatch");
            require(energy.receiveEnergy(1, false) == 0, "full terminal accepted more energy");
            require(energy.extractEnergy(1000, false) == 0, "terminal leaked stored energy");
            require(stack.getCount() == 1 && name.equals(stack.get(DataComponents.CUSTOM_NAME))
                    && target.equals(stack.get(AEComponents.WIRELESS_LINK_TARGET)), "charging lost terminal data");
        }
        System.out.println("TIANSHU_CHARGING_PASS both terminals accept FE, preserve data and respect simulation/capacity");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
