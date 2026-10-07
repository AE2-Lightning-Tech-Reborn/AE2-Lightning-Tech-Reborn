package com.moakiee.ae2lt.debug;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AECraftingPattern;
import com.moakiee.ae2lt.recipe.RainbowPigmeeColoring;
import com.moakiee.ae2lt.recipe.RainbowPigmeeColorCycleRecipe;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class RainbowPigmeeRecolorGameTests {
    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
    }

    private static ItemStack named(Item item, int count) {
        var stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Keep my data"));
        stack.set(DataComponents.REPAIR_COST, 7);
        return stack;
    }

    private static ItemStack pigmee(int count) {
        return named(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get(), count);
    }

    private static RecipeHolder<CraftingRecipe> recipe(GameTestHelper h, String path) {
        var holder = h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("ae2lt:" + path)).orElseThrow();
        return new RecipeHolder<>(holder.id(), (CraftingRecipe) holder.value());
    }

    private static CraftingInput layout(GameTestHelper h, DyeColor color, ItemStack material) {
        var dye = recipe(h, "rainbow_dye/" + color.getName()).value();
        var items = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 9; slot++) {
            items.add(slot == 4 ? pigmee(2)
                    : dye.getIngredients().get(slot).isEmpty() ? ItemStack.EMPTY : material.copy());
        }
        return CraftingInput.of(3, 3, items);
    }

    private static ItemStack cycle(GameTestHelper h, ItemStack source) {
        return recipe(h, "rainbow_pigmee_color_cycle").value().assemble(
                CraftingInput.of(2, 1, List.of(pigmee(1), source)), h.getLevel().registryAccess());
    }

    @GameTest(template = "pigmee_station_empty")
    public static void everyVanillaAndModFamilyCyclesInDyeIdOrder(GameTestHelper h) {
        var cycle = recipe(h, "rainbow_pigmee_color_cycle").value();
        for (String family : List.of("minecraft:%s_wool", "minecraft:%s_carpet", "minecraft:%s_concrete",
                "minecraft:%s_concrete_powder", "minecraft:%s_terracotta", "minecraft:%s_glazed_terracotta",
                "minecraft:%s_stained_glass", "minecraft:%s_stained_glass_pane", "minecraft:%s_shulker_box",
                "minecraft:%s_bed", "minecraft:%s_banner", "minecraft:%s_candle", "minecraft:%s_dye",
                "ae2lt:%s_pigmee_building_panel", "ae2lt:%s_pigmee_framed_building_panel",
                "ae2lt:%s_pigmee_building_slab", "ae2lt:%s_pigmee_framed_building_slab",
                "ae2lt:overloaded_cable_%s", "ae2:%s_glass_cable", "ae2:%s_smart_cable")) {
            for (var color : DyeColor.values()) {
                var source = named(item(family.formatted(color.getName())), 1);
                var original = source.copy();
                var input = CraftingInput.of(2, 2, List.of(ItemStack.EMPTY, source, pigmee(3), ItemStack.EMPTY));
                h.assertTrue(cycle.matches(input, h.getLevel()), "Cannot cycle " + family + " " + color);
                var expected = source.transmuteCopy(item(family.formatted(DyeColor.byId((color.getId() + 1) % 16).getName())), 1);
                h.assertTrue(ItemStack.matches(cycle.assemble(input, h.getLevel().registryAccess()), expected),
                        "Wrong next color or lost components: " + family + " " + color);
                h.assertTrue(ItemStack.matches(source, original), "Recipe mutated its input");
                h.assertTrue(cycle.getRemainingItems(input).stream().filter(s -> !s.isEmpty()).count() == 1,
                        "Only the catalyst may remain");
            }
        }
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void uncoloredItemsStartWhiteAndContainersKeepContents(GameTestHelper h) {
        for (String[] pair : List.of(new String[] {"minecraft:glass", "minecraft:white_stained_glass"},
                new String[] {"minecraft:glass_pane", "minecraft:white_stained_glass_pane"},
                new String[] {"minecraft:terracotta", "minecraft:white_terracotta"},
                new String[] {"minecraft:candle", "minecraft:white_candle"},
                new String[] {"minecraft:shulker_box", "minecraft:white_shulker_box"},
                new String[] {"ae2lt:overloaded_cable", "ae2lt:overloaded_cable_white"},
                new String[] {"ae2:fluix_glass_cable", "ae2:white_glass_cable"})) {
            var source = named(item(pair[0]), 1);
            h.assertTrue(ItemStack.matches(cycle(h, source), source.transmuteCopy(item(pair[1]), 1)),
                    "Uncolored item did not become white: " + pair[0]);
        }
        var box = named(Items.SHULKER_BOX, 1);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 37))));
        h.assertTrue(ItemStack.matches(cycle(h, box), box.transmuteCopy(Items.WHITE_SHULKER_BOX, 1)),
                "Shulker contents changed");
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void componentColorsCycleWithoutLosingEquipmentData(GameTestHelper h) {
        var armor = named(Items.LEATHER_CHESTPLATE, 1);
        armor.setDamageValue(31);
        h.assertTrue(RainbowPigmeeColoring.EMPTY.next(armor).get(DataComponents.DYED_COLOR).rgb()
                == (DyeColor.WHITE.getTextureDiffuseColor() & 0xFFFFFF), "Undyed armor must start white");
        var shield = named(Items.SHIELD, 1);
        shield.setDamageValue(41);
        h.assertTrue(RainbowPigmeeColoring.EMPTY.next(shield).get(DataComponents.BASE_COLOR) == DyeColor.WHITE,
                "Undyed shield must start white");
        for (var color : DyeColor.values()) {
            var next = DyeColor.byId((color.getId() + 1) % 16);
            armor.set(DataComponents.DYED_COLOR, new DyedItemColor(color.getTextureDiffuseColor() & 0xFFFFFF, false));
            var expected = armor.copy();
            expected.set(DataComponents.DYED_COLOR, new DyedItemColor(next.getTextureDiffuseColor() & 0xFFFFFF, false));
            h.assertTrue(ItemStack.matches(RainbowPigmeeColoring.EMPTY.next(armor), expected), "Armor data/color changed");
            shield.set(DataComponents.BASE_COLOR, color);
            expected = shield.copy();
            expected.set(DataComponents.BASE_COLOR, next);
            h.assertTrue(ItemStack.matches(RainbowPigmeeColoring.EMPTY.next(shield), expected), "Shield data/color changed");
        }
        // Mixed RGB colors join the cycle at the closest dye color.
        armor.set(DataComponents.DYED_COLOR, new DyedItemColor(0x1D1D22, false));
        h.assertTrue(RainbowPigmeeColoring.EMPTY.next(armor).get(DataComponents.DYED_COLOR).rgb()
                == (DyeColor.WHITE.getTextureDiffuseColor() & 0xFFFFFF), "Near-black must wrap to white");
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void allExistingLayoutsRecolorFourItemsAndSurviveNetworkCodec(GameTestHelper h) {
        for (var color : DyeColor.values()) {
            var recipe = (com.moakiee.ae2lt.recipe.RainbowPigmeeDyeRecipe) recipe(h, "rainbow_dye/" + color.getName()).value();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                ModRecipeTypes.RAINBOW_PIGMEE_DYE_SERIALIZER.get().streamCodec().encode(buffer, recipe);
                var copy = ModRecipeTypes.RAINBOW_PIGMEE_DYE_SERIALIZER.get().streamCodec().decode(buffer);
                var synced = new net.minecraft.world.item.crafting.RecipeManager(h.getLevel().registryAccess());
                var syncedRecipes = new ArrayList<RecipeHolder<?>>(h.getLevel().getRecipeManager().getRecipes());
                var id = ResourceLocation.parse("ae2lt:rainbow_dye/" + color.getName());
                syncedRecipes.removeIf(holder -> holder.id().equals(id));
                syncedRecipes.add(new RecipeHolder<>(id, copy));
                synced.replaceRecipes(syncedRecipes);
                for (var material : List.of(Items.RED_WOOL, Items.GLASS, ModItems.OVERLOADED_CABLE.get())) {
                    var input = layout(h, color, named(material, 5));
                    h.assertTrue(recipe.matches(input, h.getLevel()) && copy.matches(input, h.getLevel()),
                            "Layout missing " + color);
                    var outputItem = material == Items.RED_WOOL ? item("minecraft:" + color.getName() + "_wool")
                            : material == Items.GLASS ? item("minecraft:" + color.getName() + "_stained_glass")
                            : item("ae2lt:overloaded_cable_" + color.getName());
                    var expected = named(outputItem, 4);
                    h.assertTrue(ItemStack.matches(copy.assemble(input, h.getLevel().registryAccess()), expected),
                            "Layout output changed " + color);
                    h.assertTrue(ItemStack.matches(copy.getRemainingItems(input).get(4), pigmee(1)), "Catalyst lost data");
                }
            } finally {
                buffer.release();
            }
        }
        var cycle = recipe(h, "rainbow_pigmee_color_cycle").value();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            var serializer = ModRecipeTypes.RAINBOW_PIGMEE_COLOR_CYCLE_SERIALIZER.get();
            serializer.streamCodec().encode(buffer, (com.moakiee.ae2lt.recipe.RainbowPigmeeColorCycleRecipe) cycle);
            var copy = serializer.streamCodec().decode(buffer);
            var input = CraftingInput.of(2, 1, List.of(pigmee(1), new ItemStack(Items.BLACK_WOOL)));
            h.assertTrue(copy.matches(input, h.getLevel()) && copy.assemble(input, h.getLevel().registryAccess()).is(Items.WHITE_WOOL),
                    "Network cycle recipe changed");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void exhaustiveLayoutsAndInvalidInputsHaveNoAmbiguities(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        int[] outer = {0, 1, 2, 3, 5, 6, 7, 8};
        int accepted = 0;
        for (int mask = 0; mask < 256; mask++) {
            var items = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
            items.set(4, pigmee(1));
            for (int bit = 0; bit < 8; bit++) {
                if ((mask & (1 << bit)) != 0) items.set(outer[bit], new ItemStack(Items.GLASS));
            }
            var input = CraftingInput.of(3, 3, items);
            long matches = recipes.stream().filter(r -> r.value().matches(input, h.getLevel())).count();
            h.assertTrue(matches <= 1, "Ambiguous recolor layout " + mask);
            if (matches == 1) {
                int count = Integer.bitCount(mask);
                h.assertTrue(count == 1 || count == 4, "Wrong number of targets accepted");
                accepted++;
            }
        }
        h.assertTrue(accepted == 24, "Expected 8 single-item and 16 four-item layouts, got " + accepted);
        var cycle = recipe(h, "rainbow_pigmee_color_cycle").value();
        for (var wrong : List.of(Items.STONE, Items.DIAMOND_SWORD, ModItems.DYE_BASE.get(),
                ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get(), ModFumos.PIGMEE_FUMO_ITEM.get())) {
            var input = CraftingInput.of(2, 1, List.of(pigmee(1), new ItemStack(wrong)));
            h.assertTrue(!cycle.matches(input, h.getLevel()) && cycle.assemble(input, h.getLevel().registryAccess()).isEmpty(),
                    "Unsupported item accepted");
        }
        var dye = recipe(h, "rainbow_dye/white").value();
        for (var wrong : List.of(Items.LEATHER_BOOTS, Items.SHULKER_BOX, Items.STONE)) {
            h.assertTrue(!dye.matches(layout(h, DyeColor.WHITE, new ItemStack(wrong)), h.getLevel()),
                    "Unstackable or unsupported four-item recipe accepted");
        }
        var mixed = new ArrayList<>(layout(h, DyeColor.WHITE, named(Items.GLASS, 1)).items());
        mixed.set(0, new ItemStack(Items.GLASS));
        h.assertTrue(!dye.matches(CraftingInput.of(3, 3, mixed), h.getLevel()), "Different components merged");
        mixed.set(0, named(Items.WHITE_WOOL, 1));
        h.assertTrue(!dye.matches(CraftingInput.of(3, 3, mixed), h.getLevel()), "Different items merged");
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void nativeCraftingConsumesExactCountsAndReturnsNamedPigmee(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "RecolorQA"));
        var pos = new BlockPos(3, 2, 3);
        h.setBlock(pos, Blocks.CRAFTING_TABLE);
        var menu = new CraftingMenu(41, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(pos)));
        player.containerMenu = menu;
        var input = layout(h, DyeColor.BLUE, named(Items.GLASS, 3));
        for (int slot = 0; slot < 9; slot++) menu.getSlot(slot + 1).set(input.getItem(slot).copy());
        menu.clicked(0, 0, ClickType.PICKUP, player);
        h.assertTrue(ItemStack.matches(menu.getCarried(), named(Items.BLUE_STAINED_GLASS, 4)), "Manual output incorrect");
        player.getInventory().add(menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        h.assertTrue(player.getInventory().items.stream().filter(s -> s.is(Items.BLUE_STAINED_GLASS)).mapToInt(ItemStack::getCount).sum() == 12,
                "Shift crafting must consume 12 glass and output 12 colored glass");
        h.assertTrue(ItemStack.matches(menu.getSlot(5).getItem(), pigmee(2)), "Catalyst count/data changed");
        h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Output remains after input consumed");

        player.getInventory().clearContent();
        player.containerMenu = player.inventoryMenu;
        player.inventoryMenu.getSlot(1).set(pigmee(2));
        player.inventoryMenu.getSlot(4).set(named(Items.BLACK_WOOL, 3));
        player.inventoryMenu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        h.assertTrue(player.getInventory().items.stream().filter(s -> s.is(Items.WHITE_WOOL)).mapToInt(ItemStack::getCount).sum() == 3,
                "2x2 crafting did not cycle three wool");
        h.assertTrue(ItemStack.matches(player.inventoryMenu.getSlot(1).getItem(), pigmee(2)), "2x2 catalyst changed");
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void ae2PatternsAssembleActualColoredItemsAndReturnPigmee(GameTestHelper h) {
        for (var color : DyeColor.values()) {
            verifyPattern(h, recipe(h, "rainbow_dye/" + color.getName()), layout(h, color, named(Items.GLASS, 1)));
        }
        verifyPattern(h, recipe(h, "rainbow_pigmee_color_cycle"), CraftingInput.of(3, 3,
                List.of(pigmee(1), named(Items.BLACK_WOOL, 1), ItemStack.EMPTY,
                        ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)));
        h.succeed();
    }

    private static void verifyPattern(GameTestHelper h, RecipeHolder<CraftingRecipe> holder, CraftingInput input) {
        var expected = holder.value().assemble(input, h.getLevel().registryAccess());
        var grid = new ItemStack[9];
        java.util.Arrays.fill(grid, ItemStack.EMPTY);
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) grid[y * 3 + x] = input.getItem(x, y).copyWithCount(1);
        }
        for (boolean substitutions : new boolean[] {false, true}) {
            var encoded = PatternDetailsHelper.encodeCraftingPattern(holder, grid, expected, substitutions, false);
            var decoded = PatternDetailsHelper.decodePattern(encoded, h.getLevel());
            h.assertTrue(decoded instanceof AECraftingPattern, "AE2 cannot decode recolor " + holder.id());
            var pattern = (AECraftingPattern) decoded;
            for (int slot = 0; slot < 9; slot++) {
                if (!grid[slot].isEmpty()) {
                    h.assertTrue(pattern.isItemValid(slot, AEItemKey.of(grid[slot]), h.getLevel()),
                            "AE2 rejected encoded input with substitutions=" + substitutions);
                }
            }
            h.assertTrue(ItemStack.matches(pattern.assemble(input, h.getLevel()), expected), "AE2 recolor output changed");
            var remainders = pattern.getRemainingItems(input).stream().filter(s -> !s.isEmpty()).toList();
            h.assertTrue(remainders.size() == 1 && ItemStack.matches(remainders.getFirst(), pigmee(1)),
                    "AE2 did not return the catalyst correctly");
        }
    }

    private static RecipeHolder<CraftingRecipe> fixture(String id, Item output, int count, Item... inputs) {
        var ingredients = NonNullList.<Ingredient>create();
        for (var item : inputs) ingredients.add(Ingredient.of(item));
        return new RecipeHolder<>(ResourceLocation.parse("ae2lt:recolor_test/" + id),
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(output, count), ingredients));
    }

    @GameTest(template = "pigmee_station_empty")
    public static void uncoloredInferenceUsesEqualQuantityRecipesAndReloads(GameTestHelper h) {
        var manager = new RecipeManager(h.getLevel().registryAccess());
        var cycle = new RainbowPigmeeColorCycleRecipe(CraftingBookCategory.MISC);
        var holder = new RecipeHolder<CraftingRecipe>(ResourceLocation.parse("ae2lt:rainbow_pigmee_color_cycle"), cycle);
        var input = CraftingInput.of(2, 1, List.of(pigmee(1), named(Items.STONE, 3)));
        var valid = fixture("valid", Items.RED_WOOL, 2, Items.STONE, Items.STONE, Items.RED_DYE);
        manager.replaceRecipes(List.of(holder, valid));
        h.assertTrue(ItemStack.matches(cycle.assemble(input, h.getLevel().registryAccess()), named(Items.WHITE_WOOL, 1)),
                "Actual 2 stone + red dye -> 2 red wool recipe must infer the base, regardless of its name");

        for (var invalid : List.of(
                fixture("unequal", Items.RED_WOOL, 4, Items.STONE, Items.STONE, Items.RED_DYE),
                fixture("mixed", Items.RED_WOOL, 2, Items.STONE, Items.COBBLESTONE, Items.RED_DYE),
                fixture("wrong_dye", Items.RED_WOOL, 2, Items.STONE, Items.STONE, Items.BLUE_DYE),
                fixture("extra_dye", Items.RED_WOOL, 2, Items.STONE, Items.STONE, Items.RED_DYE, Items.RED_DYE),
                fixture("not_colored", Items.DIAMOND, 2, Items.STONE, Items.STONE, Items.RED_DYE))) {
            manager.replaceRecipes(List.of(holder, invalid));
            h.assertTrue(!cycle.matches(input, h.getLevel()) && cycle.assemble(input, h.getLevel().registryAccess()).isEmpty(),
                    "Invalid dye recipe inferred a base: " + invalid.id());
        }
        manager.replaceRecipes(List.of(holder, valid,
                fixture("ambiguous", Items.BLUE_CONCRETE, 1, Items.STONE, Items.BLUE_DYE)));
        h.assertTrue(!cycle.matches(input, h.getLevel()), "Ambiguous source chose an arbitrary family");

        manager.replaceRecipes(List.of(holder, valid));
        h.assertTrue(cycle.matches(input, h.getLevel()), "Adding the recipe back did not rebuild inference");
        manager.replaceRecipes(List.of(holder));
        h.assertTrue(!cycle.matches(input, h.getLevel()), "Removed dye recipe left stale base inference");
        h.assertTrue(!cycle.matches(CraftingInput.of(2, 1, List.of(pigmee(1), new ItemStack(Items.TERRACOTTA))), h.getLevel()),
                "An uncolored registry name alone must not count as a dye recipe");
        h.assertTrue(cycle.matches(CraftingInput.of(2, 1, List.of(pigmee(1), new ItemStack(Items.BLACK_WOOL))), h.getLevel()),
                "Colored variants must still cycle independently of base recipes");
        h.succeed();
    }
}
