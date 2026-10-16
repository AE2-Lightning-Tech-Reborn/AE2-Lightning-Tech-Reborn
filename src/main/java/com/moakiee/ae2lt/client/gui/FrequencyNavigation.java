package com.moakiee.ae2lt.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.FrequencyPage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One AE2 frame and tab strip for frequency settings, devices and power. */
public final class FrequencyNavigation {
    public static final int WIDTH = 304, HEIGHT = 252, TAB_SIZE = 22, TAB_TOP = -24;
    private FrequencyNavigation() {}

    public static int left(FrequencyPage page) {
        return 8 + FrequencyPage.NAVIGATION.indexOf(page.navigationPage()) * 24;
    }
    public static List<PageButton> create(int x, int y, FrequencyPage selected, boolean showManagement,
            boolean managementAvailable, Consumer<FrequencyPage> navigate) {
        if (selected.isManagement() && !showManagement) selected = FrequencyPage.TAB_HOME;
        var result = new ArrayList<PageButton>();
        for (var page : FrequencyPage.NAVIGATION) {
            if (page.isManagement() && !showManagement) continue;
            var button = new PageButton(page, ignored -> navigate.accept(page));
            button.setPosition(x + left(page), y + TAB_TOP);
            button.setSelected(page == selected.navigationPage());
            button.active = !page.isManagement() || managementAvailable;
            result.add(button);
        }
        return result;
    }

    public static final class PageButton extends TabButton {
        private final FrequencyPage page;
        private final ResourceLocation customIcon;
        private final ItemStack itemIcon;
        private final Icon nativeIcon;

        private PageButton(FrequencyPage page, Button.OnPress action) {
            super((Icon) null, Component.translatable(page.navigationTranslationKey()), action);
            this.page = page;
            String texture = switch (page) {
                case TAB_HOME -> "menu";
                case TAB_SELECTION -> "frequency_select";
                case TAB_MEMBER -> "frequency_member";
                default -> null;
            };
            customIcon = texture == null ? null : ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID,
                    "textures/gui/buttons/" + texture + ".png");
            nativeIcon = page == FrequencyPage.TAB_SETTING ? Icon.COG : null;
            itemIcon = page == FrequencyPage.TAB_DEVICES ? new ItemStack(appeng.core.definitions.AEBlocks.DRIVE.block())
                    : page == FrequencyPage.TAB_ENERGY ? new ItemStack(appeng.core.definitions.AEBlocks.ENERGY_CELL.block()) : ItemStack.EMPTY;
            setStyle(Style.BOX);
            setTooltip(Tooltip.create(getMessage()));
        }
        public FrequencyPage page() { return page; }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // BOX has no built-in selected state. Paint its native focus surface for selection as well.
            var background = isSelected() || active && isHoveredOrFocused()
                    ? Icon.TAB_BUTTON_BACKGROUND_FOCUS : Icon.TAB_BUTTON_BACKGROUND;
            background.getBlitter().dest(getX(), getY()).blit(graphics);
            if (customIcon != null) graphics.blit(customIcon, getX() + 2, getY() + 1, 0, 0, 16, 16, 16, 16);
            else if (nativeIcon != null) nativeIcon.getBlitter().dest(getX() + 2, getY() + 1).blit(graphics);
            else if (!itemIcon.isEmpty()) graphics.renderItem(itemIcon, getX() + 2, getY() + 1);
        }
    }
}
