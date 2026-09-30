package com.moakiee.ae2lt.integration.eaep;

import java.lang.reflect.Constructor;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

record EaepForceCraftingBridge(Class<?> menuType,
        Constructor<? extends CustomPacketPayload> packetConstructor, ResourceLocation channel) {
    static EaepForceCraftingBridge resolve(Class<?> menuType, Class<?> packetType)
            throws ReflectiveOperationException {
        menuType.getMethod("eap$clientSetForceCraftStart", boolean.class);
        var constructor = packetType.asSubclass(CustomPacketPayload.class).getConstructor(boolean.class);
        var type = (CustomPacketPayload.Type<?>) packetType.getField("TYPE").get(null);
        return new EaepForceCraftingBridge(menuType, constructor, type.id());
    }

    CustomPacketPayload packet(boolean forceStart) throws ReflectiveOperationException {
        return packetConstructor.newInstance(forceStart);
    }
}
