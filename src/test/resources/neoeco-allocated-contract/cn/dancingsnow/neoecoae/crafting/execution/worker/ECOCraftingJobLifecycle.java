package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.util.UUID;
import net.minecraft.world.level.Level;

/** Reflection-only fixture; these batch tests must not invoke lifecycle callbacks. */
public final class ECOCraftingJobLifecycle {
    public static void finish(Level level, UUID id, boolean success) { throw new UnsupportedOperationException(); }
}
