package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.moakiee.ae2lt.util.LargeStackNbt;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class CrystalCatalyzerLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_OUTPUT_MULTIPLIER = "OutputMultiplier";
    private static final String TAG_LIGHTNING_COST = "LightningCost";
    private static final String TAG_LIGHTNING_TIER = "LightningTier";
    private static final String TAG_FLUID_INPUT = "InputFluid";

    private final ResourceLocation recipeId;
    private final ItemStack output;
    private final int energyPerCycle;
    private final int outputMultiplier;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;
    private final FluidStack fluidInput;

    public CrystalCatalyzerLockedRecipe(
            ResourceLocation recipeId,
            ItemStack output,
            int energyPerCycle,
            int outputMultiplier,
            int lightningCost,
            LightningKey.Tier lightningTier) {
        this(recipeId, output, energyPerCycle, outputMultiplier, lightningCost, lightningTier,
                CrystalCatalyzerRecipe.defaultFluidInput());
    }

    public CrystalCatalyzerLockedRecipe(
            ResourceLocation recipeId,
            ItemStack output,
            int energyPerCycle,
            int outputMultiplier,
            int lightningCost,
            LightningKey.Tier lightningTier,
            FluidStack fluidInput) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.output = Objects.requireNonNull(output, "output").copy();
        this.energyPerCycle = energyPerCycle;
        this.outputMultiplier = outputMultiplier;
        this.lightningCost = lightningCost;
        this.lightningTier = Objects.requireNonNull(lightningTier, "lightningTier");
        this.fluidInput = Objects.requireNonNull(fluidInput, "fluidInput").copy();
        if (fluidInput.isEmpty()) {
            throw new IllegalArgumentException("inputFluid cannot be empty");
        }
        if (output.isEmpty()) {
            throw new IllegalArgumentException("output cannot be empty");
        }
        if (energyPerCycle < 0) {
            throw new IllegalArgumentException("energyPerCycle must be non-negative");
        }
        if (outputMultiplier <= 0) {
            throw new IllegalArgumentException("outputMultiplier must be positive");
        }
        if (lightningCost < 0) {
            throw new IllegalArgumentException("lightningCost must be non-negative");
        }
    }

    public static CrystalCatalyzerLockedRecipe fromCandidate(
            CrystalCatalyzerRecipeCandidate candidate,
            int outputMultiplier) {
        CrystalCatalyzerRecipe recipe = candidate.recipe();
        return new CrystalCatalyzerLockedRecipe(
                recipe.getId(),
                recipe.getOutputTemplate(),
                recipe.energyPerCycle(),
                outputMultiplier,
                recipe.lightningCost(),
                recipe.lightningTier(),
                recipe.fluidInput());
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public ItemStack output() {
        return output.copy();
    }

    public int energyPerCycle() {
        return energyPerCycle;
    }

    public int outputMultiplier() {
        return outputMultiplier;
    }

    public int lightningCost() {
        return lightningCost;
    }

    public LightningKey.Tier lightningTier() {
        return lightningTier;
    }

    public long totalEnergy() {
        return energyPerCycle;
    }

    public FluidStack fluidInput() {
        return fluidInput.copy();
    }

    public boolean matchesFluidInput(CrystalCatalyzerRecipe recipe) {
        var required = recipe.fluidInput();
        return fluidInput.isFluidEqual(required)
                && fluidInput.getAmount() == required.getAmount();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        tag.put(TAG_OUTPUT, LargeStackNbt.save(output));
        tag.putInt(TAG_ENERGY, energyPerCycle);
        tag.putInt(TAG_OUTPUT_MULTIPLIER, outputMultiplier);
        tag.putInt(TAG_LIGHTNING_COST, lightningCost);
        tag.putString(TAG_LIGHTNING_TIER, lightningTier.getSerializedName());
        tag.put(TAG_FLUID_INPUT, fluidInput.writeToNBT(new CompoundTag()));
        return tag;
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(CompoundTag tag) {
        return fromTag(tag, 1);
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(
            CompoundTag tag,
            int defaultOutputMultiplier) {
        if (!tag.contains(TAG_RECIPE_ID) || !tag.contains(TAG_OUTPUT, Tag.TAG_COMPOUND)) {
            return null;
        }

        ItemStack output = LargeStackNbt.load(tag.getCompound(TAG_OUTPUT));
        if (output.isEmpty()) {
            return null;
        }

        int energy = tag.getInt(TAG_ENERGY);
        if (energy < 0) {
            return null;
        }

        int outputMultiplier = tag.contains(TAG_OUTPUT_MULTIPLIER, Tag.TAG_INT)
                ? tag.getInt(TAG_OUTPUT_MULTIPLIER)
                : defaultOutputMultiplier;
        if (outputMultiplier <= 0) {
            return null;
        }

        int lightningCost = tag.contains(TAG_LIGHTNING_COST, Tag.TAG_ANY_NUMERIC)
                ? tag.getInt(TAG_LIGHTNING_COST)
                : CrystalCatalyzerRecipe.DEFAULT_LIGHTNING_COST;
        if (lightningCost < 0) {
            lightningCost = 0;
        }

        LightningKey.Tier lightningTier = tag.contains(TAG_LIGHTNING_TIER, Tag.TAG_STRING)
                ? LightningKey.Tier.fromSerializedName(tag.getString(TAG_LIGHTNING_TIER))
                : CrystalCatalyzerRecipe.DEFAULT_LIGHTNING_TIER;

        FluidStack fluidInput = tag.contains(TAG_FLUID_INPUT)
                ? FluidStack.loadFluidStackFromNBT(tag.getCompound(TAG_FLUID_INPUT))
                : CrystalCatalyzerRecipe.defaultFluidInput();
        if (fluidInput.isEmpty()) {
            return null;
        }

        return new CrystalCatalyzerLockedRecipe(
                ResourceLocation.tryParse(tag.getString(TAG_RECIPE_ID)),
                output,
                energy,
                outputMultiplier,
                lightningCost,
                lightningTier,
                fluidInput);
    }
}
