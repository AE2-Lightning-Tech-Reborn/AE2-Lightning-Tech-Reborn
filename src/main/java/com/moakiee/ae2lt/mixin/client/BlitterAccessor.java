package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.TextureTransform;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Blitter.class, remap = false)
public interface BlitterAccessor {
    @Accessor("texture")
    ResourceLocation ae2lt$getTexture();

    @Accessor("referenceWidth")
    int ae2lt$getReferenceWidth();

    @Accessor("referenceHeight")
    int ae2lt$getReferenceHeight();

    @Accessor("srcRect")
    @Nullable Rect2i ae2lt$getSourceRect();

    @Accessor("transform")
    TextureTransform ae2lt$getTransform();

    @Accessor("blending")
    boolean ae2lt$isBlending();

    @Accessor("zOffset")
    int ae2lt$getZOffset();

    @Accessor("r")
    int ae2lt$getRed();

    @Accessor("g")
    int ae2lt$getGreen();

    @Accessor("b")
    int ae2lt$getBlue();

    @Accessor("a")
    int ae2lt$getAlpha();
}
