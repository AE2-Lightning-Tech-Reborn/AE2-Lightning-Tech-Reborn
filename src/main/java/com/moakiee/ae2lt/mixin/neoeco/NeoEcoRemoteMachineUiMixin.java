package com.moakiee.ae2lt.mixin.neoeco;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** The config plugin isolates the client lookup before JVM verification of this optional class. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.compat.ae2.ECORemoteMachineUI$1", remap = false)
public abstract class NeoEcoRemoteMachineUiMixin {}
