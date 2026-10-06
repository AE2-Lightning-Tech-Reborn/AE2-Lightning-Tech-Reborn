package com.moakiee.ae2lt.api.lightning;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class LightningHostTest {
    @Test void inactiveHostDoesNotTouchGridStorage() {
        IGridNode node = (IGridNode) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {IGridNode.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isActive")) return false;
                    throw new AssertionError("inactive node accessed: " + method.getName());
                });
        IActionHost host = () -> node;
        var handler = LightningApi.forHost(host);
        assertEquals(0, handler.extract(LightningTier.HIGH_VOLTAGE, 10, false));
        assertEquals(0, handler.insert(LightningTier.HIGH_VOLTAGE, 10, false));
        assertFalse(handler.canExtract(LightningTier.HIGH_VOLTAGE));
        assertFalse(handler.canInsert(LightningTier.HIGH_VOLTAGE));
    }
}
