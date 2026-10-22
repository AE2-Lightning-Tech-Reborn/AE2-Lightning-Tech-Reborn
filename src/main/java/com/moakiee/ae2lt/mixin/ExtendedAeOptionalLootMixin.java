package com.moakiee.ae2lt.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.moakiee.ae2lt.compat.ExtendedAeLootCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ForgeHooks.class, remap = false)
public abstract class ExtendedAeOptionalLootMixin {
    @ModifyVariable(method = "loadLootTable", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static JsonElement ae2lt$optionalAppliedEDrop(
            JsonElement json, Gson gson, ResourceLocation table, JsonElement original, boolean custom) {
        return ExtendedAeLootCompat.sanitize(table, json, BuiltInRegistries.ITEM.containsKey(ExtendedAeLootCompat.ITEM));
    }
}
