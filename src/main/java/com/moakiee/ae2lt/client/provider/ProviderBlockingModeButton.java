package com.moakiee.ae2lt.client.provider;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Icons;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;

/**
 * Three-state replacement for AE2's two-state blocking button.
 */
final class ProviderBlockingModeButton extends LdlibAe2Button {
    private static final int STATE_OFF = 0;
    private static final int STATE_NORMAL = 1;
    private static final int STATE_SAME_PATTERN = 2;
    private static final ResourceLocation SAME_PATTERN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID,
                    "textures/gui/buttons/same_pattern_blocking_on.png");
    private static final LdlibStyleSprite SAME_PATTERN_ATLAS = new LdlibStyleSprite(SAME_PATTERN_TEXTURE, 16, 16);
    private static final SpriteTexture SAME_PATTERN_ICON = SAME_PATTERN_ATLAS.full();
    private static final SpriteTexture SAME_PATTERN_DISABLED = SAME_PATTERN_ATLAS.full().setColor(0x7FFFFFFF);

    private int state;

    ProviderBlockingModeButton(Runnable onPress) {
        super(16, tooltipFor(STATE_OFF), onPress);
        setMessage(Component.empty());
        toolbarFace(IGuiTexture.dynamic(() -> {
            if (state == STATE_SAME_PATTERN) {
                return active ? SAME_PATTERN_ICON : SAME_PATTERN_DISABLED;
            }
            var icon = state == STATE_NORMAL ? Icon.BLOCKING_MODE_YES : Icon.BLOCKING_MODE_NO;
            return LdlibAe2Icons.texture(icon);
        }), false);
    }

    void setState(int state) {
        int clampedState = Math.clamp(state, STATE_OFF, STATE_SAME_PATTERN);
        if (this.state != clampedState) {
            this.state = clampedState;
        }
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible;
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(tooltipFor(state));
    }

    private static Component tooltipFor(int state) {
        return Component.translatable(switch (state) {
            case STATE_NORMAL -> "ae2lt.gui.blocking_mode.normal";
            case STATE_SAME_PATTERN -> "ae2lt.gui.blocking_mode.same_pattern";
            default -> "ae2lt.gui.blocking_mode.off";
        });
    }
}
