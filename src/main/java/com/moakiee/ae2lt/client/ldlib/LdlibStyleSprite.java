package com.moakiee.ae2lt.client.ldlib;

import appeng.client.gui.style.Blitter;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.math.Size;
import com.moakiee.ae2lt.mixin.client.BlitterAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** An atlas region with LDLib textures and native immediate draws at fixed reference dimensions. */
public record LdlibStyleSprite(ResourceLocation texture, Size atlasSize, int x, int y, int width, int height) {
    public LdlibStyleSprite(ResourceLocation texture, int width, int height) {
        this(texture, Size.of(width, height), 0, 0, width, height);
    }

    public static LdlibStyleSprite from(Blitter image) {
        var atlas = (BlitterAccessor) (Object) image;
        return new LdlibStyleSprite(atlas.ae2lt$getTexture(),
                Size.of(atlas.ae2lt$getReferenceWidth(), atlas.ae2lt$getReferenceHeight()),
                image.getSrcX(), image.getSrcY(), image.getSrcWidth(), image.getSrcHeight());
    }

    public SpriteTexture full() {
        return slice(0, 0, width, height);
    }

    public SpriteTexture slice(int offsetX, int offsetY, int width, int height) {
        // The reference size keeps UVs stable when a resource pack scales an atlas.
        return new AtlasSpriteTexture(atlasSize).setImageLocation(texture)
                .setSprite(x + offsetX, y + offsetY, width, height);
    }

    public void draw(GuiGraphics graphics, int x, int y,
            int u, int v, int width, int height) {
        draw(graphics, x, y, width, height, u, v, width, height);
    }

    public void draw(GuiGraphics graphics, int x, int y, int width, int height,
            int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(texture, x, y, width, height,
                this.x + u, this.y + v, sourceWidth, sourceHeight, atlasSize.getWidth(), atlasSize.getHeight());
    }

    /** Draws an atlas slice at its original pixel size inside a differently sized hit box. */
    public IGuiTexture fixedSlice(int offsetX, int offsetY, int width, int height) {
        var sprite = slice(offsetX, offsetY, width, height);
        return (graphics, mouseX, mouseY, x, y, boxWidth, boxHeight, ticks) ->
                sprite.draw(graphics, mouseX, mouseY, x, y, width, height, ticks);
    }

    public SpriteTexture[] verticalSlices(boolean fromBottom) {
        var frames = new SpriteTexture[height + 1];
        for (int rows = 1; rows <= height; rows++) {
            frames[rows] = slice(0, fromBottom ? height - rows : 0, width, rows);
        }
        return frames;
    }

    public IGuiTexture[] horizontalSlices(int offsetX, int offsetY, int width, int height) {
        var slices = new IGuiTexture[width + 1];
        slices[0] = IGuiTexture.EMPTY;
        for (int pixels = 1; pixels <= width; pixels++) {
            slices[pixels] = slice(offsetX, offsetY, pixels, height);
        }
        return slices;
    }

    private static final class AtlasSpriteTexture extends SpriteTexture {
        private final Size atlasSize;

        private AtlasSpriteTexture(Size atlasSize) {
            this.atlasSize = atlasSize;
        }

        @Override
        public Size getImageSize() {
            return atlasSize;
        }

        @Override
        public SpriteTexture copy() {
            var copy = new AtlasSpriteTexture(atlasSize);
            copy.setImageLocation(getImageLocation())
                    .setSpritePosition(spritePosition)
                    .setSpriteSize(spriteSize)
                    .setBorderLT(borderLT)
                    .setBorderRB(borderRB)
                    .setColor(color)
                    .setWrapMode(wrapMode);
            copy.copyTransform(this);
            return copy;
        }
    }
}
