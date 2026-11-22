package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.core.AEConfig;
import appeng.menu.me.common.MEStorageMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.client.ldlib.LdlibTerminalSearchField;
import com.moakiee.ae2lt.client.ldlib.LdlibTerminalScrollbar;
import com.moakiee.ae2lt.client.ldlib.LdlibToolboxPanel;
import com.moakiee.ae2lt.client.machine.PigmeeSynthesisStationScreen;
import com.moakiee.ae2lt.client.tianshu.TianshuMaintenanceTermScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MEStorageScreen.class, remap = false)
public abstract class MEStorageControlsMixin extends AEBaseScreen<MEStorageMenu> {
    protected MEStorageControlsMixin(MEStorageMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/WidgetContainer;addTextField(Ljava/lang/String;)Lappeng/client/gui/widgets/AETextField;"))
    private AETextField ae2lt$createSearchField(WidgetContainer widgets, String id, Operation<AETextField> original) {
        if (ae2lt$hasLdlibControls()) {
            var field = new LdlibTerminalSearchField(style, Minecraft.getInstance().font);
            widgets.add(id, field);
            return field;
        }
        return original.call(widgets, id);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/WidgetContainer;addScrollBar(Ljava/lang/String;Lappeng/client/gui/widgets/Scrollbar$Style;)Lappeng/client/gui/widgets/Scrollbar;"))
    private Scrollbar ae2lt$createScrollbar(WidgetContainer widgets, String id, Scrollbar.Style style,
                                          Operation<Scrollbar> original) {
        return ae2lt$hasLdlibControls() ? LdlibTerminalScrollbar.add(widgets, id, style)
                : original.call(widgets, id, style);
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW",
            target = "(Lappeng/client/gui/style/ScreenStyle;Lnet/minecraft/network/chat/Component;)Lappeng/client/gui/widgets/ToolboxPanel;"))
    private ToolboxPanel ae2lt$createToolbox(ScreenStyle style, Component name, Operation<ToolboxPanel> original) {
        return ae2lt$hasLdlibControls() ? new LdlibToolboxPanel(style, name) : original.call(style, name);
    }

    @WrapOperation(method = {"drawFG", "renderSlot"}, at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/me/common/StackSizeRenderer;renderSizeLabel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;FFLjava/lang/String;)V"))
    private void ae2lt$drawCount(GuiGraphics graphics, Font font, float x, float y, String count,
                                 Operation<Void> original) {
        if (ae2lt$hasLdlibControls()) {
            LargeStackCountRenderer.renderTerminalLabel(graphics, font, x, y, count, AEConfig.instance().isUseLargeFonts());
        } else {
            original.call(graphics, font, x, y, count);
        }
    }

    @WrapOperation(method = "renderSlot", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/me/common/StackSizeRenderer;renderSizeLabel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;FFLjava/lang/String;Z)V"))
    private void ae2lt$drawCountWithFont(GuiGraphics graphics, Font font, float x, float y, String count,
                                         boolean largeFont, Operation<Void> original) {
        if (ae2lt$hasLdlibControls()) {
            LargeStackCountRenderer.renderTerminalLabel(graphics, font, x, y, count, largeFont);
        } else {
            original.call(graphics, font, x, y, count, largeFont);
        }
    }

    @Unique
    private boolean ae2lt$hasLdlibControls() {
        return (Object) this instanceof TianshuMaintenanceTermScreen<?>
                || (Object) this instanceof PigmeeSynthesisStationScreen;
    }

}
