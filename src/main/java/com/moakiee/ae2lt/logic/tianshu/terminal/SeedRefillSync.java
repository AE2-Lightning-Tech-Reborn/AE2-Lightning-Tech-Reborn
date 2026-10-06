package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.stacks.AEKey;
import appeng.menu.guisync.PacketWritable;
import com.moakiee.ae2lt.logic.tianshu.loop.TianshuSeedRefillService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/** Outcome of the last manual closed-loop seed refill, shown in the terminal status area. */
public record SeedRefillSync(int state, List<Entry> problems) implements PacketWritable {
    public static final int STATE_NONE = 0;
    public static final int STATE_COMPLETE = 1;
    public static final int STATE_NETWORK_MISSING = 2;
    public static final int STATE_STORAGE_BLOCKED = 3;
    public static final int STATE_MIXED = 4;
    public static final int STATE_UNAVAILABLE = 5;
    public static final int STATE_RETURN_BLOCKED = 6;

    private static final int MAX_MISSING_ENTRIES = 16;
    private static final SeedRefillSync NONE = new SeedRefillSync(STATE_NONE, List.of());

    public record Entry(AEKey what, long networkMissing, long storageBlocked, long returnBlocked) {
    }

    public SeedRefillSync {
        problems = List.copyOf(problems);
        if (state < STATE_NONE || state > STATE_RETURN_BLOCKED) {
            throw new IllegalArgumentException("invalid seed refill state: " + state);
        }
    }

    public SeedRefillSync(FriendlyByteBuf data) {
        this(data.readVarInt(), readMissing(data));
    }

    public static SeedRefillSync none() {
        return NONE;
    }

    public static SeedRefillSync of(TianshuSeedRefillService.RefillResult result) {
        if (!result.available()) return new SeedRefillSync(STATE_UNAVAILABLE, List.of());
        boolean networkMissing = !result.networkMissing().isEmpty();
        boolean storageBlocked = !result.storageBlocked().isEmpty();
        boolean returnBlocked = !result.returnBlocked().isEmpty();
        if (!networkMissing && !storageBlocked && !returnBlocked) {
            return new SeedRefillSync(STATE_COMPLETE, List.of());
        }
        var entries = new ArrayList<Entry>();
        var keys = new LinkedHashSet<AEKey>();
        keys.addAll(result.networkMissing().keySet());
        keys.addAll(result.storageBlocked().keySet());
        keys.addAll(result.returnBlocked().keySet());
        for (var key : keys) {
            if (entries.size() >= MAX_MISSING_ENTRIES) break;
            entries.add(new Entry(key,
                    result.networkMissing().getOrDefault(key, 0L),
                    result.storageBlocked().getOrDefault(key, 0L),
                    result.returnBlocked().getOrDefault(key, 0L)));
        }
        int problemKinds = (networkMissing ? 1 : 0) + (storageBlocked ? 1 : 0) + (returnBlocked ? 1 : 0);
        int state = problemKinds > 1 ? STATE_MIXED
                : networkMissing ? STATE_NETWORK_MISSING
                : storageBlocked ? STATE_STORAGE_BLOCKED : STATE_RETURN_BLOCKED;
        return new SeedRefillSync(state, entries);
    }

    @Override
    public void writeToPacket(FriendlyByteBuf data) {
        data.writeVarInt(state);
        data.writeVarInt(problems.size());
        for (var entry : problems) {
            AEKey.writeKey(data, entry.what());
            data.writeVarLong(entry.networkMissing());
            data.writeVarLong(entry.storageBlocked());
            data.writeVarLong(entry.returnBlocked());
        }
    }

    private static List<Entry> readMissing(FriendlyByteBuf data) {
        int size = data.readVarInt();
        var result = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) {
            result.add(new Entry(AEKey.readKey(data), data.readVarLong(), data.readVarLong(), data.readVarLong()));
        }
        return result;
    }
}
