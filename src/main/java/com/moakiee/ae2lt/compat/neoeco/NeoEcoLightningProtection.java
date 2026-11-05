package com.moakiee.ae2lt.compat.neoeco;

import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** One NeoECO conversion, after its original random roll and before its block write. */
public final class NeoEcoLightningProtection {
    private NeoEcoLightningProtection() {
    }

    public static boolean setBlockAndUpdate(
            Level level, BlockPos target, BlockState replacement, LightningBolt bolt) {
        if (level instanceof ServerLevel serverLevel
                && NaturalLightningTransformationHandler.isReservedForStructure(
                        serverLevel, bolt.blockPosition(), target)) {
            return false;
        }
        // The probe neither consumes ingredients nor claims/captures the bolt. Do not
        // cancel the land callback: its unrelated nearby conversions still have to run.
        return level.setBlockAndUpdate(target, replacement);
    }
}
