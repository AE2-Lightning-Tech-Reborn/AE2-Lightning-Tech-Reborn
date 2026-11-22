package com.moakiee.ae2lt.client.ldlib;

import java.util.List;
import java.util.function.BooleanSupplier;

import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.client.gui.Icon;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.network.chat.Component;

public final class LdlibTerminalStyleButton extends LdlibAe2Button {
    private static final List<TerminalStyle> STYLES = List.copyOf(Settings.TERMINAL_STYLE.getValues());
    private static final Component LABEL = Component.translatable(ButtonToolTips.TerminalStyle.getTranslationKey());
    private final AEConfig config;

    public LdlibTerminalStyleButton(AEConfig config, BooleanSupplier backwards, Runnable refresh) {
        super(16, LABEL, () -> {
            int current = STYLES.indexOf(config.getTerminalStyle());
            int step = backwards.getAsBoolean() ? -1 : 1;
            config.setTerminalStyle(STYLES.get(Math.floorMod(current + step, STYLES.size())));
            refresh.run();
        });
        this.config = config;
        setMessage(Component.empty());
        toolbarFace(IGuiTexture.dynamic(() -> LdlibAe2Icons.texture(switch (config.getTerminalStyle()) {
            case SMALL -> Icon.TERMINAL_STYLE_SMALL;
            case MEDIUM -> Icon.TERMINAL_STYLE_MEDIUM;
            case TALL -> Icon.TERMINAL_STYLE_TALL;
            case FULL -> Icon.TERMINAL_STYLE_FULL;
        })), false);
    }

    @Override
    public List<Component> getTooltipMessage() {
        var value = switch (config.getTerminalStyle()) {
            case SMALL -> ButtonToolTips.TerminalStyle_Small;
            case MEDIUM -> ButtonToolTips.TerminalStyle_Medium;
            case TALL -> ButtonToolTips.TerminalStyle_Tall;
            case FULL -> ButtonToolTips.TerminalStyle_Full;
        };
        return List.of(LABEL, Component.translatable(value.getTranslationKey()));
    }
}
