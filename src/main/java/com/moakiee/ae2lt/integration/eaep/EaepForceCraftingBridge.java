package com.moakiee.ae2lt.integration.eaep;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraftforge.network.simple.SimpleChannel;
record EaepForceCraftingBridge(Class<?> menuType, Constructor<?> packetConstructor, SimpleChannel channel) {
    static EaepForceCraftingBridge resolve(Class<?> menuType,Class<?> packetType) throws ReflectiveOperationException {
        menuType.getMethod("eap$clientSetForceCraftStart",boolean.class);
        var constructor=packetType.getConstructor(boolean.class);
        var owner=Class.forName("com.extendedae_plus.init.ModNetwork");
        SimpleChannel channel=null;
        for(var field:owner.getDeclaredFields()) if(SimpleChannel.class.isAssignableFrom(field.getType())) {
            field.setAccessible(true);channel=(SimpleChannel)field.get(null);break;
        }
        if(channel==null)throw new NoSuchFieldException("Forge network channel");
        return new EaepForceCraftingBridge(menuType,constructor,channel);
    }
    Object packet(boolean force) throws ReflectiveOperationException { return packetConstructor.newInstance(force); }
}
