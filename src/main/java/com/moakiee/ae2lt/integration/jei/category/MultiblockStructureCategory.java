package com.moakiee.ae2lt.integration.jei.category;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.integration.xei.XEITooltipContext;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIJEIWidget;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import com.lowdragmc.lowdraglib2.integration.xei.jei.handler.JEIRecipeSlotHandler;
import com.lowdragmc.lowdraglib2.integration.xei.jei.handler.JEIRecipeSlotHandler.Binding;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockPreviewUI;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipe;
import com.moakiee.ae2lt.registry.ModBlocks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Hosts a fresh LDLib2 preview per JEI layout, with native slot drawing and metadata. */
public final class MultiblockStructureCategory extends ModularUIRecipeCategory<MultiblockStructureRecipe> {
    public static final int WIDTH = MultiblockPreviewUI.DEFAULT_WIDTH;
    public static final int HEIGHT = MultiblockPreviewUI.DEFAULT_HEIGHT;

    private static final int RECIPE_BORDER_PADDING = 4;
    private static final String TRANSFER_SLOT_PREFIX = "multiblock_transfer_";

    public static final RecipeType<MultiblockStructureRecipe> TYPE = RecipeType.create(
            AE2LightningTech.MODID,
            "multiblock_structure",
            MultiblockStructureRecipe.class);

    private final IDrawable icon;
    // Neither side roots a discarded layout, including after its renderer refers back to its slots.
    private final Map<IRecipeSlotDrawable, WeakReference<Layout>> layouts = new WeakHashMap<>();
    private Layout pendingLayout;
    private int layoutHeight = HEIGHT;

    public MultiblockStructureCategory(mezz.jei.api.helpers.IGuiHelper guiHelper) {
        super(MultiblockPreviewUI::create);
        this.icon = guiHelper.createDrawableItemStack(ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.toStack());
    }

    @Override
    public RecipeType<MultiblockStructureRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2lt.multiblock.title");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return layoutHeight;
    }

    /** Called before JEI creates layouts, using the actual area below its navigation bars. */
    public boolean setAvailableHeight(int availableHeight, int minRecipePadding) {
        int height = Math.max(HEIGHT,
                availableHeight - 2 * (RECIPE_BORDER_PADDING + minRecipePadding));
        if (height == layoutHeight) {
            return false;
        }
        layoutHeight = height;
        return true;
    }

    @Override
    public ModularUI getUIForRecipe(MultiblockStructureRecipe recipe) {
        // A recipe can have several live layouts. Do not use the base's expiring recipe cache.
        var ui = MultiblockPreviewUI.create(recipe);
        ui.setAllowDebugMode(false);
        ui.setDrawTooltips(false);
        ui.init(WIDTH, layoutHeight);
        return ui;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MultiblockStructureRecipe recipe, IFocusGroup focuses) {
        var ui = getUIForRecipe(recipe);
        var layout = new Layout(recipe, ui, JEIRecipeSlotHandler.collectBindings(ui));
        // JEI 19.64 constructs slots and invokes displayed-update before returning from layout creation.
        // Metadata-only indexing never reaches that callback; retain at most its current build.
        pendingLayout = layout;
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStacks(recipe.focusStacks());
        for (var binding : layout.bindings()) {
            var element = binding.element();
            var slot = builder.addSlot(binding.role(), Math.round(element.getPositionX()),
                    Math.round(element.getPositionY()))
                    .setSlotName(binding.name())
                    .setStandardSlotBackground();
            slot.addTypedIngredients(binding.ingredients());
            // The slot callback also keeps this exact layout alive until JEI discards its slots.
            slot.addRichTooltipCallback((view, tooltip) -> layout.appendTooltip(binding, tooltip));
        }
        for (int i = 0; i < recipe.materials().size(); i++) {
            var material = recipe.materials().get(i);
            builder.addSlot(RecipeIngredientRole.INPUT)
                    .setSlotName(TRANSFER_SLOT_PREFIX + i)
                    .addItemStack(new ItemStack(material.block(), material.count()));
        }
        builder.moveRecipeTransferButton(WIDTH + 4, getHeight() - 14);
    }

    @Override
    public void onDisplayedIngredientsUpdate(MultiblockStructureRecipe recipe,
            List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
        var layout = findLayout(recipeSlots);
        var pending = pendingLayout;
        if (layout == null && pending != null && pending.recipe() == recipe
                && recipeSlots.stream().anyMatch(slot -> slot.getSlotName()
                        .filter(name -> pending.bindings().stream().anyMatch(binding -> binding.name().equals(name)))
                        .isPresent())) {
            layout = pending;
            pendingLayout = null;
            var reference = new WeakReference<>(layout);
            for (var slot : recipeSlots) {
                layouts.put(slot, reference);
            }
        }
        if (layout != null) {
            layout.updateDisplayedIngredients();
        } else {
            // JEI removes claimed widget slots from later update lists. Each live layout must
            // still sync its own bound drawables; an empty list must not consume metadata indexing.
            var updated = new IdentityHashMap<Layout, Boolean>();
            for (var reference : layouts.values()) {
                var candidate = reference.get();
                if (candidate != null && candidate.recipe() == recipe
                        && updated.put(candidate, Boolean.TRUE) == null) {
                    candidate.updateDisplayedIngredients();
                }
            }
        }
    }

    private Layout findLayout(List<IRecipeSlotDrawable> recipeSlots) {
        for (var slot : recipeSlots) {
            var reference = layouts.get(slot);
            if (reference != null) {
                var layout = reference.get();
                if (layout != null) {
                    return layout;
                }
            }
        }
        return null;
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MultiblockStructureRecipe recipe,
            IFocusGroup focuses) {
        var layout = findLayout(builder.getRecipeSlots().getSlots());
        if (layout == null) {
            throw new IllegalStateException("Missing multiblock layout bindings");
        }
        int height = builder instanceof IRecipeLayoutDrawable<?> drawable
                ? drawable.getRect().getHeight() : getHeight();
        var ui = layout.ui();
        if (ui.getScreenWidth() != WIDTH || ui.getScreenHeight() != height) {
            ui.init(WIDTH, height);
        }
        var widget = new ModularUIJEIWidget(ui) {
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                // JEI's press phase is the original preview's simulation phase.
                var mouse = getWorldMouse((float) mouseX, (float) mouseY);
                ui.refreshHoveredElement(mouse.x, mouse.y);
                return MultiblockPreviewUI.acceptsMouseClick(ui, mouse.x, mouse.y, button);
            }

            @Override
            public boolean mouseReleased(double mouseX, double mouseY, int button) {
                boolean handled = super.mouseClicked(mouseX, mouseY, button);
                super.mouseReleased(mouseX, mouseY, button);
                return handled;
            }
        };
        var slots = new IdentityHashMap<UIElement, IRecipeSlotDrawable>();
        var visibleSlots = new ArrayList<IRecipeSlotDrawable>();
        for (var binding : layout.bindings()) {
            var slot = builder.getRecipeSlots().findSlotByName(binding.name())
                    .orElseThrow(() -> new IllegalStateException("Missing JEI slot: " + binding.name()));
            slots.put(binding.element(), slot);
            visibleSlots.add(slot);
            if (binding.slotUpdater() != null) {
                binding.slotUpdater().bind(slot);
            }
        }
        MultiblockPreviewUI.setSlotRenderer(ui, (context, element) -> {
            var slot = slots.get(element);
            slot.setPosition(Math.round(element.getPositionX()), Math.round(element.getPositionY()));
            slot.draw(context.graphics);
        });
        builder.addWidget(widget);
        builder.addGuiEventListener(widget);
        builder.addSlottedWidget(new LayoutSlots(layout.bindings(), slots, widget), visibleSlots);

        List<IRecipeSlotDrawable> transferSlots = new ArrayList<>();
        for (int i = 0; i < recipe.materials().size(); i++) {
            builder.getRecipeSlots().findSlotByName(TRANSFER_SLOT_PREFIX + i).ifPresent(transferSlots::add);
        }
        if (!transferSlots.isEmpty()) {
            builder.addSlottedWidget(HiddenSlotsWidget.INSTANCE, transferSlots);
        }
    }

    @Override
    public ResourceLocation getRegistryName(MultiblockStructureRecipe recipe) {
        return recipe.id();
    }

    private record Layout(MultiblockStructureRecipe recipe, ModularUI ui, List<Binding> bindings) {
        private void updateDisplayedIngredients() {
            for (var binding : bindings) {
                if (binding.slotUpdater() != null) {
                    binding.slotUpdater().onDisplayedIngredientsUpdate();
                }
            }
        }

        private void appendTooltip(Binding binding, ITooltipBuilder tooltip) {
            var additional = XEITooltipContext.RECIPE_SLOT.collectTooltips(binding.element());
            if (additional != null) {
                tooltip.addAll(additional.tooltipTexts());
                if (additional.tooltipComponent() != null) {
                    tooltip.add(additional.tooltipComponent());
                }
            }
        }
    }

    /** Same local-to-world lookup protocol as LDLib's native slotted widget. */
    private record LayoutSlots(List<Binding> bindings, Map<UIElement, IRecipeSlotDrawable> slots,
            ModularUIJEIWidget widget) implements ISlottedRecipeWidget {
        @Override
        public ScreenPosition getPosition() {
            return HiddenSlotsWidget.POSITION;
        }

        @Override
        public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
            var mouse = widget.getWorldMouse((float) mouseX, (float) mouseY);
            widget.modularUI.refreshHoveredElement(mouse.x, mouse.y);
            for (var binding : bindings) {
                if (binding.isInteractive() && binding.element().isMouseOverElement(mouse.x, mouse.y)) {
                    return Optional.of(new RecipeSlotUnderMouse(slots.get(binding.element()), 0, 0));
                }
            }
            return Optional.empty();
        }
    }

    /** Claims slots that exist only for lookup and transfer, without drawing them. */
    private enum HiddenSlotsWidget implements ISlottedRecipeWidget {
        INSTANCE;

        private static final ScreenPosition POSITION = new ScreenPosition(0, 0);

        @Override
        public ScreenPosition getPosition() {
            return POSITION;
        }

        @Override
        public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
            return Optional.empty();
        }
    }
}
