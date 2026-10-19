package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class LightningCollectorJadeProvider implements IBlockComponentProvider, IServerDataProvider<net.minecraft.world.level.block.entity.BlockEntity> {
    private static final ResourceLocation UID =
            new ResourceLocation(AE2LightningTech.MODID, "lightning_collector");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, net.minecraft.server.level.ServerPlayer player, net.minecraft.world.level.Level world, net.minecraft.world.level.block.entity.BlockEntity blockEntity, boolean showDetails) {
        if (blockEntity instanceof LightningCollectorBlockEntity collector) {
            data.putInt("CooldownTicks", collector.getCooldownTicks());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getBlockState().is(ModBlocks.LIGHTNING_COLLECTOR.get())) {
            return;
        }
    }
}
