package com.moakiee.ae2lt.client.frequency;

import com.moakiee.ae2lt.logic.management.ManagementQuery;
import com.moakiee.ae2lt.logic.management.ManagementMode;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Bounded presentation state only. Every reopen still requests a fresh server-authorized snapshot. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class ManagementViewCache {
    public record View(ManagementQuery query, UUID selection) {}
    private record Key(String dimension, BlockPos pos, int frequency, ManagementMode mode) {}
    private static final LinkedHashMap<Key, View> VIEWS = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Key, View> entry) { return size() > 32; }
    };
    private ManagementViewCache() {}
    private static Key key(BlockPos pos, int frequency, ManagementMode mode) {
        var level = Minecraft.getInstance().level;
        return new Key(level == null ? "" : level.dimension().location().toString(), pos.immutable(), frequency, mode);
    }
    public static View get(BlockPos pos, int frequency, ManagementMode mode) {
        return VIEWS.getOrDefault(key(pos, frequency, mode), new View(ManagementQuery.initial(mode), null));
    }
    public static View latest(BlockPos pos, int frequency) {
        var base = key(pos, frequency, ManagementMode.DEVICES);
        View latest = new View(ManagementQuery.initial(ManagementMode.DEVICES), null);
        for (var entry : VIEWS.entrySet()) {
            var key = entry.getKey();
            if (key.dimension.equals(base.dimension) && key.pos.equals(base.pos) && key.frequency == frequency)
                latest = entry.getValue();
        }
        return latest;
    }
    public static void remember(BlockPos pos, int frequency, ManagementQuery query, UUID selection) {
        if (query.valid()) VIEWS.put(key(pos, frequency, query.mode()), new View(query, selection));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { VIEWS.clear(); }
}
