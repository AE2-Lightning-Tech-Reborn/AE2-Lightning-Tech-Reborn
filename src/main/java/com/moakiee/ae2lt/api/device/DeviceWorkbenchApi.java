package com.moakiee.ae2lt.api.device;

import com.moakiee.ae2lt.blockentity.workbench.AddonWorkbenchAdapter;
import com.moakiee.ae2lt.blockentity.workbench.DeviceWorkbenchAdapters;
import net.minecraft.resources.ResourceLocation;

public final class DeviceWorkbenchApi {
    private DeviceWorkbenchApi() {}

    /** Register once per item during common setup on both sides, before load-complete. */
    public static void register(ResourceLocation itemId, WorkbenchDevice device) {
        DeviceWorkbenchAdapters.registerItem(itemId, new AddonWorkbenchAdapter(device));
    }
}
