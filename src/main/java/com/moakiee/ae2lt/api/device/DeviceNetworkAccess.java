package com.moakiee.ae2lt.api.device;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import com.moakiee.ae2lt.device.network.RailgunNetworkBinding;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Shared device binding using the existing AE2 wireless-link component and device range policy. */
public final class DeviceNetworkAccess {
    private DeviceNetworkAccess() {}

    public enum Failure { NOT_BOUND, DIM_NOT_LOADED, NO_AP, INACTIVE_AP, OUT_OF_RANGE, WRONG_DIMENSION }

    public record Resolution(@Nullable IGrid grid, @Nullable IWirelessAccessPoint accessPoint,
            @Nullable Failure failure) {
        public boolean success() { return grid != null && failure == null; }
    }

    public static @Nullable GlobalPos getBoundPos(ItemStack stack) {
        return RailgunNetworkBinding.INSTANCE.getBoundPos(stack);
    }

    /** Trusted server operation; the caller must authorize the selected target. */
    public static void bind(ItemStack stack, GlobalPos target) {
        RailgunNetworkBinding.INSTANCE.bind(stack, target);
    }

    public static void unbind(ItemStack stack) {
        RailgunNetworkBinding.INSTANCE.unbind(stack);
    }

    /** Resolve connectivity only; use a player action source for subsequent player storage operations. */
    public static Resolution resolve(ItemStack stack, ServerPlayer player) {
        if (!player.serverLevel().getServer().isSameThread()) {
            throw new IllegalStateException("Device binding resolution requires the server thread");
        }
        var result = RailgunNetworkBinding.INSTANCE.resolve(stack, player);
        return new Resolution(result.grid(), result.accessPoint(),
                result.failure() == null ? null : Failure.valueOf(result.failure().name()));
    }
}
