package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.moakiee.ae2lt.me.key.LightningKey;

public final class CrystalCatalyzerLockedRecipe {
    private static final String TAG_RECIPE_ID = "RecipeId";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_OUTPUT_MULTIPLIER = "OutputMultiplier";
    private static final String TAG_LIGHTNING_COST = "LightningCost";
    private static final String TAG_LIGHTNING_TIER = "LightningTier";

    private final Identifier recipeId;
    private final ItemStack output;
    private final int energyPerCycle;
    private final int outputMultiplier;
    private final int lightningCost;
    private final LightningKey.Tier lightningTier;

    public CrystalCatalyzerLockedRecipe(
            Identifier recipeId,
            ItemStack output,
            int energyPerCycle,
            int outputMultiplier,
            int lightningCost,
            LightningKey.Tier lightningTier) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.output = Objects.requireNonNull(output, "output").copy();
        this.energyPerCycle = energyPerCycle;
        this.outputMultiplier = outputMultiplier;
        this.lightningCost = lightningCost;
        this.lightningTier = Objects.requireNonNull(lightningTier, "lightningTier");
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
        RecipeHolder<CrystalCatalyzerRecipe> holder = candidate.recipe();
        CrystalCatalyzerRecipe recipe = holder.value();
        return new CrystalCatalyzerLockedRecipe(
                holder.id().identifier(),
                recipe.getOutputTemplate(),
                recipe.energyPerCycle(),
                outputMultiplier,
                recipe.lightningCost(),
                recipe.lightningTier());
    }

    public Identifier recipeId() {
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

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE_ID, recipeId.toString());
        tag.put(TAG_OUTPUT, com.moakiee.ae2lt.recipe.compat.LegacyItemStackNbt.save(output, registries));
        tag.putInt(TAG_ENERGY, energyPerCycle);
        tag.putInt(TAG_OUTPUT_MULTIPLIER, outputMultiplier);
        tag.putInt(TAG_LIGHTNING_COST, lightningCost);
        tag.putString(TAG_LIGHTNING_TIER, lightningTier.getSerializedName());
        return tag;
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        return fromTag(tag, registries, 1);
    }

    @Nullable
    public static CrystalCatalyzerLockedRecipe fromTag(
            CompoundTag tag,
            HolderLookup.Provider registries,
            int defaultOutputMultiplier) {
        if (!tag.contains(TAG_RECIPE_ID) || !com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_OUTPUT, Tag.TAG_COMPOUND)) {
            return null;
        }

        ItemStack output = com.moakiee.ae2lt.recipe.compat.LegacyItemStackNbt.parseOptional(registries, tag.getCompoundOrEmpty(TAG_OUTPUT));
        if (output.isEmpty()) {
            return null;
        }

        int energy = tag.getIntOr(TAG_ENERGY, 0);
        if (energy < 0) {
            return null;
        }

        int outputMultiplier = com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_OUTPUT_MULTIPLIER, Tag.TAG_INT)
                ? tag.getIntOr(TAG_OUTPUT_MULTIPLIER, 0)
                : defaultOutputMultiplier;
        if (outputMultiplier <= 0) {
            return null;
        }

        int lightningCost = com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_LIGHTNING_COST, Tag.TAG_INT)
                ? tag.getIntOr(TAG_LIGHTNING_COST, 0) : 1;
        if (lightningCost < 0) {
            lightningCost = 0;
        }

        LightningKey.Tier lightningTier = LightningKey.Tier.HIGH_VOLTAGE;
        if (com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_LIGHTNING_TIER, Tag.TAG_STRING)) {
            String tierName = tag.getStringOr(TAG_LIGHTNING_TIER, "");
            for (LightningKey.Tier t : LightningKey.Tier.values()) {
                if (t.getSerializedName().equals(tierName)) {
                    lightningTier = t;
                    break;
                }
            }
        }

        return new CrystalCatalyzerLockedRecipe(
                Identifier.parse(tag.getStringOr(TAG_RECIPE_ID, "")),
                output,
                energy,
                outputMultiplier,
                lightningCost,
                lightningTier);
    }
}
