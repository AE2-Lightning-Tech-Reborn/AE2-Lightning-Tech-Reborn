package com.moakiee.ae2lt.compat;

import com.google.gson.JsonObject;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Backports recipe-id criteria while retaining actual crafting as the advancement condition. */
@Mod.EventBusSubscriber(modid = "ae2lt")
public final class LegacyRecipeCraftedTrigger extends SimpleCriterionTrigger<LegacyRecipeCraftedTrigger.Instance> {
    public static final ResourceLocation ID = new ResourceLocation("ae2lt", "recipe_crafted");
    public static final LegacyRecipeCraftedTrigger INSTANCE = new LegacyRecipeCraftedTrigger();
    public static void register() { CriteriaTriggers.register(INSTANCE); }
    @Override public ResourceLocation getId() { return ID; }
    @Override protected Instance createInstance(JsonObject json, EntityPredicate.Composite player, DeserializationContext context) {
        return new Instance(player, new ResourceLocation(json.get("recipe_id").getAsString()));
    }
    public void crafted(ServerPlayer player, ResourceLocation recipe) { trigger(player, instance -> instance.recipe.equals(recipe)); }
    @SubscribeEvent public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getInventory() instanceof CraftingContainer grid) {
            player.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, player.level)
                    .ifPresent(recipe -> INSTANCE.crafted(player, recipe.getId()));
        }
    }
    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final ResourceLocation recipe;
        Instance(EntityPredicate.Composite player, ResourceLocation recipe) { super(ID, player); this.recipe = recipe; }
        @Override public JsonObject serializeToJson(SerializationContext context) {
            var json = super.serializeToJson(context); json.addProperty("recipe_id", recipe.toString()); return json;
        }
    }
}
