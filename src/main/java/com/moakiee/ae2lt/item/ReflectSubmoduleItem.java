package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.ReflectSubmodule;

public final class ReflectSubmoduleItem extends AbstractSingleArmorSubmoduleItem {

    public static DeviceCapability.ReflectTuning tuning() {
        return new DeviceCapability.ReflectTuning(0.30D, ArmorOverloadRules.REFLECT_ACTIVE_COST_FE_PER_DAMAGE);
    }

    public ReflectSubmoduleItem(Properties properties) {
        super(
                properties,
                ArmorPart.CHEST,
                ReflectSubmodule.INSTANCE,
                stack -> List.of(
                        tuning(),
                        new DeviceCapability.PassiveDrain(ArmorOverloadRules.REFLECT_PASSIVE_DRAIN_FE)));
    }
}
