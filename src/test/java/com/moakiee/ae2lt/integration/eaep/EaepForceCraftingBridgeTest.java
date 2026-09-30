package com.moakiee.ae2lt.integration.eaep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EaepForceCraftingBridgeTest {
    @Test
    void createsExplicitTrueAndFalsePacketsForTheRegisteredChannel() throws Exception {
        var bridge = EaepForceCraftingBridge.resolve(Sync.class, FlagPacket.class);
        assertEquals(FlagPacket.TYPE.id(), bridge.channel());
        assertTrue(((FlagPacket) bridge.packet(true)).forceStart);
        assertFalse(((FlagPacket) bridge.packet(false)).forceStart);
    }

    @Test
    void rejectsMissingMenuCapabilityAndIncompatiblePacketConstructor() {
        assertThrows(NoSuchMethodException.class, () -> EaepForceCraftingBridge.resolve(Object.class, FlagPacket.class));
        assertThrows(ClassCastException.class, () -> EaepForceCraftingBridge.resolve(Sync.class, Object.class));
    }

    public interface Sync {
        void eap$clientSetForceCraftStart(boolean forceStart);
    }

    public static final class FlagPacket implements CustomPacketPayload {
        public static final Type<FlagPacket> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("extendedae_plus", "force_craft_start_flag"));
        final boolean forceStart;

        public FlagPacket(boolean forceStart) {
            this.forceStart = forceStart;
        }

        @Override
        public Type<FlagPacket> type() {
            return TYPE;
        }
    }
}
