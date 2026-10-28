package qa;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_qa")
@PrefixGameTestTemplate(false)
public final class GtoUselessCpuGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void dynamicCpuDispatchesThroughTheRealGtoProvider(GameTestHelper h) {
        GtoCpuGameTests.checkNativeProvider(h, true);
    }
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void dynamicPhysicalCpuPreservesVariantsSaveReloadAndNativeFallback(GameTestHelper h) {
        GtoCpuGameTests.checkPhysicalCpu(h, true);
    }
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void dynamicPhysicalCpuCancellationRetainsUndispatchedInputsOnce(GameTestHelper h) {
        GtoCpuGameTests.checkDynamicCancellation(h);
    }
}
