package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.grid.FrequencyDisplayName;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyAdmissions;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyEndpoints;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** FE bindings are independent of ME virtual entrances and do not probe capabilities for tooltips. */
public final class WirelessEnergyJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final ResourceLocation UID = new ResourceLocation(AE2LightningTech.MODID, "wireless_energy");
    private static final String TAG_LINK = "AE2LTWirelessEnergy";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) return;
        var viewer = accessor.getPlayer().getUUID();
        var endpoint = WirelessEnergyEndpoints.get(level.getServer()).inspectTarget(level, accessor.getPosition(), viewer);
        int frequencyId;
        String state;
        if (endpoint != null) {
            frequencyId = endpoint.frequency();
            state = "connected";
        } else {
            var admission = WirelessEnergyAdmissions.get(level.getServer()).inspect(level, accessor.getPosition(), viewer);
            if (admission == null) return;
            frequencyId = admission.frequency();
            state = switch (admission.state()) {
                case QUEUED, WAITING -> "pending";
                default -> "inactive";
            };
        }
        var manager = WirelessFrequencyManager.get(level.getServer());
        var frequency = manager == null ? null : manager.getFrequency(frequencyId);
        var link = new CompoundTag();
        link.putString("FrequencyName", FrequencyDisplayName.of(frequencyId, frequency == null ? "" : frequency.getName()));
        link.putString("State", state);
        data.put(TAG_LINK, link);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains(TAG_LINK, Tag.TAG_COMPOUND)) return;
        var link = data.getCompound(TAG_LINK);
        String state = link.getString("State");
        ChatFormatting color = switch (state) {
            case "connected" -> ChatFormatting.GREEN;
            case "pending" -> ChatFormatting.YELLOW;
            case "inactive" -> ChatFormatting.GRAY;
            default -> null;
        };
        if (color == null) return;
        var status = Component.translatable("jade.ae2lt.frequency_card_wireless_node.state." + state).withStyle(color);
        tooltip.add(Component.translatable("jade.ae2lt.wireless_energy", link.getString("FrequencyName"), status)
                .withStyle(ChatFormatting.GOLD));
    }
}
