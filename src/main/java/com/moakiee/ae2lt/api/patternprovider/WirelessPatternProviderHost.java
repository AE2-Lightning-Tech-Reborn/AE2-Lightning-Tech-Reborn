package com.moakiee.ae2lt.api.patternprovider;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.logic.wireless.support.WirelessConnectionRef;

/**
 * Public connector-facing contract for wireless pattern providers.
 *
 * <p>Addon providers can participate in AE2LT's connector, renderer and batch
 * editing flows without extending AE2LT's concrete provider implementation.
 */
public interface WirelessPatternProviderHost {
    /** World position of the provider. */
    BlockPos getProviderPos();

    /** Whether this provider currently accepts wireless machine connections. */
    boolean isWirelessProvider();

    /** Read-only live view of the configured endpoints. */
    List<? extends WirelessConnectionRef> getConnections();

    /** Public immutable snapshots; preferred over the legacy implementation-typed view. */
    default List<WirelessEndpoint> getEndpointSnapshots() {
        return getConnections().stream()
                .map(c -> new WirelessEndpoint(c.dimension(), c.pos(), c.boundFace())).toList();
    }

    /** Adds a new endpoint or changes the selected face of an existing endpoint. */
    boolean addOrUpdateConnection(
            ResourceKey<Level> dimension, BlockPos pos, Direction boundFace);

    /** Removes an endpoint identified by dimension and block position. */
    boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos);

    /** Maximum number of endpoint records this host accepts. */
    int getMaxWirelessConnections();

    /**
     * Server-side target admission for the connector. Addons can require an installed
     * core and recognize non-block-entity machines without weakening other providers.
     */
    default boolean acceptsWirelessTarget(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) != null;
    }

    /** User-facing reason shown when the selected target is not accepted. */
    default Component getWirelessTargetRejectionMessage() {
        return Component.translatable("ae2lt.connector.not_machine");
    }
}
