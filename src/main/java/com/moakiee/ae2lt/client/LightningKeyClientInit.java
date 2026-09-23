package com.moakiee.ae2lt.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import appeng.client.api.AEKeyRendering;
import appeng.api.util.AEColor;
import appeng.items.storage.BasicStorageCell;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.ElectroChimeCrystalItem;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.client.railgun.RailgunClientBootstrap;
import com.moakiee.ae2lt.client.railgun.RailgunClientExtensions;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import com.moakiee.ae2lt.registry.ModItems;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class LightningKeyClientInit {
    private LightningKeyClientInit() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModItems.registerStorageCellModels();
            RailgunClientBootstrap.install();
            ShieldHitFeedbackClientBootstrap.install();
            if (ModList.get().isLoaded("curios")) {
                PigmeeCuriosClientBridge.registerRenderers();
            }
            AEKeyRendering.register(LightningKeyType.INSTANCE, LightningKey.class, LightningKeyRenderHandler.INSTANCE);

        });
    }

    private static final class CellTypeModelProperty implements RangeSelectItemModelProperty {
        private static final CellTypeModelProperty INSTANCE = new CellTypeModelProperty();
        private static final MapCodec<CellTypeModelProperty> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            return FixedInfiniteCellItem.hasType(stack) ? FixedInfiniteCellItem.getType(stack) : 0.0F;
        }

        @Override
        public MapCodec<CellTypeModelProperty> type() {
            return CODEC;
        }
    }

    @SubscribeEvent
    public static void registerItemModelProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "cell_type"),
                CellTypeModelProperty.CODEC);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(RailgunClientExtensions.INSTANCE, ModItems.ELECTROMAGNETIC_RAILGUN.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "legacy_tint"),
                LegacyItemTintSource.CODEC);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.ARMOR_LEVEL,
                Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "celestweave_energy_level"),
                CelestweaveArmorEnergyLevel.INSTANCE);
    }

}
