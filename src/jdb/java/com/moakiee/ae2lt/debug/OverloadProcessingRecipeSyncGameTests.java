package com.moakiee.ae2lt.debug;

import com.google.gson.JsonParser;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingIngredient;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercises the same recipe packet codec used when a remote player joins. */
@GameTestHolder("ae2lt_recipe_sync")
@PrefixGameTestTemplate(false)
public final class OverloadProcessingRecipeSyncGameTests {
    private static final int[] COUNTS = {1, 64, 65, 99, 100, 127, 128, 255, 256, 16384};

    @GameTest(templateNamespace = "ae2lt_main_fixes", template = "empty")
    public static void programmaticRecipesSurviveLoginSync(GameTestHelper helper) {
        var failures = new ArrayList<String>();
        for (int count : COUNTS) {
            for (boolean largeOutput : List.of(false, true)) {
                int inputCount = largeOutput ? 1 : count;
                int outputCount = largeOutput ? count : 1;
                try {
                    var output = new ItemStack(Items.DIAMOND, outputCount);
                    output.setHoverName(Component.literal("Bulk result"));
                    var recipe = new OverloadProcessingRecipe(new ResourceLocation("ae2lt:large_count_regression"), 3,
                            List.of(new OverloadProcessingIngredient(Ingredient.of(Items.STONE), inputCount)),
                            FluidStack.EMPTY, List.of(output), FluidStack.EMPTY,
                            500, 4, LightningKey.Tier.HIGH_VOLTAGE);
                    var copy = loginRoundTrip(helper, recipe);
                    check(copy.itemInputs().get(0).count() == inputCount, "input count changed");
                    check(copy.itemResults().get(0).getCount() == outputCount, "output count changed or became empty");
                    check(ItemStack.isSameItemSameTags(output, copy.itemResults().get(0)), "result components changed");
                    System.out.println("OVERLOAD_RECIPE_SYNC_PASS input=" + inputCount + " output=" + outputCount);
                } catch (Exception | AssertionError e) {
                    failures.add("input=" + inputCount + " output=" + outputCount + ": " + e);
                }
            }
        }
        check(failures.isEmpty(), String.join("\n", failures));
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_main_fixes", template = "empty")
    public static void datapackRecipesKeepLargeResults(GameTestHelper helper) {
        var failures = new ArrayList<String>();
        for (int count : COUNTS) {
            try {
                var json = JsonParser.parseString("""
                        {"inputs":[{"ingredient":{"item":"minecraft:stone"},"count":%d}],
                         "results":[{"id":"minecraft:diamond","count":%d}],"totalEnergy":500}
                        """.formatted(count, count));
                var recipe = new OverloadProcessingRecipe.Serializer().fromJson(new ResourceLocation("ae2lt:large_count_regression"), json.getAsJsonObject());
                var copy = loginRoundTrip(helper, recipe);
                check(copy.itemInputs().get(0).count() == count, "datapack input count changed");
                check(copy.itemResults().get(0).getCount() == count, "datapack result count changed or became empty");
                System.out.println("OVERLOAD_RECIPE_JSON_PASS count=" + count);
            } catch (Exception | AssertionError e) {
                failures.add("count=" + count + ": " + e);
            }
        }
        check(failures.isEmpty(), String.join("\n", failures));
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_main_fixes", template = "empty")
    public static void invalidDatapackResultsAreRejected(GameTestHelper helper) {
        for (var result : List.of(
                "{\"id\":\"minecraft:diamond\",\"count\":0}",
                "{\"id\":\"minecraft:diamond\",\"count\":-1}",
                "{\"id\":\"minecraft:diamond\",\"count\":2147483648}",
                "{\"id\":\"minecraft:air\",\"count\":1}",
                "{\"id\":\"minecraft:diamond\",\"count\":4294967297}",
                "{\"id\":\"minecraft:diamond\",\"count\":1.5}")) {
            var json = JsonParser.parseString("{\"inputs\":[{\"ingredient\":{\"item\":\"minecraft:stone\"},\"count\":1}],"
                    + "\"results\":[" + result + "],\"totalEnergy\":500}");
            boolean rejected;
            try {
                new OverloadProcessingRecipe.Serializer().fromJson(new ResourceLocation("ae2lt:invalid_regression"), json.getAsJsonObject());
                rejected = false;
            } catch (RuntimeException e) {
                rejected = true;
            }
            check(rejected, "invalid result was accepted: " + result);
        }
        helper.succeed();
    }

    private static OverloadProcessingRecipe loginRoundTrip(GameTestHelper helper, OverloadProcessingRecipe recipe) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new ClientboundUpdateRecipesPacket(List.of(recipe));
            packet.write(buffer);
            var copy = new ClientboundUpdateRecipesPacket(buffer);
            check(buffer.readableBytes() == 0, "recipe packet left unread bytes");
            return (OverloadProcessingRecipe) copy.getRecipes().get(0);
        } finally {
            buffer.release();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
