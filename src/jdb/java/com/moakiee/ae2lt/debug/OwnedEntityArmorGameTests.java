package com.moakiee.ae2lt.debug;

import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.ArmorWearer;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.OwnedEntityArmorHandler;
import com.moakiee.ae2lt.celestweave.module.MultidimensionalProtectionSubmodule;
import com.moakiee.ae2lt.celestweave.module.NightVisionSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.celestweave.service.ArmorEnergyService;
import com.moakiee.ae2lt.celestweave.service.ArmorLightningService;
import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.menu.hub.DeviceHubHost;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_owned_armor")
@PrefixGameTestTemplate(false)
public final class OwnedEntityArmorGameTests {
    private static final BlockPos AP = new BlockPos(3, 3, 3);
    private static final IActionSource ACTION = IActionSource.empty();

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void ownershipMustResolveToPlayerAndTransferRevokesOldIdentity(GameTestHelper h) {
        var owner = owner(h);
        var mob = companion(h, owner);
        var armor = armor(h, ModItems.CELESTWEAVE_CORE.get(),
                ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get());
        mob.setItemSlot(EquipmentSlot.CHEST, armor);
        OwnedEntityArmorHandler.tickArmor(mob);
        require(hit(mob, 100).isCanceled(), "owned entity did not activate its shield");
        mob.directOwner = null;
        require(ArmorWearer.isEligible(mob), "verified offline owner was forgotten");
        var restored = new OwnedWolf(h.getLevel());
        restored.setOwnerUUID(owner.getUUID());
        restored.getPersistentData().merge(mob.getPersistentData().copy());
        require(ArmorWearer.isEligible(restored), "saved ownership did not survive entity reload");
        mob.setOwnerUUID(UUID.randomUUID());
        OwnedEntityArmorHandler.tickArmor(mob);
        require(!ArmorWearer.isEligible(mob) && !hit(mob, 100).isCanceled(),
                "ownership transfer reused the previous player's authorization");
        require(!CelestweaveArmorState.isSubmoduleRuntimeActive(armor, MultidimensionalProtectionSubmodule.ID),
                "ineligible equipment retained an active runtime entry");
        mob.directOwner = new Zombie(EntityType.ZOMBIE, h.getLevel());
        mob.setOwnerUUID(mob.directOwner.getUUID());
        require(!ArmorWearer.isEligible(mob), "a non-player owner authorized armor");
        mob.directOwner = null;
        mob.setOwnerUUID(null);
        require(!ArmorWearer.isEligible(mob), "an unowned entity authorized armor");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void effectsAndSharedBuffersBelongToWearerOnly(GameTestHelper h) {
        var owner = owner(h);
        var mob = companion(h, owner);
        var head = armor(h, ModItems.CELESTWEAVE_OCULUS.get(), ModItems.CELESTWEAVE_SUBMODULE_NIGHT_VISION.get());
        var boots = armor(h, ModItems.CELESTWEAVE_STRIDE.get());
        var ownerArmor = armor(h, ModItems.CELESTWEAVE_CORE.get());
        ArmorEnergyBuffer.write(head, 1_000);
        ArmorEnergyBuffer.write(boots, 1_000);
        ArmorEnergyBuffer.write(ownerArmor, 1_000_000);
        mob.setItemSlot(EquipmentSlot.HEAD, head);
        mob.setItemSlot(EquipmentSlot.FEET, boots);
        owner.setItemSlot(EquipmentSlot.CHEST, ownerArmor);
        CelestweaveArmorState.syncSubmoduleActiveState(mob, head, h.getLevel().registryAccess(), true,
                Dist.DEDICATED_SERVER);
        require(mob.hasEffect(MobEffects.NIGHT_VISION) && !owner.hasEffect(MobEffects.NIGHT_VISION),
                "effect was applied to the owner rather than the wearer");
        var payment = ArmorEnergyService.consumeActiveCostPayment(mob, head, 1_500);
        require(payment.paid() && ArmorEnergyBuffer.read(head) + ArmorEnergyBuffer.read(boots) == 500,
                "payment did not pool the entity's own armor");
        require(ArmorEnergyBuffer.read(ownerArmor) == 1_000_000, "entity debited its owner's armor");
        require(!ArmorEnergyService.consumeActiveCostPayment(mob, head, 501).paid(),
                "insufficient entity energy fell back to the owner's armor");
        payment.refund();
        require(ArmorEnergyBuffer.read(head) == 1_000 && ArmorEnergyBuffer.read(boots) == 1_000,
                "refund did not restore the entity buffers");
        mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        NeoForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(mob, EquipmentSlot.HEAD, head, ItemStack.EMPTY));
        require(!mob.hasEffect(MobEffects.NIGHT_VISION), "removed armor retained its effect");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void offlineOwnerCanRechargeAndPayLightningFromBoundNetwork(GameTestHelper h) {
        var owner = owner(h);
        var mob = companion(h, owner);
        var armor = armor(h, ModItems.CELESTWEAVE_CORE.get());
        mob.setItemSlot(EquipmentSlot.CHEST, armor);
        require(ArmorWearer.isEligible(mob), "online ownership did not verify");
        mob.directOwner = null;
        network(h, armor, 1_000, 100, 100);
        h.runAtTickTime(40, () -> {
            var source = ArmorWearer.actionSource(mob);
            require(source != null && source.player().orElseThrow().getUUID().equals(owner.getUUID()),
                    "offline network identity lost the actual owner's UUID");
            require(ArmorEnergyBuffer.refillFromNetwork(armor, mob, 800) == 800,
                    "offline companion could not refill FE");
            require(stored(mob, armor, AppFluxBridge.FE_KEY) == 200 && ArmorEnergyBuffer.read(armor) == 800,
                    "FE recharge accounting was not conserved");
            require(ArmorEnergyService.consumeActiveCostPayment(mob, armor, 1_000).paid(),
                    "active cost could not use the entity's buffer and bound network");
            require(ArmorEnergyBuffer.read(armor) == 0 && stored(mob, armor, AppFluxBridge.FE_KEY) == 0,
                    "active FE cost was not deducted exactly once");
            require(ArmorLightningService.consume(mob, armor, new ArmorLightningService.LightningCost(7, 11)),
                    "offline companion could not pay HV/EHV");
            require(stored(mob, armor, LightningKey.HIGH_VOLTAGE) == 93
                    && stored(mob, armor, LightningKey.EXTREME_HIGH_VOLTAGE) == 89,
                    "lightning resources were not deducted exactly");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void equipmentAndInventoryTicksDoNotDoubleCharge(GameTestHelper h) {
        var owner = owner(h);
        var mob = companion(h, owner);
        var armor = armor(h, ModItems.CELESTWEAVE_OCULUS.get(), ModItems.CELESTWEAVE_SUBMODULE_NIGHT_VISION.get());
        mob.setItemSlot(EquipmentSlot.HEAD, armor);
        ArmorEnergyBuffer.write(armor, 1_000_000);
        network(h, armor, 0, 100_000, 100_000);
        h.runAtTickTime(40, () -> {
            OwnedEntityArmorHandler.tickArmor(mob);
            long after = ArmorEnergyBuffer.read(armor);
            require(after < 1_000_000 && mob.hasEffect(MobEffects.NIGHT_VISION), "passive tick never ran");
            armor.getItem().inventoryTick(armor, h.getLevel(), mob, 0, false);
            require(ArmorEnergyBuffer.read(armor) == after, "inventory tick charged the same armor twice");
            mob.directOwner = null;
            mob.setOwnerUUID(null);
            OwnedEntityArmorHandler.tickArmor(mob);
            require(!mob.hasEffect(MobEffects.NIGHT_VISION), "owner loss was hidden by tick deduplication");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void shieldAndForcedDeathProtectionRestoreCompanionNotOwner(GameTestHelper h) {
        var owner = owner(h);
        var mob = companion(h, owner);
        var armor = armor(h, ModItems.CELESTWEAVE_CORE.get(),
                ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get());
        mob.setItemSlot(EquipmentSlot.CHEST, armor);
        owner.setHealth(7);
        mob.setHealth(5);
        OwnedEntityArmorHandler.tickArmor(mob);
        require(hit(mob, Float.MAX_VALUE).isCanceled(), "entity shield did not cancel lethal damage");
        mob.setHealth(0);
        require(mob.isAlive() && mob.getHealth() == mob.getMaxHealth(), "setHealth bypassed companion protection");
        mob.kill();
        require(mob.isAlive() && !mob.isRemoved(), "kill bypassed companion protection");
        require(owner.getHealth() == 7, "companion protection changed the owner's health");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void entityHubUsesTargetEquipmentAndRejectsOtherOwnersOrDistance(GameTestHelper h) {
        var owner = owner(h);
        var mob = companion(h, owner);
        var armor = armor(h, ModItems.CELESTWEAVE_CORE.get(),
                ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get());
        var ownArmor = armor(h, ModItems.CELESTWEAVE_CORE.get(),
                ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get());
        mob.setItemSlot(EquipmentSlot.CHEST, armor);
        owner.setItemSlot(EquipmentSlot.CHEST, ownArmor);
        var menu = new DeviceHubMenu(17, owner.getInventory(), DeviceHubMenu.TAB_CHESTPLATE, mob);
        require(menu.stillValid(owner), "owner could not configure nearby companion");
        var stranger = owner(h);
        stranger.setPos(owner.position());
        require(!menu.stillValid(stranger), "another player could configure companion");
        menu.toggleModule(0);
        require(!CelestweaveArmorState.isSubmoduleEnabled(armor, MultidimensionalProtectionSubmodule.INSTANCE)
                        && CelestweaveArmorState.isSubmoduleEnabled(ownArmor, MultidimensionalProtectionSubmodule.INSTANCE),
                "entity configuration modified the owner's armor");
        owner.setPos(owner.position().add(50, 0, 0));
        require(!menu.stillValid(owner), "remote entity menu remained valid");
        menu.toggleModule(0);
        require(!CelestweaveArmorState.isSubmoduleEnabled(armor, MultidimensionalProtectionSubmodule.INSTANCE),
                "out-of-range action still modified armor");
        owner.setPos(mob.position());
        mob.directOwner = stranger;
        mob.setOwnerUUID(stranger.getUUID());
        require(!menu.stillValid(owner) && DeviceHubHost.canConfigure(stranger, mob),
                "ownership transfer did not revoke the old owner's menu");
        menu.toggleModule(0);
        require(!CelestweaveArmorState.isSubmoduleEnabled(armor, MultidimensionalProtectionSubmodule.INSTANCE),
                "old owner still changed target armor");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void realTouhouMaidTicksArmorWhileOwnerIsOffline(GameTestHelper h) {
        if (!ModList.get().isLoaded("touhou_little_maid")) {
            h.succeed();
            return;
        }
        var owner = owner(h);
        var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("touhou_little_maid:maid"));
        var maid = (TamableAnimal) type.create(h.getLevel());
        require(maid != null, "maid entity was not registered");
        maid.setOwnerUUID(owner.getUUID());
        maid.setTame(true, true);
        maid.setNoAi(true);
        // GameTestServer has no profile cache or real login transport. Temporarily register the
        // fixture in PlayerList's UUID index, which is the API used by the real maid's getOwner().
        withOnlineOwner(owner, () -> require(ArmorWearer.isEligible(maid),
                "real maid did not verify its online player owner"));
        maid.setPos(owner.position().add(1, 0, 0));
        var armor = armor(h, ModItems.CELESTWEAVE_CORE.get(),
                ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get());
        maid.setItemSlot(EquipmentSlot.CHEST, armor);
        h.getLevel().addFreshEntity(maid);
        h.runAtTickTime(5, () -> {
            require(maid.getOwner() == null, "fixture owner should be offline");
            require(hit(maid, Float.MAX_VALUE).isCanceled(), "real maid did not receive armor ticks");
            require(DeviceHubHost.canConfigure(owner, maid), "owner could not configure real maid");
            maid.setHealth(0);
            require(maid.isAlive(), "real maid bypassed undying protection");
            maid.setOwnerUUID(null);
        });
        h.runAtTickTime(7, () -> {
            require(!hit(maid, 1).isCanceled(), "ownerless maid kept special protection");
            maid.discard();
            h.succeed();
        });
    }

    @SuppressWarnings("unchecked")
    private static void withOnlineOwner(ServerPlayer owner, Runnable action) {
        try {
            var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);
            var players = (java.util.Map<UUID, ServerPlayer>) field.get(owner.server.getPlayerList());
            players.put(owner.getUUID(), owner);
            try {
                action.run();
            } finally {
                players.remove(owner.getUUID());
            }
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static ServerPlayer owner(GameTestHelper h) {
        var owner = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ArmorOwner"));
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        return owner;
    }

    private static OwnedWolf companion(GameTestHelper h, ServerPlayer owner) {
        var mob = new OwnedWolf(h.getLevel());
        mob.setOwnerUUID(owner.getUUID());
        mob.directOwner = owner;
        mob.setPos(owner.position().add(1, 0, 0));
        return mob;
    }

    private static ItemStack armor(GameTestHelper h, Item item, Item... modules) {
        var armor = new ItemStack(item);
        CelestweaveArmorState.setSlot(armor, h.getLevel().registryAccess(), CelestweaveArmorState.SLOT_CORE,
                new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
        for (var module : modules) {
            require(CelestweaveArmorState.installOneModule(armor, h.getLevel().registryAccess(), new ItemStack(module)),
                    "module installation rejected: " + module);
        }
        return armor;
    }

    private static LivingIncomingDamageEvent hit(LivingEntity wearer, float damage) {
        ArmorCapabilityCollector.clearCache(wearer);
        return NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(wearer,
                new DamageContainer(wearer.damageSources().generic(), damage)));
    }

    private static void network(GameTestHelper h, ItemStack armor, long fe, long hv, long ehv) {
        h.setBlock(AP, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                net.minecraft.core.Direction.UP));
        h.setBlock(AP.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(AP.below().north(), AEBlocks.DRIVE.block());
        DriveBlockEntity drive = h.getBlockEntity(AP.below().north());
        var lightningCell = new ItemStack(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get());
        var lightning = StorageCells.getCellInventory(lightningCell, null);
        require(lightning != null, "lightning cell missing");
        lightning.insert(LightningKey.HIGH_VOLTAGE, hv, Actionable.MODULATE, ACTION);
        lightning.insert(LightningKey.EXTREME_HIGH_VOLTAGE, ehv, Actionable.MODULATE, ACTION);
        lightning.persist();
        drive.getInternalInventory().setItemDirect(0, lightningCell);
        if (fe > 0) {
            require(AppFluxBridge.isAvailable(), "Applied Flux is required by recharge tests");
            var cell = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_64m_cell")));
            var storage = StorageCells.getCellInventory(cell, null);
            require(storage != null && storage.insert(AppFluxBridge.FE_KEY, fe, Actionable.MODULATE, ACTION) == fe,
                    "could not fill FE cell");
            storage.persist();
            drive.getInternalInventory().setItemDirect(1, cell);
        }
        ArmorNetworkBinding.INSTANCE.bind(armor, GlobalPos.of(h.getLevel().dimension(), h.absolutePos(AP)));
    }

    private static long stored(LivingEntity wearer, ItemStack armor, AEKey key) {
        var result = ArmorNetworkBinding.INSTANCE.resolve(armor, wearer);
        require(result.success(), "network unavailable: " + result.failure());
        MEStorage storage = result.grid().getStorageService().getInventory();
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, ACTION);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class OwnedWolf extends Wolf {
        private LivingEntity directOwner;

        private OwnedWolf(ServerLevel level) {
            super(EntityType.WOLF, level);
        }

        @Override
        public LivingEntity getOwner() {
            return directOwner;
        }
    }
}
