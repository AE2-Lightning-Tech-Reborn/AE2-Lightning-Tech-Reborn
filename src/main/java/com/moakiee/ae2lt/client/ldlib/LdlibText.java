package com.moakiee.ae2lt.client.ldlib;

import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

/** Retains Font's String shaping and formatting before submitting through LDLib. */
public final class LdlibText {
    private LdlibText() {
    }

    public static int drawString(GuiGraphics graphics, Font font, String text, float x, float y,
            int color, boolean shadow) {
        if (text == null) {
            return 0;
        }
        String displayText = font.isBidirectional() ? font.bidirectionalShaping(text) : text;
        FormattedCharSequence formatted = sink -> StringDecomposer.iterateFormatted(displayText, Style.EMPTY, sink);
        return LDLibFonts.drawText(graphics, font, formatted, x, y, color, shadow);
    }
}
