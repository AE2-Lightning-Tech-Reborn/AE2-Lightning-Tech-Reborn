package com.moakiee.ae2lt.menu.hub;

import java.util.UUID;

/** Identity, slot and nonce together prevent delayed actions from targeting a replacement device. */
public final class DeviceSession {
    private Object device;
    private int slot;
    private UUID nonce = UUID.randomUUID();
    public UUID bind(Object current, int currentSlot) {
        if (device != current || slot != currentSlot) {
            device = current; slot = currentSlot; nonce = UUID.randomUUID();
        }
        return nonce;
    }
    public boolean matches(UUID token, Object current, int currentSlot) {
        return device == current && slot == currentSlot && nonce.equals(token);
    }
    public void clear() { device = null; nonce = UUID.randomUUID(); }
}
