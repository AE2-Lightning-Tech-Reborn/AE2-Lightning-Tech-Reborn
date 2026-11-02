package com.moakiee.ae2lt.debug;

import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.lwjgl.opengl.GL11;

/** Opt-in startup evidence from a real render loop; excluded from release jars. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class ClientStartupProbe {
    private static int frames;
    private static boolean done;

    @SubscribeEvent
    public static void tick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ae2lt.clientStartupProbe") || done) return;
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || ++frames < 100) return;
        done = true;
        try {
            if (mc.getMainRenderTarget().width <= 0 || mc.getMainRenderTarget().height <= 0)
                throw new AssertionError("Empty client framebuffer");
            var result = new StringBuilder("PASS: title screen rendered for 100 frames\n");
            result.append("OpenGL=").append(GL11.glGetString(GL11.GL_VERSION)).append('\n');
            result.append("renderer=").append(GL11.glGetString(GL11.GL_RENDERER)).append('\n');
            for (var mod : ModList.get().getMods()) result.append(mod.getModId()).append('=')
                    .append(mod.getVersion()).append('\n');
            Screenshot.grab(mc.gameDirectory, "client-main-menu.png", mc.getMainRenderTarget(), ignored -> {});
            Files.writeString(mc.gameDirectory.toPath().resolve("client-startup-result.txt"), result);
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("client-startup-result.txt"), "FAIL: " + failure); }
            catch (Exception ignored) { }
        }
        mc.stop();
    }
}
