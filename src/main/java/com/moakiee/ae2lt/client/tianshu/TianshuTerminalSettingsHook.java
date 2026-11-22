package com.moakiee.ae2lt.client.tianshu;

import java.util.List;

import appeng.client.gui.Icon;
import appeng.client.gui.me.common.TerminalSettingsScreen;
import appeng.client.gui.widgets.OpenGuideButton;
import appeng.core.localization.ButtonToolTips;
import appeng.items.tools.GuideItem;
import guideme.GuidesCommon;
import com.moakiee.ae2lt.AE2LightningTech;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class TianshuTerminalSettingsHook {
    private TianshuTerminalSettingsHook() {
    }

    @SubscribeEvent
    public static void bindNativeSettingsGuide(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TerminalSettingsScreen<?> settings)
                || !(settings.getMenu() instanceof TianshuMaintenanceMenu)) return;
        var toolbar = ((AEBaseScreenAccessor) settings).ae2lt$getVerticalToolbar();
        var buttons = ((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
        for (int i = 0; i < buttons.size(); i++) {
            var previous = buttons.get(i);
            if (!(previous instanceof OpenGuideButton)) continue;
            var button = LdlibAe2Button.toolbar(Icon.HELP, ButtonToolTips.OpenGuide.text(),
                    () -> GuidesCommon.openGuide(settings.getMenu().getPlayer(), GuideItem.GUIDE_ID,
                            TianshuMaintenanceTermScreen.terminalHelpTopic(settings.getMenu(), "settings")),
                    () -> List.of(ButtonToolTips.OpenGuide.text(), ButtonToolTips.OpenGuideDetail.text()));
            button.setMessage(Component.empty());
            button.setPosition(previous.getX(), previous.getY());
            buttons.set(i, button);
            event.removeListener(previous);
            event.addListener(button);
            return;
        }
    }

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addTianshuSettingsTab(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TerminalSettingsScreen settings)
                || !(settings.getMenu() instanceof TianshuMaintenanceMenu)) return;

        // AE2's help button is owned by the native left toolbar. Add this button to
        // the same toolbar so its width, right edge, spacing, background, and resize
        // behavior all stay consistent with the rest of the terminal UI.
        var toolbar = ((AEBaseScreenAccessor) settings).ae2lt$getVerticalToolbar();
        var buttons = ((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
        for (var existing : buttons) {
            if (existing instanceof TianshuSettingsButton) return;
        }

        var button = new TianshuSettingsButton(() ->
                settings.switchToScreen(new TianshuTerminalSettingsScreen(settings)));
        toolbar.add(button);
        // Init.Post runs after AE2 populated its toolbar. Register the new button
        // for this initialization; later re-initializations populate it normally.
        event.addListener(button);
    }

    private static final class TianshuSettingsButton extends LdlibAe2Button {
        private TianshuSettingsButton(Runnable onPress) {
            super(16, Component.translatable("ae2lt.tianshu.settings.title"), onPress);
            setMessage(Component.empty());
            toolbarFace(IGuiTexture.EMPTY, false);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!visible) {
                return;
            }
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.renderItem(new ItemStack(ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get()),
                    getX(), getY() + 1 + (isHovered() ? 1 : 0), 0, 3);
        }
    }
}
