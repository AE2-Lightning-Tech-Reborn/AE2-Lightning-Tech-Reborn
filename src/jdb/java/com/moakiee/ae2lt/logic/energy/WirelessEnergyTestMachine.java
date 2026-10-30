package com.moakiee.ae2lt.logic.energy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Dedicated ticker fixture; excluded from the product JAR. No production tick hook is faked. */
@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.MOD)
public final class WirelessEnergyTestMachine extends BlockEntity {
    private static final ResourceLocation ID = ResourceLocation.parse("ae2lt:energy_admission_test_machine");
    static MachineBlock BLOCK;
    static BlockEntityType<WirelessEnergyTestMachine> TYPE;
    Direction inputFace = Direction.NORTH;
    int simulations;
    int ticks, receiveCalls, received, receiveLimit = 100000, actualLimit = 100000, retainedFloor, reportedCapacity = 100000;
    boolean drain, instantTrash, unknown, inputReady = true, accepting = true;
    Runnable duringTick = () -> {}, duringSimulation = () -> {}, afterReceive = () -> {}, duringCapability = () -> {};
    ProbeStorage energy = makeStorage();
    ProbeStorage makeStorage() { return new ProbeStorage(); }
    final class ProbeStorage extends EnergyStorage {
        ProbeStorage() { super(100000, 100000, 0); }
        @Override public boolean canReceive() { return accepting && super.canReceive(); }
        void prime(int value) { energy = value; }
        @Override public int receiveEnergy(int amount, boolean simulate) {
            if (simulate) { simulations++; duringSimulation.run(); }
            int limit = simulate ? receiveLimit : Math.min(receiveLimit, actualLimit);
            int accepted = instantTrash ? Math.min(amount, limit) : super.receiveEnergy(Math.min(amount, limit), simulate);
            if (!simulate) { receiveCalls++; received += accepted; setChanged(); afterReceive.run(); }
            return accepted;
        }
        @Override public int getEnergyStored() { return unknown ? Integer.MAX_VALUE : super.getEnergyStored(); }
        @Override public int getMaxEnergyStored() { return reportedCapacity; }
    }
    WirelessEnergyTestMachine(BlockPos pos, BlockState state) { super(TYPE, pos, state); }
    private void machineTick() {
        ticks++; duringTick.run();
        if (drain) energy.prime(retainedFloor);
    }
    @Override protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putInt("testEnergy", energy.getEnergyStored()); tag.putInt("testReceived", received);
    }
    @Override protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); energy.prime(tag.getInt("testEnergy")); received = tag.getInt("testReceived");
    }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, ID, () -> BLOCK = new MachineBlock(BlockBehaviour.Properties.of()));
        event.register(Registries.ITEM, ID, () -> new net.minecraft.world.item.BlockItem(BLOCK, new net.minecraft.world.item.Item.Properties()));
        event.register(Registries.BLOCK_ENTITY_TYPE, ID, () -> TYPE = BlockEntityType.Builder.of(WirelessEnergyTestMachine::new, BLOCK).build(null));
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TYPE,
                (be, face) -> {
                    be.duringCapability.run();
                    return be.inputReady && face == be.inputFace ? be.energy : null;
                });
    }
    static final class MachineBlock extends BaseEntityBlock {
        MachineBlock(Properties properties) { super(properties); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(MachineBlock::new); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WirelessEnergyTestMachine(pos, state); }
        @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
            super.onPlace(state, level, pos, oldState, moved);
            if (level.getBlockEntity(pos) instanceof WirelessEnergyTestMachine machine) {
                machine.energy = machine.makeStorage();
                level.invalidateCapabilities(pos);
            }
        }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide ? null : (l, p, s, be) -> ((WirelessEnergyTestMachine) be).machineTick();
        }
    }
}
