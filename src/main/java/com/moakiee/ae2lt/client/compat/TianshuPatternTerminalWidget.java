package com.moakiee.ae2lt.client.compat;

import appeng.menu.SlotSemantics;
import com.illusivesoulworks.polymorph.api.client.widgets.PlayerRecipesWidget;
import com.moakiee.ae2lt.client.TianshuPatternEncodingTermScreen;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;

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
    public void selectRecipe(Identifier id) {
        super.selectRecipe(id);
        screen.getMenu().getPlayer().level().getRecipeManager().byKey(id)
                .ifPresent(recipe -> screen.getMenu().refreshPolymorphRecipe());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (isCraftingMode()) super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        return isCraftingMode() && super.mouseClicked(event, handled);
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
                Identifier.fromNamespaceAndPath("polyeng", name),
                Identifier.fromNamespaceAndPath("polyeng", name + "_highlighted"));
    }
}
