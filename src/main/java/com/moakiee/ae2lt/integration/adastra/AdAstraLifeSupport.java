package com.moakiee.ae2lt.integration.adastra;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.WaterBreathingSubmodule;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;
import earth.terrarium.adastra.api.events.AdAstraEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

public final class AdAstraLifeSupport {
    private AdAstraLifeSupport() {}

    public static void register() {
        AdAstraEvents.EntityOxygenEvent.register((entity, ambient) -> ambient || hasLifeSupport(entity));
        AdAstraEvents.HotTemperatureTickEvent.register((level, wearer) -> !hasLifeSupport(wearer));
        AdAstraEvents.ColdTemperatureTickEvent.register((level, wearer) -> !hasLifeSupport(wearer));
    }

    private static boolean hasLifeSupport(Entity entity) {
        if (!(entity instanceof LivingEntity wearer)) return false;
        var helmet = CelestweaveEquipmentAccess.findArmor(wearer, EquipmentSlot.HEAD);
        var module = WaterBreathingSubmodule.INSTANCE;
        if (helmet.isEmpty()) return false;
        if (entity.level().isClientSide()) {
            return module.isInstalled(helmet, entity.registryAccess())
                    && CelestweaveArmorState.isSubmoduleActiveClient(helmet, module, entity.level().getGameTime());
        }
        return CelestweaveArmorState.isSubmoduleRuntimeActive(helmet, module.id());
    }
}
