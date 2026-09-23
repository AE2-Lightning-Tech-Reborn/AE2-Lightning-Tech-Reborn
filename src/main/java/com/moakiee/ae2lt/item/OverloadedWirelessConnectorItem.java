package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.block.OverloadedInterfaceBlock;
import com.moakiee.ae2lt.block.OverloadedPowerSupplyBlock;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.network.WirelessConnectorUsePacket;
import com.moakiee.ae2lt.api.patternprovider.WirelessPatternProviderHost;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

/**
 * A tool item for establishing and managing wireless connections between an
 * Overloaded Pattern Provider / Overloaded ME Interface and remote machines.
 */
public class OverloadedWirelessConnectorItem extends Item {

    private static final String TAG_SELECTED = "SelectedProvider";
    private static final String TAG_DIM = "Dim";
    private static final String TAG_POS = "Pos";
    private static final String TAG_HOST_TYPE = "HostType";

    public static final String HOST_PROVIDER = "provider";
    public static final String HOST_INTERFACE = "interface";
    public static final String HOST_POWER_SUPPLY = "power_supply";

    public OverloadedWirelessConnectorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return handleBlockUse(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return handleBlockUse(context);
    }

    private InteractionResult handleBlockUse(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var targetBe = level.getBlockEntity(pos);
        boolean isHost = targetBe instanceof WirelessPatternProviderHost
                || state.getBlock() instanceof OverloadedInterfaceBlock
                || state.getBlock() instanceof OverloadedPowerSupplyBlock;
        boolean isMachine = targetBe != null;

        if (!isHost && !isMachine) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new WirelessConnectorUsePacket(
                    context.getHand(),
                    pos,
                    context.getClickedFace(),
                    net.minecraft.client.Minecraft.getInstance().hasControlDown()));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }

        if (hasSelection(stack)) {
            var hostType = getSelectedHostType(stack);
            clearSelection(stack);
            com.moakiee.ae2lt.recipe.compat.LegacyPlayerMessages.display(player,
                    Component.translatable(getDeselectedTranslationKey(hostType)).withStyle(ChatFormatting.GREEN),
                    true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // ── Selection management ─────────────────────────────────────────────

    public static void selectHost(ItemStack stack, Level level, BlockPos pos, String hostType) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            var sel = new CompoundTag();
            sel.putString(TAG_DIM, level.dimension().identifier().toString());
            sel.putLong(TAG_POS, pos.asLong());
            sel.putString(TAG_HOST_TYPE, hostType);
            tag.put(TAG_SELECTED, sel);
        });
    }

    public static boolean hasSelection(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return com.moakiee.ae2lt.recipe.compat.LegacyNbtTypes.contains(tag, TAG_SELECTED, CompoundTag.TAG_COMPOUND);
    }

    @Nullable
    public static String getSelectedHostType(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_SELECTED)) return null;
        var sel = tag.getCompoundOrEmpty(TAG_SELECTED);
        return sel.contains(TAG_HOST_TYPE) ? sel.getStringOr(TAG_HOST_TYPE, "") : HOST_PROVIDER;
    }

    public static boolean isSelectionInCurrentDimension(Level level, ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_SELECTED)) return true;
        var sel = tag.getCompoundOrEmpty(TAG_SELECTED);
        if (!sel.contains(TAG_DIM)) return true;
        return level.dimension().identifier().equals(Identifier.parse(sel.getStringOr(TAG_DIM, "")));
    }

    public static void clearSelection(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(TAG_SELECTED);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static String getDeselectedTranslationKey(@Nullable String hostType) {
        if (HOST_INTERFACE.equals(hostType)) {
            return "ae2lt.connector.deselected_interface";
        }
        if (HOST_POWER_SUPPLY.equals(hostType)) {
            return "ae2lt.connector.deselected_power_supply";
        }
        return "ae2lt.connector.deselected";
    }

    @Nullable
    private static BlockEntity resolveSelectedHost(Level level, ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_SELECTED)) return null;

        var sel = tag.getCompoundOrEmpty(TAG_SELECTED);
        var dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(sel.getStringOr(TAG_DIM, "")));
        var pos = BlockPos.of(sel.getLongOr(TAG_POS, 0L));

        if (!level.dimension().equals(dimKey) || !level.isLoaded(pos)) return null;

        return level.getBlockEntity(pos);
    }

    @Nullable
    public static WirelessPatternProviderHost getSelectedProvider(Level level, ItemStack stack) {
        var be = resolveSelectedHost(level, stack);
        return be instanceof WirelessPatternProviderHost provider ? provider : null;
    }

    @Nullable
    public static OverloadedInterfaceBlockEntity getSelectedInterface(Level level, ItemStack stack) {
        var be = resolveSelectedHost(level, stack);
        return be instanceof OverloadedInterfaceBlockEntity iface ? iface : null;
    }

    @Nullable
    public static OverloadedPowerSupplyBlockEntity getSelectedPowerSupply(Level level, ItemStack stack) {
        var be = resolveSelectedHost(level, stack);
        return be instanceof OverloadedPowerSupplyBlockEntity powerSupply ? powerSupply : null;
    }
}
