package com.moakiee.ae2lt.logic.provider;

import java.util.SplittableRandom;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_stress")
@PrefixGameTestTemplate(false)
public final class ReturnInventoryLimitGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void slotStorageAndGuiReceiptsConserveNearLimitResources(GameTestHelper helper) {
        var key = LightningKey.HIGH_VOLTAGE;
        var inv = UnlimitedReturnInventory.create(() -> {}, null);
        var random = new SplittableRandom(20261004L);
        for (int round = 0; round < 10_000; round++) {
            long stored = Long.MAX_VALUE - random.nextLong(10_000);
            long request = random.nextBoolean() ? Long.MAX_VALUE : random.nextLong(1, 20_000);
            long expected = Math.min(request, Long.MAX_VALUE - stored);
            inv.setStack(0, new GenericStack(key, stored));
            helper.assertTrue(inv.insert(0, key, request, Actionable.SIMULATE) == expected
                    && inv.getAmount(0) == stored, "Simulation receipt changed ownership at round " + round);
            long accepted = round % 2 == 0 ? inv.insert(0, key, request, Actionable.MODULATE)
                    : inv.insert(key, request, Actionable.MODULATE, IActionSource.empty());
            helper.assertTrue(accepted == expected && inv.getAmount(0) == stored + expected,
                    "Modulated receipt or ownership overflowed at round " + round);
        }
        var controller = new OverloadedReturnInventoryController(72, () -> {}, null);
        controller.full().setStack(0, new GenericStack(key, Long.MAX_VALUE - 7));
        controller.copyFullToPage();
        helper.assertTrue(controller.pageView().insert(0, key, 100, Actionable.MODULATE) == 7
                && controller.full().getAmount(0) == Long.MAX_VALUE,
                "GUI page acknowledged an unstored amount");
        controller.setOnlineSupplier(() -> false);
        helper.assertTrue(controller.pageView().insert(0, key, 1, Actionable.MODULATE) == 0
                && controller.full().getAmount(0) == Long.MAX_VALUE,
                "Offline restriction discarded a full owned stack");
        System.out.println("RETURN_LIMIT_STRESS_PASS rounds=10000 slot/MEStorage/GUI conservation");
        helper.succeed();
    }
}
