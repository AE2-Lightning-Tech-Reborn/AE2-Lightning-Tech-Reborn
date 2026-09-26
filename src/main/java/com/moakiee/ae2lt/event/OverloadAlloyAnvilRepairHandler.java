package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.OverloadAlloyAnvilMenu;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OverloadAlloyAnvilRepairHandler {
    private OverloadAlloyAnvilRepairHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRepair(AnvilRepairEvent event) {
        if (event.getEntity().containerMenu instanceof OverloadAlloyAnvilMenu) {
            event.setBreakChance(0);
        }
    }
}
