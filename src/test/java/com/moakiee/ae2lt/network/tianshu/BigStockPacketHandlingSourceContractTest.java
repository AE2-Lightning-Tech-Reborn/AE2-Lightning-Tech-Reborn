package com.moakiee.ae2lt.network.tianshu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BigStockPacketHandlingSourceContractTest {
    @Test
    void clientStockDeltasStayBehindDistExecutor() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/tianshu/BigStockPacket.java"));

        assertTrue(source.contains("DistExecutor.unsafeRunWhenOn("));
        assertTrue(source.contains("ClientNetworkPacketHandlers.handleBigStock("));
        assertTrue(source.contains("setPacketHandled(true)"));
        assertFalse(source.contains("Minecraft.getInstance()"));
    }
}
