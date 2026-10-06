package com.moakiee.ae2lt.gametest;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.recipe.RainbowPigmeeColorCycleRecipe;
import com.moakiee.ae2lt.recipe.RainbowPigmeeDyeRecipe;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class RainbowPigmeeCraftingGameTests {
    private RainbowPigmeeCraftingGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void cycleKeepsItemDataAndReturnsCatalyst(GameTestHelper helper) {
        var recipe = (RainbowPigmeeColorCycleRecipe) helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation(AE2LightningTech.MODID, "rainbow_pigmee_color_cycle"))
                .orElseThrow();
        var input = input(2, 1);
        var pigmee = new ItemStack(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get());
        pigmee.setHoverName(Component.literal("Keep me"));
        var wool = new ItemStack(Items.RED_WOOL);
        wool.setHoverName(Component.literal("Keep color data"));
        input.setItem(0, pigmee);
        input.setItem(1, wool);

        helper.assertTrue(recipe.matches(input, helper.getLevel()), "cycle recipe must match");
        var result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(Items.BLACK_WOOL), "red must advance to black");
        helper.assertTrue(result.getHoverName().getString().equals("Keep color data"), "target name must survive");
        helper.assertTrue(ItemStack.matches(recipe.getRemainingItems(input).get(0), pigmee),
                "catalyst must retain its data");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void dyeLayoutsProduceFourAndReturnCatalyst(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getServer().getAdvancements().getAdvancement(
                new ResourceLocation(AE2LightningTech.MODID, "recipes/dye_base")) != null,
                "dye base advancement must load");
        for (var color : DyeColor.values()) {
            helper.assertTrue(helper.getLevel().getServer().getAdvancements().getAdvancement(
                    new ResourceLocation(AE2LightningTech.MODID,
                            "recipes/rainbow_dye/" + color.getName())) != null,
                    "dye advancement must load: " + color);
            var recipe = (RainbowPigmeeDyeRecipe) helper.getLevel().getRecipeManager()
                    .byKey(new ResourceLocation(AE2LightningTech.MODID,
                            "rainbow_dye/" + color.getName())).orElseThrow();
            var input = input(3, 3);
            for (int slot = 0; slot < 9; slot++) {
                if (slot == 4) {
                    input.setItem(slot, new ItemStack(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get()));
                } else if (!recipe.getIngredients().get(slot).isEmpty()) {
                    input.setItem(slot, new ItemStack(ModItems.DYE_BASE.get()));
                }
            }
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "dye layout must match: " + color);
            var dye = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(dye.getCount() == 4 && dye.getItem() == recipe.getResultItem(null).getItem(),
                    "dye layout must produce four: " + color);
            helper.assertTrue(recipe.getRemainingItems(input).get(4).is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get()),
                    "dye catalyst must remain: " + color);

            var wool = new ItemStack(Items.WHITE_WOOL);
            wool.setHoverName(Component.literal("Preserve data"));
            for (int slot = 0; slot < 9; slot++) {
                if (slot != 4 && !recipe.getIngredients().get(slot).isEmpty()) {
                    input.setItem(slot, wool.copy());
                }
            }
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "recolor layout must match: " + color);
            var recolored = recipe.assemble(input, helper.getLevel().registryAccess());
            var expected = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", color.getName() + "_wool"));
            helper.assertTrue(recolored.is(expected) && recolored.getCount() == 4
                    && recolored.getHoverName().getString().equals("Preserve data"),
                    "recolor must preserve item data: " + color);
        }
        helper.succeed();
    }

    private static CraftingContainer input(int width, int height) {
        var menu = new AbstractContainerMenu(MenuType.CRAFTING, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
        return new TransientCraftingContainer(menu, width, height);
    }
}
