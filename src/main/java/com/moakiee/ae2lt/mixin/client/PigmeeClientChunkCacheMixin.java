package com.moakiee.ae2lt.mixin.client;

import com.moakiee.ae2lt.client.workstation.PigmeeRemoteChunkAccess;
import com.moakiee.ae2lt.client.workstation.PigmeeRemoteChunkCache;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public abstract class PigmeeClientChunkCacheMixin implements PigmeeRemoteChunkAccess {
    @Unique private PigmeeRemoteChunkCache ae2lt$remote;
    @Unique private int ae2lt$bodyX, ae2lt$bodyZ, ae2lt$bodyRadius;
    @Override public PigmeeRemoteChunkCache ae2lt$remoteChunks() { return ae2lt$remote; }
    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2lt$init(ClientLevel level, int radius, CallbackInfo ci) {
        ae2lt$remote = new PigmeeRemoteChunkCache(level); ae2lt$bodyRadius = Math.max(2, radius) + 3;
    }
    @Inject(method = "updateViewCenter", at = @At("TAIL"))
    private void ae2lt$bodyCenter(int x, int z, CallbackInfo ci) { ae2lt$bodyX = x; ae2lt$bodyZ = z; }
    @Inject(method = "updateViewRadius", at = @At("TAIL"))
    private void ae2lt$bodyRadius(int radius, CallbackInfo ci) { ae2lt$bodyRadius = Math.max(2, radius) + 3; }
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("RETURN"), cancellable = true)
    private void ae2lt$remoteFallback(int x, int z, ChunkStatus status, boolean required, CallbackInfoReturnable<LevelChunk> cir) {
        if (cir.getReturnValue() == null || cir.getReturnValue() instanceof EmptyLevelChunk) {
            var chunk = ae2lt$remote.get(x, z); if (chunk != null) cir.setReturnValue(chunk);
        }
    }
    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void ae2lt$remoteData(int x, int z, FriendlyByteBuf buffer, CompoundTag heightmaps,
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> entities, CallbackInfoReturnable<LevelChunk> cir) {
        if ((Math.abs((long)x - ae2lt$bodyX) > ae2lt$bodyRadius || Math.abs((long)z - ae2lt$bodyZ) > ae2lt$bodyRadius) && ae2lt$remote.accepts(x, z)) {
            cir.setReturnValue(ae2lt$remote.replace(x, z, buffer, heightmaps, entities));
        }
    }
    @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
    private void ae2lt$remoteBiomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo ci) {
        var chunk = ae2lt$remote.get(x, z);
        if (chunk != null && (Math.abs((long)x - ae2lt$bodyX) > ae2lt$bodyRadius || Math.abs((long)z - ae2lt$bodyZ) > ae2lt$bodyRadius)) {
            chunk.replaceBiomes(buffer); ci.cancel();
        }
    }
    @Inject(method = "drop", at = @At("HEAD"))
    private void ae2lt$remoteDrop(ChunkPos pos, CallbackInfo ci) { ae2lt$remote.drop(pos); }
    @Inject(method = "getLoadedChunksCount", at = @At("RETURN"), cancellable = true)
    private void ae2lt$count(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(cir.getReturnValue() + ae2lt$remote.count()); }
}
