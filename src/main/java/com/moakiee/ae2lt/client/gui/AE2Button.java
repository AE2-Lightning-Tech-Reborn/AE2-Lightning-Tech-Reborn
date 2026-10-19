package com.moakiee.ae2lt.client.gui;

import com.moakiee.ae2lt.client.compat.Button;
import net.minecraft.network.chat.Component;

/**
 * Lightweight 1.19.2 compatibility shim for screens ported from newer AE2 UI code.
 */
public class AE2Button extends Button {
    public AE2Button(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    /** Convenience ctor for callers that position/resize the button later. */
    public AE2Button(Component message, OnPress onPress) {
        this(0, 0, 0, 0, message, onPress);
    }
}
