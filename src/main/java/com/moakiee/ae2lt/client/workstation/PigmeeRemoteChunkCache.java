package com.moakiee.ae2lt.client.workstation;

import com.moakiee.ae2lt.workstation.PigmeeRemoteView;
import com.moakiee.ae2lt.workstation.PigmeeRemoteViewPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** At most 25 distant chunks, keyed by actual coordinates, separate from vanilla's ring. */
public final class PigmeeRemoteChunkCache {
    private final ClientLevel level;
    private final Map<Long, LevelChunk> chunks = new HashMap<>();
    private UUID token;
    private int x, z;
    private boolean active;
    public PigmeeRemoteChunkCache(ClientLevel level) { this.level = level; }
    public boolean accepts(int x, int z) {
        return active && Math.abs((long)x - this.x) <= PigmeeRemoteView.RADIUS && Math.abs((long)z - this.z) <= PigmeeRemoteView.RADIUS;
    }
    public LevelChunk get(int x, int z) { return chunks.get(ChunkPos.asLong(x, z)); }
    public int count() { return chunks.size(); }
    public LevelChunk replace(int x, int z, FriendlyByteBuf buffer, CompoundTag heightmaps,
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities) {
        if (!accepts(x, z)) return null;
        var pos = new ChunkPos(x, z);
        var chunk = chunks.computeIfAbsent(pos.toLong(), key -> new LevelChunk(level, pos));
        chunk.replaceWithPacketData(buffer, heightmaps, blockEntities);
        level.onChunkLoaded(pos);
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
        return chunk;
    }
    public boolean drop(ChunkPos pos) {
        var chunk = chunks.remove(pos.toLong());
        if (chunk == null) return false;
        // A newly installed native body chunk takes precedence over a former remote copy.
        if (level.getChunkSource().getChunk(pos.x, pos.z, false) == null) {
            NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
            level.unload(chunk);
        } else chunk.clearAllBlockEntities();
        return true;
    }
    private void forget(ChunkPos pos) {
        drop(pos);
        if (level.getChunkSource().getChunk(pos.x, pos.z, false) != null) return;
        level.queueLightUpdate(() -> {
            // A native body chunk or a newer remote generation may arrive before this queue runs.
            if (level.getChunkSource().getChunk(pos.x, pos.z, false) != null) return;
            var light = level.getLightEngine(); light.setLightEnabled(pos, false);
            for (int y = light.getMinLightSection(); y < light.getMaxLightSection(); y++) {
                var section = SectionPos.of(pos, y);
                light.queueSectionData(LightLayer.BLOCK, section, null);
                light.queueSectionData(LightLayer.SKY, section, null);
            }
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) light.updateSectionStatus(SectionPos.of(pos, y), true);
        });
    }
    private void state(PigmeeRemoteViewPacket packet) {
        if (!packet.active() && !packet.token().equals(token)) return;
        boolean switched = token != null && !token.equals(packet.token());
        token = packet.token(); active = packet.active(); x = packet.x(); z = packet.z();
        for (long key : java.util.Set.copyOf(chunks.keySet())) {
            var pos = new ChunkPos(key);
            if (switched || !accepts(pos.x, pos.z)) forget(pos);
        }
    }
    public static void receive(PigmeeRemoteViewPacket packet) {
        var level = Minecraft.getInstance().level;
        if (level != null) ((PigmeeRemoteChunkAccess) level.getChunkSource()).ae2lt$remoteChunks().state(packet);
    }
}
