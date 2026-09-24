package com.moakiee.ae2lt.integration.jei;

import java.util.Collection;
import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.CrystalCatalyzerScreen;
import com.moakiee.ae2lt.client.LightningAssemblyChamberScreen;
import com.moakiee.ae2lt.client.LightningSimulationChamberScreen;
import com.moakiee.ae2lt.client.OverloadProcessingFactoryScreen;
import com.moakiee.ae2lt.client.TeslaCoilScreen;
import com.moakiee.ae2lt.integration.jei.category.CrystalCatalyzerCategory;
import com.moakiee.ae2lt.integration.jei.category.FirmamentConversionCategory;
import com.moakiee.ae2lt.integration.jei.category.LightningAssemblyCategory;
import com.moakiee.ae2lt.integration.jei.category.LightningSimulationCategory;
import com.moakiee.ae2lt.integration.jei.category.LightningStrikeCategory;
import com.moakiee.ae2lt.integration.jei.category.LightningTransformCategory;
import com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory;
import com.moakiee.ae2lt.integration.jei.category.OverloadGrowthCategory;
import com.moakiee.ae2lt.integration.jei.category.OverloadProcessingCategory;
import com.moakiee.ae2lt.integration.jei.category.TeslaCoilCategory;
import com.moakiee.ae2lt.integration.jei.compat.ae2jeiintegration.AE2JeiIntegrationCompat;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipes;
import com.moakiee.ae2lt.integration.recipeviewer.JeiRecipeSyncClient;
import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.IRecipeManager;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import appeng.client.integrations.jei.transfer.UseCraftingRecipeTransfer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.fml.ModList;
import appeng.client.integrations.jei.transfer.EncodePatternTransferHandler;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "jei_plugin");
    private static final String EMI_MODID = "emi";
    private static JEIPlugin activePlugin;
    private IJeiRuntime runtime;
    private RecipeMap lastMap;
    private SyncedRecipes published;

    public JEIPlugin() {
        AE2JeiIntegrationCompat.registerConverter();
    }

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        registration.register(
                LightningJeiIngredients.TYPE,
                LightningJeiIngredients.INGREDIENTS,
                LightningJeiIngredients.HELPER,
                LightningJeiIngredients.RENDERER,
                LightningJeiIngredients.CODEC);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new OverloadGrowthCategory(guiHelper),
                new LightningAssemblyCategory(guiHelper),
                new LightningSimulationCategory(guiHelper),
                new LightningTransformCategory(guiHelper),
                new LightningStrikeCategory(guiHelper),
                new OverloadProcessingCategory(guiHelper),
                new TeslaCoilCategory(guiHelper),
                new CrystalCatalyzerCategory(guiHelper),
                new FirmamentConversionCategory(guiHelper));
        // EMI has a native adapter for this highly interactive page. Avoid also feeding
        // the JEI version through EMI's JEI bridge when both viewers are installed.
        if (!isEmiLoaded()) {
            registration.addRecipeCategories(new MultiblockStructureCategory(guiHelper));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(OverloadGrowthCategory.TYPE, List.of(OverloadGrowthCategory.Page.values()));
        registration.addRecipes(TeslaCoilCategory.TYPE, List.of(TeslaCoilCategory.Page.values()));
        if (!isEmiLoaded()) {
            registration.addRecipes(MultiblockStructureCategory.TYPE, MultiblockStructureRecipes.all());
        }
        registration.addIngredientInfo(
                ModItems.PIGMEE_CORE.get(),
                Component.translatable("jei.ae2lt.pigmee_core.info"));

        RecipeMap map = JeiRecipeSyncClient.recipes();
        lastMap = map;
        published = SyncedRecipes.from(map);
        published.register(registration);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        this.runtime = runtime;
        activePlugin = this;
        publishIfChanged();
    }

    @Override
    public void onRuntimeUnavailable() {
        if (activePlugin == this) activePlugin = null;
        runtime = null;
        published = null;
        lastMap = null;
    }

    public static void onRecipesChanged() {
        if (activePlugin != null) activePlugin.publishIfChanged();
    }

    private void publishIfChanged() {
        if (runtime == null) return;
        RecipeMap map = JeiRecipeSyncClient.recipes();
        if (map == lastMap) return;
        SyncedRecipes next = SyncedRecipes.from(map);
        next.replace(runtime.getRecipeManager(), published);
        published = next;
        lastMap = map;
    }

    private static <I extends RecipeInput, T extends Recipe<I>> List<T> byType(
            RecipeMap map, net.minecraft.world.item.crafting.RecipeType<T> type) {
        return map.byType(type).stream().map(RecipeHolder::value).toList();
    }

    private record SyncedRecipes(
            List<CrystalCatalyzerRecipe> crystal,
            List<LightningAssemblyRecipe> assembly,
            List<LightningSimulationRecipe> simulation,
            List<LightningTransformRecipe> transform,
            List<LightningStrikeRecipe> strike,
            List<OverloadProcessingRecipe> overload,
            List<FirmamentConversionRecipe> firmament) {
        static SyncedRecipes from(RecipeMap map) {
            return new SyncedRecipes(
                    byType(map, ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get()).stream()
                            .filter(recipe -> !recipe.getOutputTemplate().isEmpty()).toList(),
                    byType(map, ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get()),
                    byType(map, ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get()),
                    byType(map, ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get()),
                    byType(map, ModRecipeTypes.LIGHTNING_STRIKE_TYPE.get()),
                    byType(map, ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get()),
                    byType(map, ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get()));
        }

        void register(IRecipeRegistration registration) {
            registration.addRecipes(CrystalCatalyzerCategory.TYPE, crystal);
            registration.addRecipes(LightningAssemblyCategory.TYPE, assembly);
            registration.addRecipes(LightningSimulationCategory.TYPE, simulation);
            registration.addRecipes(LightningTransformCategory.TYPE, transform);
            registration.addRecipes(LightningStrikeCategory.TYPE, strike);
            registration.addRecipes(OverloadProcessingCategory.TYPE, overload);
            registration.addRecipes(FirmamentConversionCategory.TYPE, firmament);
        }

        void replace(IRecipeManager manager, SyncedRecipes old) {
            if (old != null) {
                manager.hideRecipes(CrystalCatalyzerCategory.TYPE, old.crystal);
                manager.hideRecipes(LightningAssemblyCategory.TYPE, old.assembly);
                manager.hideRecipes(LightningSimulationCategory.TYPE, old.simulation);
                manager.hideRecipes(LightningTransformCategory.TYPE, old.transform);
                manager.hideRecipes(LightningStrikeCategory.TYPE, old.strike);
                manager.hideRecipes(OverloadProcessingCategory.TYPE, old.overload);
                manager.hideRecipes(FirmamentConversionCategory.TYPE, old.firmament);
            }
            manager.addRecipes(CrystalCatalyzerCategory.TYPE, crystal);
            manager.addRecipes(LightningAssemblyCategory.TYPE, assembly);
            manager.addRecipes(LightningSimulationCategory.TYPE, simulation);
            manager.addRecipes(LightningTransformCategory.TYPE, transform);
            manager.addRecipes(LightningStrikeCategory.TYPE, strike);
            manager.addRecipes(OverloadProcessingCategory.TYPE, overload);
            manager.addRecipes(FirmamentConversionCategory.TYPE, firmament);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.toStack(), LightningAssemblyCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.LIGHTNING_SIMULATION_CHAMBER.toStack(), LightningSimulationCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.OVERLOAD_PROCESSING_FACTORY.toStack(), OverloadProcessingCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.TESLA_COIL.toStack(), TeslaCoilCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.CRYSTAL_CATALYZER.toStack(), CrystalCatalyzerCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.PIGMEE_CRYSTAL_CATALYZER.toStack(), CrystalCatalyzerCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.FIRMAMENT_CONVERSION_CORE.toStack(), FirmamentConversionCategory.TYPE);
        if (!isEmiLoaded()) {
            registration.addRecipeCatalyst(
                    ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.toStack(),
                    MultiblockStructureCategory.TYPE);
            registration.addRecipeCatalyst(
                    ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.toStack(),
                    MultiblockStructureCategory.TYPE);
        }
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new TianshuCraftingTransferHandler<>(
                com.moakiee.ae2lt.menu.TianshuCraftingTermMenu.class, com.moakiee.ae2lt.menu.TianshuCraftingTermMenu.TYPE,
                registration.getTransferHelper()));
        registration.addUniversalRecipeTransferHandler(new TianshuCraftingTransferHandler<>(
                com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu.class, com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu.TYPE,
                registration.getTransferHelper()));
        if (ModList.get().isLoaded("ae2wtlib")) {
            // JEI matches the concrete menu class, not its superclass. The enhanced host shares
            // the base wireless MenuType; its exact class is sufficient for this registration.
            registration.addUniversalRecipeTransferHandler(new TianshuCraftingTransferHandler<>(
                    com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu.class,
                    null, registration.getTransferHelper()));
        }
        var helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(new UseCraftingRecipeTransfer<>(
                PigmeeSynthesisStationMenu.class, PigmeeSynthesisStationMenu.TYPE, helper),
                mezz.jei.api.constants.RecipeTypes.CRAFTING);
        registration.addUniversalRecipeTransferHandler(new EncodePatternTransferHandler<>(
                TianshuPatternEncodingTermMenu.TYPE,
                TianshuPatternEncodingTermMenu.class,
                helper));
        registration.addUniversalRecipeTransferHandler(new EncodePatternTransferHandler<>(
                TianshuWirelessPatternEncodingTermMenu.TYPE,
                TianshuWirelessPatternEncodingTermMenu.class,
                helper));

    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(com.moakiee.ae2lt.client.TianshuCraftingTermScreen.class,
                new TianshuCraftingGhostHandler());
        registration.addGuiContainerHandler(LightningAssemblyChamberScreen.class,
                clickableAreaHandler(83, 22, 42, 46, LightningAssemblyCategory.TYPE));
        registration.addGuiContainerHandler(LightningSimulationChamberScreen.class,
                clickableAreaHandler(82, 25, 35, 46, LightningSimulationCategory.TYPE));
        registration.addGuiContainerHandler(OverloadProcessingFactoryScreen.class,
                clickableAreaHandler(84, 46, 31, 10, OverloadProcessingCategory.TYPE));
        registration.addGuiContainerHandler(TeslaCoilScreen.class,
                clickableAreaHandler(43, 22, 36, 40, TeslaCoilCategory.TYPE));
        registration.addGuiContainerHandler(CrystalCatalyzerScreen.class,
                clickableAreaHandler(74, 33, 35, 10, CrystalCatalyzerCategory.TYPE));
    }

    private static <T extends AbstractContainerScreen<?>> IGuiContainerHandler<T> clickableAreaHandler(
            int x, int y, int width, int height, RecipeType<?> recipeType) {
        return new IGuiContainerHandler<T>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(T screen, double mouseX, double mouseY) {
                return List.of(IGuiClickableArea.createBasic(x, y, width, height, recipeType));
            }
        };
    }

    private static boolean isEmiLoaded() {
        return ModList.get().isLoaded(EMI_MODID);
    }
}
