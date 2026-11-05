package com.moakiee.ae2lt.integration.mekanism;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.ChemicalSlotInfo;
import mekanism.common.tile.component.config.slot.FluidSlotInfo;
import mekanism.common.tile.component.config.slot.ISlotInfo;

/**
 * Classifies Mekanism chemical and fluid tanks for automatic provider returns.
 * <p>
 * Unlike item slots, Mekanism often registers one tank as INPUT, OUTPUT and
 * INPUT_OUTPUT at once ({@code setupIOConfig(type, tank, side)}), for example the
 * secondary chemical of Purification/Injection Chambers, Osmium Compressors and
 * the Metallurgic Infuser's infusion tank. Being listed as OUTPUT therefore does
 * not make a tank a product. A tank is a product only if it is in an output
 * group, is not an EXTRA tank, and is not also in an input group that forbids
 * external extraction (Mekanism's own statement that the tank is an input).
 */
final class MekanismOutputRoles {
    private static final DataType[] OUTPUT_TYPES = {
            DataType.OUTPUT, DataType.OUTPUT_1, DataType.OUTPUT_2 };
    private static final DataType[] INPUT_TYPES = {
            DataType.INPUT, DataType.INPUT_1, DataType.INPUT_2 };

    private MekanismOutputRoles() {
    }

    static boolean isProductTank(@Nullable ConfigInfo config, Object tank) {
        if (config == null || tank == null) {
            return false;
        }
        boolean output = false;
        for (var type : OUTPUT_TYPES) {
            if (holds(config.getSlotInfo(type), tank)) {
                output = true;
                break;
            }
        }
        if (!output || holds(config.getSlotInfo(DataType.EXTRA), tank)) {
            return false;
        }
        for (var type : INPUT_TYPES) {
            var info = config.getSlotInfo(type);
            if (info != null && !info.canOutput() && holds(info, tank)) {
                return false;
            }
        }
        return true;
    }

    private static boolean holds(@Nullable ISlotInfo info, Object tank) {
        List<?> tanks;
        if (info instanceof ChemicalSlotInfo chemical) {
            tanks = chemical.getTanks();
        } else if (info instanceof FluidSlotInfo fluid) {
            tanks = fluid.getTanks();
        } else {
            return false;
        }
        // Identity: tank objects are the machine's live containers.
        for (var candidate : tanks) {
            if (candidate == tank) {
                return true;
            }
        }
        return false;
    }
}
