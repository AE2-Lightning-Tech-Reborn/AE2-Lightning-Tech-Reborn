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
public final class TianshuWirelessChargingGameTests {
    public static void wirelessTerminalsAcceptFe(GameTestHelper helper) {
        for (var item : List.of(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get(),
                ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get())) {
            var stack = new ItemStack(item);
            var name = Component.literal("Keep terminal data while charging");
            var target = GlobalPos.of(helper.getLevel().dimension(), BlockPos.ZERO);
            stack.set(DataComponents.CUSTOM_NAME, name);
            stack.set(AEComponents.WIRELESS_LINK_TARGET, target);
            stack.set(AEComponents.STORED_ENERGY, 0.0);
            var access = net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack);
            var energy = access.getCapability(Capabilities.Energy.ITEM);
            require(energy != null, "missing FE capability: " + item);
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                require(energy.insert(1000, transaction) == 1000, "simulated charge was rejected");
            }
            require(energy.getAmountAsLong() == 0, "aborted transaction mutated terminal charge");
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                require(energy.insert(1000, transaction) == 1000, "actual charge was rejected");
                transaction.commit();
            }
            require(energy.getAmountAsLong() == 1000, "committed FE did not reach the terminal");
            require(Math.abs(item.getAECurrentPower(stack) - PowerUnit.FE.convertTo(PowerUnit.AE, 1000)) < 0.001,
                    "FE was not converted to usable AE terminal energy");
            long remaining = energy.getCapacityAsLong() - energy.getAmountAsLong();
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                require(energy.insert(Integer.MAX_VALUE, transaction) == remaining, "overcharge capacity mismatch");
                transaction.commit();
            }
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                require(energy.insert(1, transaction) == 0, "full terminal accepted more energy");
                require(energy.extract(1000, transaction) == 0, "terminal leaked stored energy");
                transaction.commit();
            }
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
