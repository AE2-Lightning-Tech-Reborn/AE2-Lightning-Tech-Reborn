package com.moakiee.ae2lt.client.workstation;

import com.moakiee.ae2lt.workstation.PigmeeControlInputPacket;
import com.moakiee.ae2lt.workstation.PigmeeControlStatePacket;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** First-person movement bridge. Never replaces Minecraft.player or the server camera. */
public final class PigmeeControlScreen extends Screen {
    private PigmeeControlStatePacket state;
    private final Set<Integer> held = new HashSet<>();
    private int sequence;
    private float yaw, pitch;
    private boolean closed;
    private boolean ready;
    private int waitingTicks;
    private net.minecraft.client.CameraType previousCameraType;
    private PigmeeControlScreen(PigmeeControlStatePacket state) { super(Component.translatable("screen.ae2lt.pigmee_control")); this.state = state; yaw = state.yaw(); pitch = state.pitch(); }
    public static void receive(PigmeeControlStatePacket packet) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof PigmeeControlScreen screen && screen.state.token().equals(packet.token())) {
            if (!packet.active()) screen.onClose(); else screen.state = packet;
        } else if (packet.active() && packet.opening() && mc.level != null && mc.player != null) {
            mc.setScreen(new PigmeeControlScreen(packet));
        } else if (packet.active()) {
            PacketDistributor.sendToServer(new PigmeeControlInputPacket(packet.token(), 0, 0, 0, 0, true));
        }
    }
    public static net.minecraft.world.entity.Entity renderView(net.minecraft.world.entity.Entity original) {
        var mc = Minecraft.getInstance();
        if (original == mc.player && mc.screen instanceof PigmeeControlScreen screen && !screen.closed && mc.level != null) {
            var actor = mc.level.getPlayerByUUID(screen.state.actor());
            if (actor != null) return actor;
        }
        return original;
    }
    @Override protected void init() {
        var actor = minecraft.level.getPlayerByUUID(state.actor());
        if (actor != null) { yaw = actor.getYRot(); pitch = actor.getXRot(); ready = true; }
        if (previousCameraType == null) previousCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        // Keep Minecraft.cameraEntity == the real player: LocalPlayer.sendPosition and
        // serverAiStep depend on that identity. Only Camera.setup's render subject changes.
        minecraft.player.xxa = 0; minecraft.player.zza = 0; minecraft.player.setJumping(false);
        minecraft.player.input.forwardImpulse = 0; minecraft.player.input.leftImpulse = 0;
        minecraft.player.input.jumping = false; minecraft.player.input.shiftKeyDown = false;
        net.minecraft.client.KeyMapping.releaseAll();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (closed) return;
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()
                || !minecraft.isWindowActive()) { onClose(); return; }
        var actor = minecraft.level.getPlayerByUUID(state.actor());
        if (actor == null) {
            if (ready || ++waitingTicks > 100) { onClose(); return; }
            PacketDistributor.sendToServer(new PigmeeControlInputPacket(state.token(), sequence++, 0, yaw, pitch, false));
            return;
        }
        if (!ready) { yaw = actor.getYRot(); pitch = actor.getXRot(); ready = true; }
        int flags = bit(GLFW.GLFW_KEY_W, 1) | bit(GLFW.GLFW_KEY_S, 2) | bit(GLFW.GLFW_KEY_A, 4) | bit(GLFW.GLFW_KEY_D, 8)
                | bit(GLFW.GLFW_KEY_SPACE, 16) | bit(GLFW.GLFW_KEY_LEFT_SHIFT, 32) | bit(GLFW.GLFW_KEY_LEFT_CONTROL, 64);
        yaw += bit(GLFW.GLFW_KEY_RIGHT, 4) - bit(GLFW.GLFW_KEY_LEFT, 4);
        pitch = Mth.clamp(pitch + bit(GLFW.GLFW_KEY_DOWN, 3) - bit(GLFW.GLFW_KEY_UP, 3), -90, 90);
        PacketDistributor.sendToServer(new PigmeeControlInputPacket(state.token(), sequence++, flags, yaw, pitch, false));
    }
    private int bit(int key, int value) { return held.contains(key) ? value : 0; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; } held.add(key); return true; }
    @Override public boolean keyReleased(int key, int scan, int modifiers) { held.remove(key); return true; }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        yaw += (float) dx * .25F; pitch = Mth.clamp(pitch + (float) dy * .25F, -90, 90); return true;
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, 46, 0xC0000000);
        graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.ae2lt.pigmee_control_keys"), width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.ae2lt.pigmee_control_health", state.health(), state.food(), minecraft.player == null ? 0 : minecraft.player.getHealth()), width / 2, 33, 0xFFDD88);
        graphics.drawCenteredString(font, ready ? Component.literal("+") : Component.translatable("screen.ae2lt.pigmee_loading"), width / 2, height / 2 - 4, 0xFFFFFF);
    }
    @Override public void onClose() { removed(); if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null); }
    @Override public void removed() {
        if (closed) return; closed = true; held.clear();
        if (minecraft != null) {
            if (previousCameraType != null) minecraft.options.setCameraType(previousCameraType);
            if (minecraft.getConnection() != null) PacketDistributor.sendToServer(new PigmeeControlInputPacket(state.token(), sequence, 0, yaw, pitch, true));
            net.minecraft.client.KeyMapping.releaseAll();
        }
    }
}
