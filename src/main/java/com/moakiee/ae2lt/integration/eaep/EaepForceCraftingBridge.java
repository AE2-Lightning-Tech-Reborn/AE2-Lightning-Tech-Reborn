package com.moakiee.ae2lt.integration.eaep;

import java.lang.reflect.Constructor;

import net.minecraftforge.network.simple.SimpleChannel;

record EaepForceCraftingBridge(Class<?> menuType, Constructor<?> packetConstructor, SimpleChannel channel) {
    static EaepForceCraftingBridge resolve(Class<?> menuType, Class<?> packetType, Class<?> networkType)
            throws ReflectiveOperationException {
        menuType.getMethod("eap$clientSetForceCraftStart", boolean.class);
        var constructor = packetType.getConstructor(boolean.class);
        var channel = (SimpleChannel) networkType.getField("CHANNEL").get(null);
        return new EaepForceCraftingBridge(menuType, constructor, channel);
    }

    Object packet(boolean forceStart) throws ReflectiveOperationException {
        return packetConstructor.newInstance(forceStart);
    }
}
