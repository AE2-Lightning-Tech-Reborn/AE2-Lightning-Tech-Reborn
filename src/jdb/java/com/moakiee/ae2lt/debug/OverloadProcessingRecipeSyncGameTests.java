package com.moakiee.ae2lt.debug;

import com.google.gson.JsonParser;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingIngredient;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the same recipe packet codec used when a remote player joins. */
@GameTestHolder("ae2lt_recipe_sync")
@PrefixGameTestTemplate(false)
public final class OverloadProcessingRecipeSyncGameTests {
    private static final int[] COUNTS = {1, 64, 65, 99, 100, 127, 128, 255, 256, 16384};

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void programmaticRecipesSurviveLoginSync(GameTestHelper helper) {
        var failures = new ArrayList<String>();
        for (int count : COUNTS) {
            for (boolean largeOutput : List.of(false, true)) {
                int inputCount = largeOutput ? 1 : count;
                int outputCount = largeOutput ? count : 1;
                try {
                    var output = new ItemStack(Items.DIAMOND, outputCount);
                    output.set(DataComponents.CUSTOM_NAME, Component.literal("Bulk result"));
                    var recipe = new OverloadProcessingRecipe(3,
                            List.of(new OverloadProcessingIngredient(Ingredient.of(Items.STONE), inputCount)),
                            FluidStack.EMPTY, List.of(output), FluidStack.EMPTY,
                            500, 4, LightningKey.Tier.HIGH_VOLTAGE);
                    var copy = loginRoundTrip(helper, recipe);
                    check(copy.itemInputs().getFirst().count() == inputCount, "input count changed");
                    check(copy.itemResults().getFirst().getCount() == outputCount, "output count changed or became empty");
                    check(ItemStack.isSameItemSameComponents(output, copy.itemResults().getFirst()), "result components changed");
                    System.out.println("OVERLOAD_RECIPE_SYNC_PASS input=" + inputCount + " output=" + outputCount);
                } catch (Exception | AssertionError e) {
                    failures.add("input=" + inputCount + " output=" + outputCount + ": " + e);
                }
            }
        }
        check(failures.isEmpty(), String.join("\n", failures));
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void datapackRecipesKeepLargeResults(GameTestHelper helper) {
        var failures = new ArrayList<String>();
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int count : COUNTS) {
            try {
                var json = JsonParser.parseString("""
                        {"inputs":[{"ingredient":{"item":"minecraft:stone"},"count":%d}],
                         "results":[{"id":"minecraft:diamond","count":%d}],"totalEnergy":500}
                        """.formatted(count, count));
                var recipe = new OverloadProcessingRecipe.Serializer().codec().codec().parse(ops, json).getOrThrow();
                var copy = loginRoundTrip(helper, recipe);
                check(copy.itemInputs().getFirst().count() == count, "datapack input count changed");
                check(copy.itemResults().getFirst().getCount() == count, "datapack result count changed or became empty");
                var saved = new OverloadProcessingRecipe.Serializer().codec().codec().encodeStart(ops, copy).getOrThrow();
                var reloaded = new OverloadProcessingRecipe.Serializer().codec().codec().parse(ops, saved).getOrThrow();
                check(reloaded.itemResults().getFirst().getCount() == count, "JSON round trip changed count");
                System.out.println("OVERLOAD_RECIPE_JSON_PASS count=" + count);
            } catch (Exception | AssertionError e) {
                failures.add("count=" + count + ": " + e);
            }
        }
        check(failures.isEmpty(), String.join("\n", failures));
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void invalidDatapackResultsAreRejected(GameTestHelper helper) {
        var codec = new OverloadProcessingRecipe.Serializer().codec().codec();
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var result : List.of(
                "{\"id\":\"minecraft:diamond\",\"count\":0}",
                "{\"id\":\"minecraft:diamond\",\"count\":-1}",
                "{\"id\":\"minecraft:diamond\",\"count\":2147483648}",
                "{\"id\":\"minecraft:air\",\"count\":1}",
                "{\"id\":\"minecraft:diamond\",\"count\":2,\"components\":{\"minecraft:max_stack_size\":64,\"minecraft:max_damage\":10}}")) {
            var json = JsonParser.parseString("{\"inputs\":[{\"ingredient\":{\"item\":\"minecraft:stone\"},\"count\":1}],"
                    + "\"results\":[" + result + "],\"totalEnergy\":500}");
            boolean rejected;
            try {
                rejected = codec.parse(ops, json).error().isPresent();
            } catch (IllegalArgumentException e) {
                rejected = true;
            }
            check(rejected, "invalid result was accepted: " + result);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "pigmee_station_empty")
    public static void kubeJsRecipesSurviveLoginSync(GameTestHelper helper) {
        // The fixture is installed by the dedicated regression run, never in production.
        if (!Boolean.getBoolean("ae2lt.recipeSyncKubeJsFixture")) {
            helper.succeed();
            return;
        }
        for (int count : COUNTS) {
            var id = ResourceLocation.fromNamespaceAndPath("ae2lt", "sync_probe_" + count);
            var holder = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
            var recipe = (OverloadProcessingRecipe) holder.value();
            var copy = loginRoundTrip(helper, recipe);
            check(copy.itemInputs().getFirst().count() == count, "KJS input count changed: " + count);
            check(copy.itemResults().getFirst().getCount() == count, "KJS result changed or became empty: " + count);
            System.out.println("OVERLOAD_RECIPE_KJS_PASS count=" + count);
        }
        helper.succeed();
    }

    private static OverloadProcessingRecipe loginRoundTrip(GameTestHelper helper, OverloadProcessingRecipe recipe) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var packet = new ClientboundUpdateRecipesPacket(List.of(new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath("ae2lt", "large_count_regression"), recipe)));
            ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(buffer, packet);
            var copy = ClientboundUpdateRecipesPacket.STREAM_CODEC.decode(buffer);
            check(buffer.readableBytes() == 0, "recipe packet left unread bytes");
            return (OverloadProcessingRecipe) copy.getRecipes().getFirst().value();
        } finally {
            buffer.release();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
