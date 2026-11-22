package com.moakiee.ae2lt.client.compat;

import java.util.Set;

import appeng.menu.SlotSemantics;
import com.illusivesoulworks.polymorph.api.client.widgets.PlayerRecipesWidget;
import com.illusivesoulworks.polymorph.api.client.widgets.children.OpenSelectionButton;
import com.illusivesoulworks.polymorph.api.client.widgets.children.OutputWidget;
import com.illusivesoulworks.polymorph.api.common.base.IRecipePair;
import com.illusivesoulworks.polymorph.common.util.RecipePair;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.VanillaSpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Widget;
import com.moakiee.ae2lt.client.tianshu.TianshuPatternEncodingTermScreen;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Polymorph recipe selector for the custom Tianshu pattern-terminal screen. */
final class TianshuPatternTerminalWidget extends PlayerRecipesWidget {
    private static final WidgetSprites OUTPUT = sprites("output_button");
    private static final WidgetSprites CURRENT_OUTPUT = sprites("current_output");
    private static final WidgetSprites SELECTOR = sprites("selector_button");

    private final TianshuPatternEncodingTermScreen<?> screen;

    TianshuPatternTerminalWidget(TianshuPatternEncodingTermScreen<?> screen) {
        super(screen, screen.getMenu().getSlots(SlotSemantics.CRAFTING_RESULT).getFirst());
        this.screen = screen;
    }

    @Override
    public void initChildWidgets() {
        super.initChildWidgets();
        var selector = new RecipeSelectorButton(screen, getXPos(), getYPos(),
                () -> selectionWidget.setActive(!selectionWidget.isActive()));
        selector.visible = openButton.visible;
        openButton = selector;
    }

    @Override
    public void setRecipesList(Set<IRecipePair> recipes, ResourceLocation selected) {
        super.setRecipesList(recipes, selected);
        // Polymorph owns sorting, output placement, and the click and tooltip traversal.
        var outputs = selectionWidget.getOutputWidgets().listIterator();
        while (outputs.hasNext()) {
            var original = outputs.next();
            var replacement = new RecipeOutputWidget(new RecipePair(original.getResourceLocation(), original.getOutput()));
            replacement.setPosition(original.getX(), original.getY());
            replacement.setHighlighted(original.getResourceLocation().equals(selected));
            outputs.set(replacement);
        }
    }

    @Override
    public void selectRecipe(ResourceLocation id) {
        super.selectRecipe(id);
        screen.getMenu().getPlayer().level().getRecipeManager().byKey(id)
                .ifPresent(recipe -> screen.getMenu().refreshPolymorphRecipe());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isCraftingMode()) super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return isCraftingMode() && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public Pair<WidgetSprites, WidgetSprites> getOutputSprites() {
        return Pair.of(OUTPUT, CURRENT_OUTPUT);
    }

    @Override
    public WidgetSprites getSelectorSprites() {
        return SELECTOR;
    }

    private boolean isCraftingMode() {
        return screen.getMenu().tianshuMode == TianshuEncodingMode.CRAFTING;
    }

    private static WidgetSprites sprites(String name) {
        return new WidgetSprites(
                ResourceLocation.fromNamespaceAndPath("polyeng", name),
                ResourceLocation.fromNamespaceAndPath("polyeng", name + "_highlighted"));
    }

    private static LdlibAe2Widget buttonFace(int width, int height, IGuiTexture texture,
            boolean allowRightClick, Runnable action) {
        var control = new Button().noText().setOnClick(event -> {
            event.stopPropagation();
            action.run();
        });
        if (allowRightClick) {
            control.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    event.stopPropagation();
                    UISoundUtils.playButtonClickSound();
                    action.run();
                }
            });
        }
        control.buttonStyle(style -> style.baseTexture(texture).hoverTexture(texture).pressedTexture(texture));
        return new LdlibAe2Widget(width, height, control) {
            @Override
            protected boolean handlesInput() {
                return true;
            }
        };
    }

    private static final class RecipeSelectorButton extends OpenSelectionButton {
        private final TianshuPatternEncodingTermScreen<?> screen;
        private final LdlibAe2Widget face;
        private int offsetX;
        private int offsetY;
        private boolean highlighted;

        private RecipeSelectorButton(TianshuPatternEncodingTermScreen<?> screen, int x, int y, Runnable action) {
            super(screen, x, y, SELECTOR, ignored -> action.run());
            this.screen = screen;
            setOffsets(x, y);
            var normal = VanillaSpriteTexture.of(SELECTOR.enabled());
            var hover = VanillaSpriteTexture.of(SELECTOR.enabledFocused());
            face = buttonFace(getWidth(), getHeight(),
                    IGuiTexture.dynamic(() -> active && highlighted ? hover : normal), false, this::onPress);
        }

        @Override
        public void setOffsets(int x, int y) {
            super.setOffsets(x, y);
            offsetX = x;
            offsetY = y;
        }

        private void positionFace() {
            setPosition(screen.getGuiLeft() + offsetX, screen.getGuiTop() + offsetY);
            face.setPosition(getX(), getY());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!active || !visible || !isValidClickButton(button)) {
                return false;
            }
            positionFace();
            return face.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            positionFace();
            highlighted = isFocused() || isMouseOver(mouseX, mouseY);
            face.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class RecipeOutputWidget extends OutputWidget {
        private static final int FRAME_DEPTH = 600;
        private static final int OUTPUT_DEPTH = 700;
        private static final int LDLIB_ITEM_DEPTH = 232;
        private static final int ITEM_INSET = 4;
        private static final IGuiTexture IDLE = VanillaSpriteTexture.of(OUTPUT.enabled());
        private static final IGuiTexture HOVER = VanillaSpriteTexture.of(OUTPUT.enabledFocused());
        private static final IGuiTexture SELECTED = VanillaSpriteTexture.of(CURRENT_OUTPUT.enabled());
        private static final IGuiTexture SELECTED_HOVER = VanillaSpriteTexture.of(CURRENT_OUTPUT.enabledFocused());
        private final LdlibAe2Widget face;
        private boolean selected;
        private boolean hovered;

        private RecipeOutputWidget(IRecipePair recipe) {
            super(Pair.of(OUTPUT, CURRENT_OUTPUT), recipe);
            // SelectionWidget consumes a successful click and submits this output's recipe ID.
            face = buttonFace(getWidth(), getHeight(),
                    IGuiTexture.dynamic(() -> selected ? hovered ? SELECTED_HOVER : SELECTED : hovered ? HOVER : IDLE),
                    true, () -> {});
        }

        @Override
        public void setHighlighted(boolean highlighted) {
            super.setHighlighted(highlighted);
            selected = highlighted;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!active || !visible || !isValidClickButton(button)) {
                return false;
            }
            face.setPosition(getX(), getY());
            return face.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            hovered = mouseX >= getX() && mouseX < getX() + getWidth()
                    && mouseY >= getY() && mouseY < getY() + getHeight();
            face.setPosition(getX(), getY());
            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(0, 0, FRAME_DEPTH);
                face.render(graphics, mouseX, mouseY, partialTick);
                // Account for the offset applied by DrawerHelper to the item and its decorations.
                pose.translate(0, 0, OUTPUT_DEPTH - FRAME_DEPTH - LDLIB_ITEM_DEPTH);
                DrawerHelper.drawItemStack(graphics, getOutput(), getX() + ITEM_INSET, getY() + ITEM_INSET,
                        0xFFFFFFFF, null);
            } finally {
                pose.popPose();
            }
        }
    }
}
