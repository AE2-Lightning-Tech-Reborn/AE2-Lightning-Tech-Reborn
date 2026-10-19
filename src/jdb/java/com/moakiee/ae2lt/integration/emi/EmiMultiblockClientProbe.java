package com.moakiee.ae2lt.integration.emi;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Exercises the native viewer used by the unmodified GTO client. */
public final class EmiMultiblockClientProbe {
    private static int ticks, openedAt;
    private static boolean done, interacted;

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(EmiMultiblockClientProbe.class);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done || !Boolean.getBoolean("ae2lt.emiMultiblockProbe")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || ++ticks < 100) return;
        try {
            require(ticks < 1200, "EMI probe timeout");
            if (openedAt == 0) {
                var recipes = EmiApi.getRecipeManager().getRecipes(AE2LTEmiPlugin.MULTIBLOCK_STRUCTURE);
                if (recipes.isEmpty() && ticks < 600) return;
                require(!recipes.isEmpty(), "native EMI structure recipes missing");
                require(recipes.stream().allMatch(EmiMultiblockStructureRecipe.class::isInstance),
                        "EMI must use the native interactive adapter");
                EmiApi.displayRecipe(recipes.get(0));
                openedAt = ticks;
                return;
            }
            if (ticks - openedAt < 10) return;
            require(mc.screen != null && mc.screen.getClass().getName().contains("RecipeScreen"),
                    "EMI recipe screen did not open");
            var ref = (WeakReference<?>) field(EmiInteractiveMultiblockWidget.class, null, "activeWidget");
            var widget = (EmiInteractiveMultiblockWidget) ref.get();
            require(widget != null, "native EMI widget never rendered");
            var preview = field(EmiInteractiveMultiblockWidget.class, widget, "preview");
            var previewType = preview.getClass();
            require(widget.getBounds().width() == (int) field(previewType, preview, "width")
                    && widget.getBounds().height() == (int) field(previewType, preview, "height"),
                    "EMI drawing and hit-test dimensions differ");
            if (!interacted) {
                float yaw = (float) field(previewType, preview, "yaw");
                require(widget.handleMouseDragged(60, 70, 0, 12, 6), "rotation drag rejected");
                require((float) field(previewType, preview, "yaw") != yaw, "rotation did not change");
                float zoom = (float) field(previewType, preview, "zoom");
                require(widget.handleMouseScrolled(60, 70, 0, 1), "zoom scroll rejected");
                require((float) field(previewType, preview, "zoom") > zoom, "zoom did not change");
                require(widget.mouseClicked(42, 20, 0)
                        && (boolean) field(previewType, preview, "hideShell"), "shell toggle failed");
                interacted = true;
                return;
            }
            Screenshot.grab(mc.gameDirectory, "emi-multiblock.png", mc.getMainRenderTarget(), ignored -> {});
            Files.writeString(Path.of("emi-multiblock-result.txt"),
                    "PASS: native GTO EMI registry, recipe screen, rendered widget bounds, rotation, zoom and shell toggle.\n");
            finish();
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(Path.of("emi-multiblock-result.txt"), "FAIL: " + failure); }
            catch (Exception ignored) { }
            finish();
        }
    }

    private static Object field(Class<?> type, Object target, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void finish() {
        done = true;
        Minecraft.getInstance().setScreen(null);
        Minecraft.getInstance().stop();
    }
}
