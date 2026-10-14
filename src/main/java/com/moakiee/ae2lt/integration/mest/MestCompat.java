package com.moakiee.ae2lt.integration.mest;

import com.lhy.mest.api.RegisterMestModuleSlotsEvent;
import net.neoforged.bus.api.IEventBus;

/** Invoked only after the optional MEST mod was detected. */
public final class MestCompat {
    private MestCompat() {}
    public static void register(IEventBus bus) {
        bus.addListener(MestCompat::registerSlots);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT)
            com.moakiee.ae2lt.integration.mest.client.MestTianshuClient.register(bus);
    }
    private static void registerSlots(RegisterMestModuleSlotsEvent event) {
        event.register(MestTianshuSession.CRAFTING_MODULE,
                context -> ((MestTianshuAccess) context.menu()).ae2lt$tianshuSession().addSlots(context, false));
        event.register(MestTianshuSession.PATTERN_MODULE,
                context -> ((MestTianshuAccess) context.menu()).ae2lt$tianshuSession().addSlots(context, true));
        event.register(MestTianshuSession.MAINTENANCE_MODULE,
                context -> ((MestTianshuAccess) context.menu()).ae2lt$tianshuSession().addMaintenanceSlot(context));
    }
}
