package com.moakiee.ae2lt.debug;

import java.util.List;
import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler;
import com.moakiee.ae2lt.celestweave.module.OverloadProtectionSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.celestweave.service.ArmorTickService;
import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.item.railgun.RailgunModuleStorage;
import com.moakiee.ae2lt.item.railgun.RailgunStructuralCore;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual registry/NBT, event bus, armor buffers and bound ME storage; debug source set only. */
@GameTestHolder("ae2lt_protection")
@PrefixGameTestTemplate(false)
public final class ArmorProtectionGameTests {
    private static final BlockPos AP = new BlockPos(3, 3, 3);
    private static final IActionSource ACTION = IActionSource.empty();
    private static final long CAP = 20_000_000_000L;

    private static ItemStack armor(GameTestHelper h, Item module) {
        var armor = new ItemStack(ModItems.CELESTWEAVE_CORE.get());
        CelestweaveArmorState.setSlot(armor, h.getLevel().registryAccess(), CelestweaveArmorState.SLOT_CORE,
                new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
        require(CelestweaveArmorState.installOneModule(armor, h.getLevel().registryAccess(),
                new ItemStack(ModItems.ENERGY_MODULE_T3.get())), "energy module rejected");
        require(CelestweaveArmorState.installOneModule(armor, h.getLevel().registryAccess(),
                new ItemStack(module)), "shield module rejected");
        ArmorEnergyBuffer.write(armor, h.getLevel().registryAccess(), CAP);
        return armor;
    }

    private static ServerPlayer equip(GameTestHelper h, ItemStack armor) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ShieldTest"));
        player.setItemSlot(EquipmentSlot.CHEST, armor);
        CelestweaveArmorState.syncSubmoduleActiveState(player, armor, h.getLevel().registryAccess(), true,
                Dist.DEDICATED_SERVER);
        return player;
    }

    private static void network(GameTestHelper h, ItemStack armor, long ehv) {
        h.setBlock(AP, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                net.minecraft.core.Direction.UP));
        h.setBlock(AP.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(AP.below().north(), AEBlocks.DRIVE.block());
        var cell = new ItemStack(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get());
        var inventory = StorageCells.getCellInventory(cell, null);
        require(inventory != null, "lightning cell missing");
        require(inventory.insert(LightningKey.EXTREME_HIGH_VOLTAGE, ehv, Actionable.MODULATE, ACTION) == ehv,
                "failed to fill EHV");
        inventory.persist();
        DriveBlockEntity drive = h.getBlockEntity(AP.below().north());
        drive.getInternalInventory().setItemDirect(0, cell);
        ArmorNetworkBinding.INSTANCE.bind(armor, GlobalPos.of(h.getLevel().dimension(), h.absolutePos(AP)));
    }

    private static MEStorage storage(ServerPlayer player, ItemStack armor) {
        var result = ArmorNetworkBinding.INSTANCE.resolve(armor, player);
        require(result.success(), "network binding failed: " + result.failure());
        return result.grid().getStorageService().getInventory();
    }

    private static long ehv(ServerPlayer player, ItemStack armor) {
        return storage(player, armor).extract(LightningKey.EXTREME_HIGH_VOLTAGE, Long.MAX_VALUE,
                Actionable.SIMULATE, ACTION);
    }

    private static LivingIncomingDamageEvent hit(ServerPlayer player, float amount, boolean hard) {
        ArmorCapabilityCollector.clearCache(player);
        var source = hard ? player.damageSources().magic() : player.damageSources().generic();
        return NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(player, new DamageContainer(source, amount)));
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void phaseCancelsWithoutZeroingAndOnlyAbsorbs1024(GameTestHelper h) {
        var armor = armor(h, ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD.get());
        var player = equip(h, armor);
        network(h, armor, 10_000);
        h.runAtTickTime(40, () -> {
            var first = hit(player, 512, false);
            require(first.isCanceled() && first.getAmount() == 512, "phase must only cancel a covered hit");
            first.setCanceled(false);
            require(first.getAmount() == 512, "uncancel must restore a real phase hit");
            require(ArmorEnergyBuffer.read(armor) == CAP - 10_240_000 && ehv(player, armor) == 8_976,
                    "phase per-damage fee incorrect");
            var atCap = hit(player, 1024, false);
            require(atCap.isCanceled() && atCap.getAmount() == 1024, "1024 boundary incorrect");
            var over = hit(player, 1025, false);
            require(!over.isCanceled() && over.getAmount() == 1, "1025 should leave one point");
            var huge = hit(player, Float.MAX_VALUE, true);
            require(!huge.isCanceled() && huge.getAmount() > 1024, "phase stopped a lethal huge hit");
            require(ArmorEnergyBuffer.read(armor) == CAP - 20_480_000 && ehv(player, armor) == 7_952,
                    "phase repeated hits exceeded shared window cost");
        });
        h.runAtTickTime(60, () -> {
            hit(player, 1, false);
            require(ArmorEnergyBuffer.read(armor) == CAP - 20_500_000 && ehv(player, armor) == 7_950,
                    "phase window did not expire at exactly 20 ticks");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void ordinaryDamageHasFixedCapAndDoesNotBuildDeathCombos(GameTestHelper h) {
        var armor = armor(h, ModItems.CELESTWEAVE_SUBMODULE_OVERLOAD_PROTECTION.get());
        var player = equip(h, armor);
        ArmorEnergyBuffer.write(armor, 2_000_000_000L);
        network(h, armor, 1024);
        h.runAtTickTime(40, () -> {
            var event = hit(player, Float.MAX_VALUE, true);
            require(event.isCanceled() && event.getAmount() == 0, "overload did not zero a huge hit");
            require(ArmorEnergyBuffer.read(armor) == 0 && ehv(player, armor) == 0,
                    "first huge hit did not use the initial tier cap");
            // Reopening/reposting the same attack and reaching Pre must not create another combo.
            event.setAmount(Float.MAX_VALUE);
            event.setCanceled(false);
            NeoForge.EVENT_BUS.post(event);
            event.setAmount(Float.MAX_VALUE);
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre(
                    player, event.getContainer()));
            require(ArmorEnergyBuffer.read(armor) == 0 && ehv(player, armor) == 0,
                    "same damage callbacks charged more than once");
            for (int hit = 2; hit <= 100; hit++) {
                require(hit(player, Float.MAX_VALUE, false).isCanceled(), "ordinary damage raised its fixed cap");
                require(ArmorEnergyBuffer.read(armor) == 0 && ehv(player, armor) == 0,
                        "repeated ordinary damage charged extra");
            }
            ArmorNetworkBinding.INSTANCE.unbind(armor);
        });
        h.runAtTickTime(41, () -> {
            ArmorTickService.tickEquipped(player, armor, true, h.getLevel().registryAccess(), Dist.DEDICATED_SERVER);
            require(hit(player, Float.MAX_VALUE, false).isCanceled(), "paid cap failed after depletion");
            require(CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player), "death did not reuse capped credit");
            CelestweaveArmorState.setSubmoduleEnabled(armor, OverloadProtectionSubmodule.INSTANCE, false);
            ArmorTickService.tickEquipped(player, armor, true, h.getLevel().registryAccess(), Dist.DEDICATED_SERVER);
            require(!hit(player, 1, false).isCanceled(), "paid credit ignored disabled toggle");
            CelestweaveArmorState.setSubmoduleEnabled(armor, OverloadProtectionSubmodule.INSTANCE, true);
        });
        h.runAtTickTime(42, () -> {
            ArmorTickService.tickEquipped(player, armor, true, h.getLevel().registryAccess(), Dist.DEDICATED_SERVER);
            ArmorCapabilityCollector.clearCache(player);
            require(!CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player),
                    "second death should need the tier-two difference");
            require(hit(player, Float.MAX_VALUE, false).isCanceled(),
                    "unpaid higher death tier raised ordinary damage's fee cap");
        });
        h.runAtTickTime(60, () -> {
            ArmorTickService.tickEquipped(player, armor, true, h.getLevel().registryAccess(), Dist.DEDICATED_SERVER);
            require(!hit(player, 1, false).isCanceled(), "expired window gave free protection");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void directDeathCombosPayDifferencesAndResetAtTwentyTicks(GameTestHelper h) {
        var armor = armor(h, ModItems.CELESTWEAVE_SUBMODULE_OVERLOAD_PROTECTION.get());
        var player = equip(h, armor);
        network(h, armor, 16_384);
        for (int i = 0; i < 16; i++) {
            int combo = i + 1;
            h.runAtTickTime(40 + i, () -> {
                ArmorCapabilityCollector.clearCache(player);
                require(CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player), "death combo failed " + combo);
                require(CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player), "same-tick death reentry failed");
                require(hit(player, Float.MAX_VALUE, false).isCanceled(),
                        "ordinary damage after death required another tier payment");
                require(ArmorEnergyBuffer.read(armor) == CAP - Math.min(CAP, 2_000_000_000L * combo)
                                && ehv(player, armor) == 16_384 - 1024L * combo,
                        "wrong death fee/reentry charged twice at combo " + combo);
            });
        }
        h.runAtTickTime(59, () -> {
            ArmorNetworkBinding.INSTANCE.unbind(armor);
            ArmorTickService.tickEquipped(player, armor, true, h.getLevel().registryAccess(), Dist.DEDICATED_SERVER);
            require(hit(player, Float.MAX_VALUE, false).isCanceled(), "capped shield required extra resources");
            require(CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player), "capped death required extra resources");
        });
        h.runAtTickTime(60, () -> {
            ArmorTickService.tickEquipped(player, armor, true, h.getLevel().registryAccess(), Dist.DEDICATED_SERVER);
            ArmorCapabilityCollector.clearCache(player);
            require(!CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player), "combo extended the fixed window");
            ArmorNetworkBinding.INSTANCE.bind(armor, GlobalPos.of(h.getLevel().dimension(), h.absolutePos(AP)));
            storage(player, armor).insert(LightningKey.EXTREME_HIGH_VOLTAGE, 1024, Actionable.MODULATE, ACTION);
            ArmorEnergyBuffer.write(armor, 2_000_000_000L);
            CelestweaveArmorState.setModulesPowered(armor, true);
            CelestweaveArmorState.syncSubmoduleActiveState(player, armor, h.getLevel().registryAccess(), true,
                    Dist.DEDICATED_SERVER);
            ArmorCapabilityCollector.clearCache(player);
            require(CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player), "new window did not reset combo");
            require(ArmorEnergyBuffer.read(armor) == 0 && ehv(player, armor) == 0, "new window charged the wrong tier");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void mixedShieldDeathComboAndFailedPaymentDoNotDoubleBill(GameTestHelper h) {
        var armor = armor(h, ModItems.CELESTWEAVE_SUBMODULE_OVERLOAD_PROTECTION.get());
        var player = equip(h, armor);
        network(h, armor, 16_384);
        h.runAtTickTime(40, () -> {
            ArmorEnergyBuffer.write(armor, 1_999_999_999L);
            require(!CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player), "underfunded death was protected");
            require(ArmorEnergyBuffer.read(armor) == 1_999_999_999L && ehv(player, armor) == 16_384,
                    "failed death took partial resources");
            ArmorEnergyBuffer.write(armor, CAP);
            require(hit(player, 400, false).isCanceled(), "first shield failed");
            require(ArmorEnergyBuffer.read(armor) == CAP - 8_000_000 && ehv(player, armor) == 15_584,
                    "small hit was charged the full tier");
        });
        h.runAtTickTime(59, () -> {
            ArmorCapabilityCollector.clearCache(player);
            require(CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player), "death did not share shield combo");
            require(ArmorEnergyBuffer.read(armor) == CAP - 2_000_000_000L && ehv(player, armor) == 15_360,
                    "first death after shielding did not stay at tier 1");
        });
        h.runAtTickTime(60, () -> {
            ArmorCapabilityCollector.clearCache(player);
            require(CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player), "new death window failed");
            require(ArmorEnergyBuffer.read(armor) == CAP - 4_000_000_000L && ehv(player, armor) == 14_336,
                    "late combo moved the deadline or failed to reset to tier 1");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void failedPaymentLeavesNoCreditAndNoPartialDebit(GameTestHelper h) {
        var armor = armor(h, ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD.get());
        var player = equip(h, armor);
        network(h, armor, 1);
        h.runAtTickTime(40, () -> {
            require(!hit(player, 10, false).isCanceled(), "insufficient EHV blocked damage");
            require(ArmorEnergyBuffer.read(armor) == CAP && ehv(player, armor) == 1, "failed payment took resources");
            storage(player, armor).insert(LightningKey.EXTREME_HIGH_VOLTAGE, 19, Actionable.MODULATE, ACTION);
            ArmorEnergyBuffer.write(armor, 0);
            require(!hit(player, 10, false).isCanceled(), "insufficient FE blocked damage");
            require(ehv(player, armor) == 20, "failed FE payment consumed EHV");
            ArmorEnergyBuffer.write(armor, 200_000);
            require(hit(player, 10, false).isCanceled(), "funded shield failed");
            require(ArmorEnergyBuffer.read(armor) == 0 && ehv(player, armor) == 0,
                    "unpaid hit granted free window credit");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void multidimensionalIsFreeAndEveryShieldPairConflicts(GameTestHelper h) {
        var tiers = List.of(ModItems.CELESTWEAVE_SUBMODULE_MATRIX_SHIELD.get(),
                ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD.get(),
                ModItems.CELESTWEAVE_SUBMODULE_OVERLOAD_PROTECTION.get(),
                ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get());
        for (Item first : tiers) {
            for (Item second : tiers) {
                var armor = armor(h, first);
                require(!CelestweaveArmorState.installOneModule(armor, h.getLevel().registryAccess(),
                        new ItemStack(second)), "shield pair installed together: " + first + "/" + second);
            }
        }
        var armor = armor(h, ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get());
        ArmorEnergyBuffer.write(armor, 0);
        var player = equip(h, armor);
        ArmorTickService.tickEquipped(player, armor, true, h.getLevel().registryAccess(), Dist.DEDICATED_SERVER);
        require(hit(player, Float.MAX_VALUE, true).isCanceled(), "free shield needs resources");
        require(CelestweaveArmorUndyingHandler.tryProtectForcedDeath(player), "free death protection needs resources");
        require(ArmorEnergyBuffer.read(armor) == 0, "free protection changed FE");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void oldStackAndArmorNbtMigrateWithoutLosingSettings(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var oldItem = new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_OVERLOAD_PROTECTION.get(), 3);
        oldItem.set(DataComponents.CUSTOM_NAME, Component.literal("legacy module"));
        var oldTag = (CompoundTag) oldItem.save(registries);
        oldTag.putString("id", "ae2lt:module_undying");
        var migratedItem = ItemStack.parse(registries, oldTag).orElseThrow();
        require(migratedItem.is(ModItems.CELESTWEAVE_SUBMODULE_OVERLOAD_PROTECTION.get())
                && migratedItem.getCount() == 3 && migratedItem.getHoverName().getString().equals("legacy module"),
                "old item id/count/components did not migrate");

        var chest = armor(h, ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD.get());
        var armorId = CelestweaveArmorState.ensureArmorId(chest);
        var raw = (CompoundTag) chest.save(registries);
        var component = raw.getCompound("components").getCompound("ae2lt:celestweave_modules");
        ListTag modules = component.getList("modules", 10);
        var installedOld = oldTag.copy();
        installedOld.putInt("count", 1);
        modules.add(installedOld);
        var toggles = new CompoundTag();
        toggles.putBoolean("undying", false);
        component.put("toggles", toggles);
        var data = new CompoundTag();
        var legacy = new CompoundTag();
        legacy.putInt("ComboCount", 7);
        legacy.putLong("ComboUntil", 12345);
        var options = new CompoundTag();
        options.putBoolean("hit_feedback", false);
        legacy.put("Options", options);
        data.put("undying", legacy);
        component.put("submodule_data", data);

        var migrated = ItemStack.parse(registries, raw).orElseThrow();
        var container = migrated.get(ModDataComponents.CELESTWEAVE_MODULES.get());
        require(container != null && container.armorId().orElseThrow().equals(armorId), "armor identity lost");
        require(container.modules().size() == 2, "phase was not absorbed or energy module was lost");
        require(container.modules().stream().noneMatch(s -> s.is(ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD.get())),
                "legacy phase survived overload migration");
        require(!container.toggles().get("overload_protection") && !container.toggles().containsKey("undying"),
                "disabled state was not preserved");
        require(container.submoduleData().get("overload_protection").equals(legacy), "legacy data lost");
        require(ArmorEnergyBuffer.read(migrated) == CAP, "energy buffer lost");
        String saved = migrated.save(registries).toString();
        require(!saved.contains("module_undying") && !saved.contains("undying:"), "legacy id was written back");
        var reloaded = ItemStack.parse(registries, migrated.save(registries)).orElseThrow();
        require(migrated.save(registries).equals(reloaded.save(registries)), "migration was not idempotent");
        toggles.putBoolean("overload_protection", true);
        var canonical = new CompoundTag();
        canonical.putInt("ComboCount", 9);
        data.put("overload_protection", canonical);
        var mixed = ItemStack.parse(registries, raw).orElseThrow().get(ModDataComponents.CELESTWEAVE_MODULES.get());
        require(mixed.toggles().get("overload_protection"), "new toggle lost to legacy toggle");
        var mergedData = mixed.submoduleData().get("overload_protection");
        require(mergedData.getInt("ComboCount") == 9 && mergedData.getLong("ComboUntil") == 12345,
                "new values must win while old unknown data survives");
        require(legacy.getInt("ComboCount") == 7, "migration mutated input data");
        var phase = new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD.get());
        require(ItemStack.parse(registries, phase.save(registries)).orElseThrow().is(phase.getItem()),
                "standalone phase item was consumed");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void legacyResearchNotesStillRequestTheRenamedRitualItem(GameTestHelper h) {
        var ids = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
        ids.add(net.minecraft.resources.ResourceLocation.parse("ae2lt:pigmee_core"));
        ids.add(net.minecraft.resources.ResourceLocation.parse("ae2lt:module_undying"));
        ids.add(net.minecraft.resources.ResourceLocation.parse("ae2lt:module_phase_lock"));
        for (String id : List.of("stone", "dirt", "sand", "gravel", "apple", "diamond")) {
            ids.add(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id));
        }
        var descriptions = ids.stream().map(id -> "item." + id.getNamespace() + "." + id.getPath()).toList();
        var seed = UUID.randomUUID();
        var note = new ItemStack(ModItems.RESEARCH_NOTE.get());
        new com.moakiee.ae2lt.logic.research.ResearchNoteData(seed,
                com.moakiee.ae2lt.logic.research.RitualGoal.HYPERDIMENSIONAL_PIGMEE,
                ids, descriptions, true).writeTo(note);
        var migrated = com.moakiee.ae2lt.logic.research.ResearchNoteData.read(note);
        require(migrated != null && migrated.ritualSeed().equals(seed) && migrated.consumed(),
                "note seed/completion changed");
        require(migrated.recipeItems().get(1).toString().equals("ae2lt:module_overload_protection"),
                "old note cannot match the renamed item in ritual comparisons");
        require(migrated.descriptionKeys().get(1).equals("item.ae2lt.module_overload_protection"),
                "old note still renders an obsolete translation key");
        require(migrated.recipeItems().subList(2, 9).equals(ids.subList(2, 9)), "ritual order changed");
        com.moakiee.ae2lt.item.ResearchNoteItem.applyGeneratedState(note, migrated);
        require(!note.get(DataComponents.CUSTOM_DATA).copyTag().toString().contains("module_undying"),
                "reopening the note did not save canonical IDs");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void installedCoreSplitsExactlyOnce(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var old = new ItemStack(ModItems.RAILGUN_MODULE_CORE.get());
        old.set(DataComponents.CUSTOM_NAME, Component.literal("legacy"));
        var gun = new ItemStack(ModItems.ELECTROMAGNETIC_RAILGUN.get());
        RailgunStructuralCore.setCore(gun, new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
        var gunTag = (CompoundTag) gun.save(registries);
        var entries = new ListTag();
        entries.add(old.save(registries));
        gunTag.getCompound("components").put("ae2lt:railgun_module_entries", entries);
        gun = ItemStack.parse(registries, gunTag).orElseThrow();
        var storage = RailgunModuleStorage.INSTANCE;
        require(storage.getCount(gun, "charge") == 1 && storage.getCount(gun, "compensation") == 1
                && RailgunStructuralCore.hasCore(gun), "legacy railgun lost modules or structural core");
        require(!storage.installOne(gun, old), "installed a second legacy core");
        var migratedGun = gun.save(registries);
        gun = ItemStack.parse(registries, migratedGun).orElseThrow();
        require(migratedGun.equals(gun.save(registries)), "railgun migration was not idempotent");
        var compensation = storage.uninstallOne(gun, "compensation");
        require(ItemStack.isSameItemSameComponents(compensation, old.transmuteCopy(ModItems.MODULE_COMPENSATION.get(), 1)),
                "compensation components changed");
        require(RailgunModuleStorage.entryData(gun).hasCore() && storage.capabilities(gun).isEmpty(),
                "charge alone must unlock charging without compensation");
        var charge = storage.uninstallOne(gun, "charge");
        require(ItemStack.isSameItemSameComponents(charge, old.transmuteCopy(ModItems.RAILGUN_MODULE_CHARGE.get(), 1)),
                "charge components changed");
        require(storage.installOne(gun, compensation) && !RailgunModuleStorage.entryData(gun).hasCore(),
                "compensation alone unlocked charging");

        var chest = armor(h, ModItems.MODULE_COMPENSATION.get());
        var chestTag = (CompoundTag) chest.save(registries);
        var modules = chestTag.getCompound("components").getCompound("ae2lt:celestweave_modules").getList("modules", 10);
        modules.set(1, old.save(registries));
        chest = ItemStack.parse(registries, chestTag).orElseThrow();
        require(CelestweaveArmorState.getInstalledAmount(chest, registries, "compensation") == 1
                && CelestweaveArmorState.getInstalledAmount(chest, registries, "charge") == 0, "old chest core split incorrectly");
        require(!CelestweaveArmorState.installOneModule(chest, registries, old), "legacy item bypassed chest module limit");
        var migratedChest = chest.save(registries);
        chest = ItemStack.parse(registries, migratedChest).orElseThrow();
        require(migratedChest.equals(chest.save(registries)), "chest migration was not idempotent");
        require(ItemStack.isSameItemSameComponents(CelestweaveArmorState.uninstallOneModule(chest, registries, "compensation"),
                compensation), "chest compensation components changed");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void sharedModulesResolveForEachArmorPart(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        for (var armorItem : List.of(ModItems.CELESTWEAVE_CORE.get(), ModItems.CELESTWEAVE_CONDUIT.get(), ModItems.CELESTWEAVE_STRIDE.get())) {
            var device = new ItemStack(armorItem);
            CelestweaveArmorState.setSlot(device, registries, CelestweaveArmorState.SLOT_CORE, new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
            for (var item : List.of(ModItems.RAILGUN_MODULE_ACCELERATION.get(), ModItems.RAILGUN_MODULE_RANGE.get())) {
                require(CelestweaveArmorState.installOneModule(device, registries, new ItemStack(item)), "shared module refused");
                require(!CelestweaveArmorState.canInstallModule(device, registries, new ItemStack(item)), "duplicate armor module accepted");
            }
            var ids = CelestweaveArmorState.collectSubmodules(device, registries).stream().map(m -> m.id()).toList();
            var distance = switch (CelestweaveArmorState.armorPart(device)) {
                case CHEST -> "reach_extension";
                case LEGS -> "range";
                case FEET -> "dash";
                default -> throw new IllegalStateException();
            };
            require(ids.equals(List.of("acceleration", distance)), "unexpected module controls: " + ids);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void lifeSupportProtectsOnlyItsWearer(GameTestHelper h) {
        if (!net.neoforged.fml.ModList.get().isLoaded("ad_astra")) {
            h.succeed();
            return;
        }
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "LifeTest"));
        var registries = h.getLevel().registryAccess();
        var helmet = new ItemStack(ModItems.CELESTWEAVE_OCULUS.get());
        CelestweaveArmorState.setSlot(helmet, registries, CelestweaveArmorState.SLOT_CORE,
                new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
        require(CelestweaveArmorState.installOneModule(helmet, registries,
                new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_WATER_BREATHING.get())), "life support refused");
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        CelestweaveArmorState.syncSubmoduleActiveState(player, helmet, registries, true, Dist.DEDICATED_SERVER);
        require(earth.terrarium.adastra.api.events.AdAstraEvents.EntityOxygenEvent.fire(player, false), "no personal oxygen");
        require(!earth.terrarium.adastra.api.events.AdAstraEvents.ColdTemperatureTickEvent.fire(h.getLevel(), player), "cold passed through");
        require(!earth.terrarium.adastra.api.events.AdAstraEvents.HotTemperatureTickEvent.fire(h.getLevel(), player), "heat passed through");
        CelestweaveArmorState.syncSubmoduleActiveState(player, helmet, registries, false, Dist.DEDICATED_SERVER);
        require(!earth.terrarium.adastra.api.events.AdAstraEvents.EntityOxygenEvent.fire(player, false), "oxygen persisted");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void shieldAndPurificationExposeMekanismCapabilities(GameTestHelper h) {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism")) {
            h.succeed();
            return;
        }
        var shield = armor(h, ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD.get());
        var player = equip(h, shield);
        var laser = shield.getCapability(mekanism.common.capabilities.Capabilities.LASER_DISSIPATION);
        require(laser != null && laser.getDissipationPercent() == 1.0, "phase failed to absorb full beam");
        ArmorEnergyBuffer.write(shield, h.getLevel().registryAccess(), 0L);
        com.moakiee.ae2lt.integration.mekanism.MekanismArmorIntegration.absorbLaserEnergy(laser, player, 10000, 1);
        require(ArmorEnergyBuffer.read(shield) > 0L, "phase absorption did not charge armor");
        var purify = armor(h, ModItems.CELESTWEAVE_SUBMODULE_PURIFICATION.get());
        equip(h, purify);
        var radiation = purify.getCapability(mekanism.common.capabilities.Capabilities.RADIATION_SHIELDING);
        require(radiation != null && radiation.getRadiationShielding() == 1.0, "purification failed to shield radiation");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 1270)
    public static void purificationConvertsStoredDoseInCleanAir(GameTestHelper h) {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism")) {
            h.succeed();
            return;
        }
        var chest = armor(h, ModItems.CELESTWEAVE_SUBMODULE_PURIFICATION.get());
        var player = equip(h, chest);
        var manager = mekanism.api.radiation.IRadiationManager.INSTANCE;
        require(manager.getRadiationLevel(player) < manager.minRadiationMagnitude(), "test requires clean air");
        var body = player.getCapability(mekanism.common.capabilities.Capabilities.RADIATION_ENTITY);
        require(body != null, "missing native entity radiation capability");
        double baseline = manager.baselineRadiation();
        double initial = baseline + 60.0;
        body.set(initial);
        player.setHealth(2);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        long first = h.getLevel().getGameTime();
        h.onEachTick(() -> {
            long now = h.getLevel().getGameTime();
            if (now % 20 != 0) return;
            long pulse = (now - first) / 20;
            if (pulse == 1) player.setItemSlot(EquipmentSlot.CHEST, chest);
            com.moakiee.ae2lt.celestweave.CelestweaveArmorUtilityHandler.tickEntityUtilities(player);
            if (pulse == 0) {
                require(body.getRadiation() == initial && player.getHealth() == 2, "unprotected wearer processed dose");
            } else if (pulse == 1) {
                require(body.getRadiation() < initial && player.getHealth() > 2, "stored dose did not heal in clean air");
                double afterDose = body.getRadiation();
                float afterHealth = player.getHealth();
                com.moakiee.ae2lt.celestweave.CelestweaveArmorUtilityHandler.tickEntityUtilities(player);
                require(body.getRadiation() == afterDose && player.getHealth() == afterHealth, "duplicate pulse");
                player.setHealth(player.getMaxHealth());
            } else if (pulse == 59) {
                require(body.getRadiation() > baseline && body.getRadiation() < baseline + 1.01,
                        "full-health cleansing did not progress gradually: " + body.getRadiation());
            } else if (pulse == 60) {
                require(body.getRadiation() == baseline, "dose remained after 60 pulses");
                player.setHealth(2);
            } else if (pulse == 61) {
                require(body.getRadiation() == baseline && player.getHealth() == 2, "clean wearer received healing");
                h.succeed();
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
