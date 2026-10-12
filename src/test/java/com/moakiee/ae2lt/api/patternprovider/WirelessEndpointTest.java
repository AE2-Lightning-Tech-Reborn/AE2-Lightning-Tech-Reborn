package com.moakiee.ae2lt.api.patternprovider;

import static org.junit.jupiter.api.Assertions.*;

import com.moakiee.ae2lt.logic.wireless.support.WirelessConnectionRef;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class WirelessEndpointTest {
    private static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("minecraft", "overworld"));

    @Test
    void endpointCopiesMutablePositionsAndRejectsMissingFields() {
        var position = new BlockPos.MutableBlockPos(1, 2, 3);
        var endpoint = new WirelessEndpoint(DIMENSION, position, Direction.NORTH);
        position.set(4, 5, 6);
        assertEquals(new BlockPos(1, 2, 3), endpoint.pos());
        assertThrows(NullPointerException.class, () -> new WirelessEndpoint(null, position, Direction.NORTH));
        assertThrows(NullPointerException.class, () -> new WirelessEndpoint(DIMENSION, null, Direction.NORTH));
        assertThrows(NullPointerException.class, () -> new WirelessEndpoint(DIMENSION, position, null));
    }

    @Test
    void publicHostSnapshotsDoNotAliasTheLegacyConnectionView() {
        var position = new BlockPos.MutableBlockPos(1, 2, 3);
        var connections = new ArrayList<WirelessConnectionRef>();
        connections.add(new WirelessConnectionRef() {
            public ResourceKey<Level> dimension() { return DIMENSION; }
            public BlockPos pos() { return position; }
            public Direction boundFace() { return Direction.WEST; }
            public CompoundTag toTag() { return new CompoundTag(); }
        });
        var host = (WirelessPatternProviderHost) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {WirelessPatternProviderHost.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getConnections")) return connections;
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, arguments);
                    throw new AssertionError("unexpected host access: " + method.getName());
                });
        var snapshots = host.getEndpointSnapshots();
        position.set(4, 5, 6);
        connections.clear();
        assertEquals(List.of(new WirelessEndpoint(DIMENSION, new BlockPos(1, 2, 3), Direction.WEST)), snapshots);
        assertThrows(UnsupportedOperationException.class, () -> snapshots.clear());
        assertTrue(host.getEndpointSnapshots().isEmpty());
    }
}
