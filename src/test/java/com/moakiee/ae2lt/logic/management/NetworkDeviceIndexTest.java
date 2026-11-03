package com.moakiee.ae2lt.logic.management;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.networking.IGridNode;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkDeviceIndexTest {
    @Test
    void equalNodesKeepIndependentIdentitiesAcrossRemovalAndReaddition() throws Exception {
        var index = new NetworkDeviceIndex();
        var first = equalNode();
        var second = equalNode();
        index.addNode(first, null);
        index.addNode(second, null);
        UUID firstId = id(index, first), secondId = id(index, second);
        assertNotEquals(firstId, secondId);
        assertSame(first, index.resolve(firstId));
        assertSame(second, index.resolve(secondId));

        index.removeNode(first);
        assertFalse(index.contains(first));
        assertTrue(index.contains(second));
        assertNull(index.resolve(firstId));
        assertSame(second, index.resolve(secondId));

        index.addNode(first, null);
        assertNotEquals(firstId, id(index, first));
        assertSame(second, index.resolve(secondId));
        assertSame(first, index.resolve(id(index, first)));
    }

    private static IGridNode equalNode() {
        return (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> true;
                    case "hashCode" -> 1;
                    case "toString" -> "equal QA node";
                    default -> null;
                });
    }

    @SuppressWarnings("unchecked")
    private static UUID id(NetworkDeviceIndex index, IGridNode node) throws Exception {
        Field field = NetworkDeviceIndex.class.getDeclaredField("nodes");
        field.setAccessible(true);
        return ((Map<IGridNode, UUID>) field.get(index)).get(node);
    }
}
