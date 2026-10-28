package qa;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;

/**
 * An opt-in local TCP entrance for offline two-JVM menu tests. Vanilla's public-server
 * blacklist download is unrelated to loopback packet handling and can fail before a socket
 * exists. Keep the ordinary ConnectScreen trials as separate acceptance evidence.
 * This uses the normal Connection and Forge login listener; no packets are simulated.
 */
final class GtoLoopbackConnectionQA extends Screen {
    private volatile Connection connection;

    private GtoLoopbackConnectionQA() { super(Component.literal("GTO QA: local TCP login")); }

    static void start(Screen parent, Minecraft mc, String address) {
        if (!"127.0.0.1:25587".equals(address))
            throw new IllegalArgumentException("QA direct connection is restricted to 127.0.0.1:25587");
        var screen = new GtoLoopbackConnectionQA();
        var data = new ServerData("GTO loopback QA", address, false);
        mc.clearLevel();
        mc.prepareForMultiplayer();
        mc.updateReportEnvironment(ReportEnvironment.thirdParty(address));
        mc.setScreen(screen);
        var connector = new Thread(() -> {
            try {
                var socket = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 25587);
                var connection = Connection.connectToServer(socket, mc.options.useNativeTransport());
                connection.setListener(new ClientHandshakePacketListenerImpl(connection, mc, data, parent,
                        false, null, ignored -> {}));
                connection.send(new ClientIntentionPacket("127.0.0.1", 25587, ConnectionProtocol.LOGIN));
                connection.send(new ServerboundHelloPacket(mc.getUser().getName(),
                        Optional.ofNullable(mc.getUser().getProfileId())));
                screen.connection = connection;
            } catch (Exception failure) {
                mc.execute(() -> mc.setScreen(new DisconnectedScreen(parent,
                        Component.literal("GTO QA local TCP login failed"), Component.literal(failure.toString()))));
            }
        }, "GTO QA loopback connector");
        connector.setDaemon(true);
        connector.start();
    }

    @Override public void tick() {
        var current = connection;
        if (current != null) {
            if (current.isConnected()) current.tick();
            else current.handleDisconnection();
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2, 0xffffff);
    }
}
