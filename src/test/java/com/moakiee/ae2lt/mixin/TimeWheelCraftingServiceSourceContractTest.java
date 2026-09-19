package com.moakiee.ae2lt.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class TimeWheelCraftingServiceSourceContractTest {
    private static final Path MIXIN = Path.of(
            "src/main/java/com/moakiee/ae2lt/mixin/thunderbolt/TimeWheelCraftingServiceMixin.java");
    private static final Path POOL = Path.of(
            "src/main/java/com/moakiee/ae2lt/crafting/timewheel/TimeWheelCraftingCpuPool.java");
    private static final Path MIXINS = Path.of("src/main/resources/ae2lt.mixins.json");
    private static final Pattern PRIORITY = Pattern.compile("priority\\s*=\\s*(\\d+)");

    @Test
    void timeWheelHeadTickOutranksGtlCancelAndSharesASameTickGuardWithThunderbolt()
            throws Exception {
        String mixin = Files.readString(MIXIN);
        String pool = Files.readString(POOL);
        String mixins = Files.readString(MIXINS);

        var priorityMatch = PRIORITY.matcher(mixin);
        assertTrue(priorityMatch.find(), "TimeWheel CraftingService mixin must set an explicit priority");
        int priority = Integer.parseInt(priorityMatch.group(1));
        assertTrue(priority < 1000,
                "Mixin 0.8.5 HEAD injects insertBefore the original insn in apply order; lower priority is outermost and must run before GTLCore's default-1000 cancel");

        assertTrue(mixin.contains("method = \"onServerEndTick\""));
        assertTrue(mixin.contains("at = @At(\"HEAD\")"));
        assertFalse(mixin.contains("cancellable = true"),
                "TimeWheel HEAD tick must not cancel CraftingService.onServerEndTick");
        assertFalse(mixin.contains("addWaitingKeys"),
                "HEAD must not write currentlyCrafting; AE2 snapshots that set before rebuild and would hide TimeWheel demand from watchers");
        assertTrue(mixin.contains("TimeWheelCraftingCpuPoolProvider"));
        assertTrue(mixin.contains("IdentityHashMap<TimeWheelCraftingCpuPool, Boolean>"));
        assertTrue(mixins.contains("thunderbolt.TimeWheelCraftingServiceMixin"));

        assertTrue(pool.contains("boolean beginPhysicalTick(long currentTick)"));
        assertTrue(pool.contains("if (!beginPhysicalTick(TickHandler.instance().getCurrentTick()))"));
    }
}
