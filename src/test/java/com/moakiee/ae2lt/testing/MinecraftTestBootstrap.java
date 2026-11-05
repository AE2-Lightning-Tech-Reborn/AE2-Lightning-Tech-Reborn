package com.moakiee.ae2lt.testing;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.fml.loading.EarlyLoadingException;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.network.NetworkEvent;

/** Registry bootstrap for ordinary JUnit workers, which run without ModLauncher transformations. */
public final class MinecraftTestBootstrap {
    private static boolean initialized;

    private MinecraftTestBootstrap() {}

    public static synchronized void bootstrap() {
        if (initialized) return;
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(),
                    new EarlyLoadingException("test bootstrap", null, List.of()));
        }
        try {
            // Forge 47.4.10 initializes network channels during vanilla bootstrap.
            // EventBus normally gets these per-class lists from transformed events.
            // Its internal factory also builds the same inheritance tree without
            // invoking constructors, which these untransformed network events lack.
            Method factory = EventListenerHelper.class.getDeclaredMethod(
                    "getListenerListInternal", Class.class, boolean.class);
            factory.setAccessible(true);
            prepareEventTree(NetworkEvent.class, factory);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot initialize untransformed Forge network events", failure);
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        initialized = true;
    }

    private static void prepareEventTree(Class<?> type, Method factory) throws ReflectiveOperationException {
        if (Event.class.isAssignableFrom(type)) {
            prepareParent(type.getSuperclass(), factory);
            factory.invoke(null, type, true);
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            prepareEventTree(nested, factory);
        }
    }

    private static void prepareParent(Class<?> type, Method factory) throws ReflectiveOperationException {
        if (type != null && Event.class.isAssignableFrom(type)) {
            prepareParent(type.getSuperclass(), factory);
            factory.invoke(null, type, true);
        }
    }
}
