package com.moakiee.ae2lt.compat.neoeco;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Loaded only when LDLib decodes the remote machine holder on the client. */
public final class NeoEcoClientWorld {
    private NeoEcoClientWorld() {}
    public static Level current() { return Minecraft.getInstance().level; }
}
