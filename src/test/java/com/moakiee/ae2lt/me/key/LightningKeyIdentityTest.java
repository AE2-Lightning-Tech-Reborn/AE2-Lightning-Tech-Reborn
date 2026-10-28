package com.moakiee.ae2lt.me.key;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class LightningKeyIdentityTest {
    @Test
    void packetAndSavedKeysKeepCanonicalIdentityAndMapLookups() {
        var amounts = new HashMap<LightningKey, Long>();
        for (var tier : LightningKey.Tier.values()) {
            var key = LightningKey.of(tier);
            amounts.put(key, Long.MAX_VALUE - tier.ordinal());
            var saved = LightningKeyType.INSTANCE.loadKeyFromTag(key.toTag());
            assertSame(key, saved);
            var packet = new FriendlyByteBuf(Unpooled.buffer());
            try {
                key.writeToPacket(packet);
                var decoded = LightningKeyType.INSTANCE.readFromPacket(packet);
                assertSame(key, decoded);
                assertEquals(amounts.get(key), amounts.get(decoded));
                assertEquals(key.hashCode(), decoded.hashCode());
            } finally { packet.release(); }
        }
        assertEquals(2, amounts.size());
        assertNotEquals(LightningKey.HIGH_VOLTAGE, LightningKey.EXTREME_HIGH_VOLTAGE);
    }
}
