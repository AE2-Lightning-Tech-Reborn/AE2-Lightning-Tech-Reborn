package com.moakiee.ae2lt.machine.largeoverload;

import java.util.EnumMap;
import java.util.Map;
import appeng.api.networking.GridServices;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** First-generation factory registrations stay entirely in LT. */
public final class LargeFactoryRegistration {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("ae2lt");
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2lt");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "ae2lt");
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "ae2lt");
    public static final Map<LargeFactoryComponent, DeferredBlock<? extends Block>> PARTS = new EnumMap<>(LargeFactoryComponent.class);
    public static final Map<LargeFactoryRecipeAccess.Process, DeferredItem<Item>> PROCESS_CORES = new EnumMap<>(LargeFactoryRecipeAccess.Process.class);

    static {
        for (var component : LargeFactoryComponent.values()) {
            if (component == LargeFactoryComponent.AIR || component == LargeFactoryComponent.OTHER
                    || component == LargeFactoryComponent.FIRMAMENT_CORE) continue;
            String name = "large_overload_" + component.name().toLowerCase(java.util.Locale.ROOT);
            var block = BLOCKS.register(name, () -> {
                var properties = BlockBehaviour.Properties.of().strength(8, 30).sound(SoundType.METAL).requiresCorrectToolForDrops();
                if (component == LargeFactoryComponent.CONTROLLER) return new LargeFactoryControllerBlock(properties);
                if (component.isPatternHatch() || component == LargeFactoryComponent.CRYSTAL_HATCH) return new LargeFactoryHatchBlock(properties, component);
                if (component.isHatch()) return new LargeFactoryAuxBlock(properties, component);
                return new LargeFactoryPartBlock(properties, component);
            });
            PARTS.put(component, block);
            ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        }
        for (var process : LargeFactoryRecipeAccess.Process.values()) if (!process.base() && process != LargeFactoryRecipeAccess.Process.FIRMAMENT) {
            PROCESS_CORES.put(process, ITEMS.register("large_factory_" + process.name().toLowerCase(java.util.Locale.ROOT) + "_process_core",
                    () -> new Item(new Item.Properties().stacksTo(1))));
        }
    }
    public static final DeferredItem<LargeFactoryRecoveryItem> RECOVERY = ITEMS.register("large_factory_recovery_capsule",
            () -> new LargeFactoryRecoveryItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeFactoryControllerBlockEntity>> CONTROLLER = ENTITIES.register(
            "large_overload_controller", () -> BlockEntityType.Builder.of(LargeFactoryControllerBlockEntity::new,
                    block(LargeFactoryComponent.CONTROLLER)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeFactoryHatchBlockEntity>> HATCH = ENTITIES.register(
            "large_overload_processing_hatch", () -> BlockEntityType.Builder.of(LargeFactoryHatchBlockEntity::new,
                    block(LargeFactoryComponent.PATTERN_HATCH), block(LargeFactoryComponent.EXPANDED_PATTERN_HATCH),
                    block(LargeFactoryComponent.CRYSTAL_HATCH)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeFactoryAuxBlockEntity>> AUX = ENTITIES.register(
            "large_overload_aux_hatch", () -> BlockEntityType.Builder.of(LargeFactoryAuxBlockEntity::new,
                    block(LargeFactoryComponent.PROCESS_CORE_HATCH), block(LargeFactoryComponent.ENERGY_HATCH)).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<LargeFactoryMenu>> MENU = MENUS.register("large_overload_factory", () -> LargeFactoryMenu.TYPE);

    private LargeFactoryRegistration() { }
    public static Block block(LargeFactoryComponent part) { return PARTS.get(part).get(); }
    public static LargeFactoryComponent component(net.minecraft.world.level.block.state.BlockState state) {
        if (state.isAir()) return LargeFactoryComponent.AIR;
        if (state.getBlock() instanceof Part part) return part.component();
        if (state.is(com.moakiee.ae2lt.registry.ModBlocks.FIRMAMENT_CONVERSION_CORE.get())) return LargeFactoryComponent.FIRMAMENT_CORE;
        return LargeFactoryComponent.OTHER;
    }
    public interface Part { LargeFactoryComponent component(); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus);
        bus.addListener(LargeFactoryRegistration::capabilities);
        bus.addListener(LargeFactoryRegistration::setup);
    }
    @SuppressWarnings("unchecked")
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GridServices.register(LargeFactoryNetworkIdentity.class, LargeFactoryNetworkIdentity.class);
            for (var part : new LargeFactoryComponent[]{LargeFactoryComponent.PATTERN_HATCH, LargeFactoryComponent.EXPANDED_PATTERN_HATCH, LargeFactoryComponent.CRYSTAL_HATCH}) {
                var block = (AEBaseEntityBlock<LargeFactoryHatchBlockEntity>) block(part);
                block.setBlockEntity(LargeFactoryHatchBlockEntity.class, HATCH.get(), null, LargeFactoryHatchBlockEntity::serverTick);
            }
            AEBaseBlockEntity.registerBlockEntityItem(HATCH.get(), block(LargeFactoryComponent.PATTERN_HATCH).asItem());
        });
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST, HATCH.get(), (be, context) -> be);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, HATCH.get(), (be, side) -> be.inventory().toItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AUX.get(), (be, side) -> be.inventory());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AUX.get(), (be, side) -> be.energy());
    }
}
