/* Adapted from Applied Energistics 2 forge/v15.4.10 (b4b08d9941e3faecb520d76be617629bb56661e1).
 * Original authors: TeamAppliedEnergistics. SPDX-License-Identifier: LGPL-3.0-or-later
 * Relocated for AE2-GTO, which no longer ships the JEI transfer implementation.
 */
package com.moakiee.ae2lt.compat.gto.jei;

import net.minecraft.world.item.crafting.Recipe;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;

public abstract class AbstractTransferHandler {
    protected static final int CRAFTING_GRID_WIDTH = 3;
    protected static final int CRAFTING_GRID_HEIGHT = 3;

    protected final boolean fitsIn3x3Grid(Recipe<?> recipe, IRecipeSlotsView display) {
        if (recipe != null) {
            return recipe.canCraftInDimensions(CRAFTING_GRID_WIDTH, CRAFTING_GRID_HEIGHT);
        } else {
            return true;
        }
    }
}
