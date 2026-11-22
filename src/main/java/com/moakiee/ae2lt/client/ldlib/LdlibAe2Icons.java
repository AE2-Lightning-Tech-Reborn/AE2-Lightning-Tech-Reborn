package com.moakiee.ae2lt.client.ldlib;

import java.util.EnumMap;

import appeng.client.gui.Icon;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.math.Size;

/** AE2 atlas sprites used by LDLib-rendered controls. */
public final class LdlibAe2Icons {
    private static final Size ATLAS_SIZE = Size.of(Icon.TEXTURE_WIDTH, Icon.TEXTURE_HEIGHT);
    private static final EnumMap<Icon, SpriteTexture> NORMAL = new EnumMap<>(Icon.class);
    private static final EnumMap<Icon, SpriteTexture> DISABLED = new EnumMap<>(Icon.class);

    private LdlibAe2Icons() {
    }

    public static IGuiTexture texture(Icon icon) {
        return NORMAL.computeIfAbsent(icon, LdlibAe2Icons::sprite);
    }

    public static IGuiTexture texture(Icon icon, boolean active) {
        // Blitter.opacity(0.5F) truncates the disabled half-size alpha to 127.
        return active ? texture(icon) : DISABLED.computeIfAbsent(icon, glyph -> sprite(glyph).setColor(0x7FFFFFFF));
    }

    public static SpriteTexture sprite(Icon icon) {
        return new LdlibStyleSprite(Icon.TEXTURE, ATLAS_SIZE, icon.x, icon.y, icon.width, icon.height).full();
    }
}
