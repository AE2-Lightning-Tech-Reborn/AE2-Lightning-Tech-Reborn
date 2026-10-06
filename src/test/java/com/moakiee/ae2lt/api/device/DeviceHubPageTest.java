package com.moakiee.ae2lt.api.device;

import static org.junit.jupiter.api.Assertions.*;
import com.moakiee.ae2lt.network.hub.AddonHubStatePacket;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DeviceHubPageTest {
    @Test void settingsWrapWithoutOverflowAndDuplicateIdsAreRejected() {
        var setting = new DeviceHubPage.Setting(ResourceLocation.parse("test:mode"), "test.mode", "max",
                Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
        assertEquals(Integer.MIN_VALUE, setting.step(1));
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceHubPage.Status("device", true, true, List.of(), List.of(setting, setting)));
    }

    @Test void statePacketPreservesSessionAndSevenSettings() {
        var settings = java.util.stream.IntStream.range(0, 7).mapToObj(i -> new DeviceHubPage.Setting(
                ResourceLocation.parse("test:setting_" + i), "test.label", Integer.toString(i), i, 0, 10, i != 6)).toList();
        var state = new AddonHubStatePacket(42, UUID.randomUUID(), ResourceLocation.parse("test:coil"), true, settings);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            AddonHubStatePacket.STREAM_CODEC.encode(buffer, state);
            assertEquals(state, AddonHubStatePacket.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
