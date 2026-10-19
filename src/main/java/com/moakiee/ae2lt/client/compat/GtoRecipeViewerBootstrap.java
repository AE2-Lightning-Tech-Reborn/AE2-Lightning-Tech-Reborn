package com.moakiee.ae2lt.client.compat;

import java.util.function.Consumer;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** GTO replaces annotation discovery with its public recipe-viewer plugin events. */
@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GtoRecipeViewerBootstrap {
    private GtoRecipeViewerBootstrap() { }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("emi")) Emi.register();
            if (ModList.get().isLoaded("jei")) Jei.register();
        });
    }

    private static void listen(String fieldName, Consumer<Consumer<Object>> listener) {
        try {
            // Keep GTCEu's nested implementation types off LT's compile/runtime dependency list.
            // The pinned GTO API exposes Event<Consumer<Plugin>> for both viewers.
            Object event = Class.forName("com.gtolib.api.GTOApi").getField(fieldName).get(null);
            event.getClass().getMethod("addListener", Class.class, Consumer.class)
                    .invoke(event, GtoRecipeViewerBootstrap.class, listener);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot register LT with GTO " + fieldName, failure);
        }
    }

    private static final class Emi {
        private static void register() {
            listen("EMI_PLUGIN_EVENT", registrar -> {
                try {
                    Object container = Class.forName("dev.emi.emi.registry.EmiPluginContainer")
                            .getConstructor(dev.emi.emi.api.EmiPlugin.class, String.class)
                            .newInstance(new com.moakiee.ae2lt.integration.emi.AE2LTEmiPlugin(), AE2LightningTech.MODID);
                    registrar.accept(container);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot create LT EMI plugin container", failure);
                }
            });
        }
    }

    private static final class Jei {
        private static void register() {
            listen("JEI_PLUGIN_EVENT", registrar -> registrar.accept(new com.moakiee.ae2lt.integration.jei.JEIPlugin()));
        }
    }
}
