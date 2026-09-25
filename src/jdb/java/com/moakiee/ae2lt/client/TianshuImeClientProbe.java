package com.moakiee.ae2lt.client;

import java.nio.file.Files;
import java.util.List;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.AETextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Native EditBox/AETextField focus, IME routing and committed Unicode regression. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class TianshuImeClientProbe {
    private static boolean done;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ae2lt.tianshuImeClientProbe") || done || mc.getOverlay() != null
                || !(mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)) return;
        done = true;
        String result;
        try {
            verify(mc);
            verifyUploadFrame(mc);
            com.moakiee.ae2lt.client.core.CoreEffectClientChecks.verify(mc);
            result = "PASS: native 26.1 IME regression reproduced; focused search survives 60 inactive-anvil "
                    + "updates; preedit routes and clears; Chinese commits; hiding the focused anvil ends its input; "
                    + "upload frame spans 6/13/20 rows; 7 block-entity renderer types dispatch, both cores build meshes, 6 GPU pipelines compile.";
        } catch (Throwable failure) {
            failure.printStackTrace();
            result = "FAIL: " + failure;
        }
        System.out.println("TIANSHU_IME_PROBE " + result);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("tianshu-ime-result.txt"), result); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }

    private static void verify(Minecraft mc) throws Exception {
        var style = StyleManager.loadStyleDoc("/screens/wireless_tianshu_crafting_terminal.json");
        var search = new AETextField(style, mc.font, 30, 30, 150, 16);
        var anvil = new AETextField(style, mc.font, 30, 60, 150, 16);
        var screen = new Screen(Component.literal("Tianshu IME regression")) {
            @Override protected void init() {
                addRenderableWidget(search);
                addRenderableWidget(anvil);
                setInitialFocus(search);
            }
        };
        mc.setScreen(screen);
        require(search.isFocused() && inputEnabled(mc), "search failed to enable native text input");
        // The old per-frame call stopped IME despite search retaining the actual focus.
        anvil.setFocused(false);
        require(search.isFocused() && !inputEnabled(mc), "old inactive-editor IME failure was not reproduced");
        search.setFocused(true);
        for (int i = 0; i < 60; i++) {
            TianshuCraftingTermScreen.updateAnvilEditorAvailability(anvil, false, false);
            require(inputEnabled(mc), "inactive anvil disabled search IME on frame " + i);
        }
        var preedit = new PreeditEvent("zhong", 5, List.of("zhong"), 0);
        require(screen.preeditUpdated(preedit), "screen did not route native preedit to focused search");
        var overlay = net.minecraft.client.gui.components.EditBox.class.getDeclaredField("preeditOverlay");
        overlay.setAccessible(true);
        require(overlay.get(search) != null && overlay.get(anvil) == null, "preedit reached wrong field");
        var graphics = new net.minecraft.client.gui.GuiGraphicsExtractor(mc,
                new net.minecraft.client.renderer.state.gui.GuiRenderState(), 0, 0);
        search.extractWidgetRenderState(graphics, 0, 0, 0);
        require(screen.preeditUpdated(null) && overlay.get(search) == null, "preedit cancellation was lost");
        require(screen.charTyped(new CharacterEvent('中')) && screen.charTyped(new CharacterEvent('文'))
                && search.getValue().equals("中文"), "Chinese commit failed");
        TianshuCraftingTermScreen.updateAnvilEditorAvailability(anvil, true, true);
        screen.setFocused(anvil);
        require(anvil.isFocused() && inputEnabled(mc), "anvil failed to enable native text input");
        TianshuCraftingTermScreen.updateAnvilEditorAvailability(anvil, false, true);
        require(!anvil.isFocused() && !inputEnabled(mc), "hidden anvil retained native input focus");
        screen.setFocused(search);
        require(inputEnabled(mc), "search could not reacquire native input");
    }

    private static void verifyUploadFrame(Minecraft mc) {
        for (int rows : new int[]{6, 13, 20}) {
            var state = new net.minecraft.client.renderer.state.gui.GuiRenderState();
            var graphics = new net.minecraft.client.gui.GuiGraphicsExtractor(mc, state, 0, 0);
            TianshuUploadTargetScreen.drawFrame(graphics, 10, 20, rows);
            var bounds = new java.util.ArrayList<net.minecraft.client.gui.navigation.ScreenRectangle>();
            state.forEachElement(element -> bounds.add(element.bounds()),
                    net.minecraft.client.renderer.state.gui.GuiRenderState.TraverseRange.ALL);
            require(bounds.size() == 4, "upload frame omitted a strip at " + rows + " rows");
            bounds.sort(java.util.Comparator.comparingInt(net.minecraft.client.gui.navigation.ScreenRectangle::top));
            int bottom = 20;
            for (var rectangle : bounds) {
                require(rectangle.left() == 10 && rectangle.width() == 190 && rectangle.top() == bottom,
                        "upload frame has a gap/incorrect stretched width at " + rows + " rows: " + rectangle);
                bottom = rectangle.bottom();
            }
            require(bottom == 20 + 33 + rows * 17 + 18, "upload frame height mismatch");
        }
    }

    private static boolean inputEnabled(Minecraft mc) throws Exception {
        var field = com.mojang.blaze3d.platform.TextInputManager.class.getDeclaredField("textInputEnabled");
        field.setAccessible(true);
        return field.getBoolean(mc.textInputManager());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
