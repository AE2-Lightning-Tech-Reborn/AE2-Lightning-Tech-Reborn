package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/** Actual jar bytes only: test fixtures and client class initialization cannot mask missing APIs. */
@EnabledIfSystemProperty(named = "ae2lt.apiAudit.enabled", matches = "true")
class Forge120OptionalApiContractTest {
    @Test
    void neoEcoGtoReleaseExposesTheAllocatedFacadeAndCpuCheckpointAbi() throws Exception {
        var prefix = "cn/dancingsnow/neoecoae/api/me/";
        exists("neoeco", prefix + "ECOCraftingCPULogic.class");
        exists("neoeco", prefix + "ECOFastPathFacade.class");
        exists("neoeco", prefix + "ECOFastPathFacade$Reservation.class");
        exists("neoeco", prefix + "ECOFastPathFacade$EnergyAccount.class");
        exists("neoeco", prefix + "provider/ECOFastPathDispatchProvider.class");
        method("neoeco", prefix + "ECOFastPathFacade", "prepareAllocated",
                "(Lappeng/api/networking/crafting/ICraftingProvider;Lappeng/api/crafting/IPatternDetails;"
                        + "[Lappeng/api/stacks/KeyCounter;JLnet/minecraft/world/level/Level;Ljava/util/UUID;)L"
                        + prefix + "ECOFastPathFacade$PreparedBatch;", true);
        method("neoeco", prefix + "ECOFastPathFacade", "pushPatternForJob",
                "(Lappeng/api/networking/crafting/ICraftingProvider;Lappeng/api/crafting/IPatternDetails;"
                        + "[Lappeng/api/stacks/KeyCounter;Ljava/util/UUID;)Z", true);
        method("neoeco", prefix + "ECOFastPathFacade$PreparedBatch", "craftCount", "()J", false);
        method("neoeco", prefix + "ECOFastPathFacade$PreparedBatch", "power", "()D", false);
        method("neoeco", prefix + "ECOFastPathFacade$PreparedBatch", "remainders", "()Ljava/util/List;", false);
        method("neoeco", prefix + "ECOFastPathFacade$PreparedBatch", "submit",
                "(L" + prefix + "ECOFastPathFacade$EnergyAccount;)Z", false);
        method("neoeco", prefix + "ECOFastPathFacade$EnergyAccount", "reserve",
                "(D)L" + prefix + "ECOFastPathFacade$Reservation;", false);
        for (String name : new String[] {"commit", "refund"})
            method("neoeco", prefix + "ECOFastPathFacade$Reservation", name, "()V", false);
        method("neoeco", prefix + "ECOCraftingCPULogic", "writeToNBT",
                "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", false);
        method("neoeco", prefix + "ECOCraftingCPULogic", "readFromNBT",
                "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", false);
        method("neoeco", prefix + "ECOCraftingCPU", "markDirty", "()V", false);
    }

    @Test
    void thunderboltBatchResolverHasCrossTickContract() throws Exception {
        var type = "com/moakiee/thunderbolt/api/crafting/batch/BatchProviderResolver";
        method("thunderbolt", type, "cacheResolutionAcrossTicks", "()Z", false);
        method("thunderbolt", type, "resolve",
                "(Lappeng/api/networking/crafting/ICraftingProvider;)Lcom/moakiee/thunderbolt/api/crafting/batch/IBatchCraftingProvider;", false);
        for (String name : new String[] {
                "api/crafting/batch/BatchProviderAdapter", "core/storage/big/BigAmounts",
                "core/storage/big/BigStorageOps", "core/crafting/big/BigCraftingPlanner",
                "core/crafting/big/BigExecutionProgram", "core/crafting/pattern/PlannedInputPattern",
                "ae2/crafting/ExactPlanReports", "ae2/crafting/ExactAmountFormatter",
                "ae2/crafting/ExactPlanPresentation", "ae2/crafting/ExactPlanReport"}) {
            exists("thunderbolt", "com/moakiee/thunderbolt/" + name + ".class");
        }
    }

    @Test
    void neoEcoLtExactAndJobLifecycleReflectionContractsExist() throws Exception {
        var prefix = "cn/dancingsnow/neoecoae/";
        var facade = prefix + "api/me/ECOFastPathFacade";
        method("neoeco", facade, "prepareExactAllocated",
                "(Lappeng/api/networking/crafting/ICraftingProvider;Lappeng/api/crafting/IPatternDetails;"
                        + "[Lappeng/api/stacks/KeyCounter;Ljava/math/BigInteger;Lnet/minecraft/world/level/Level;"
                        + "Ljava/util/UUID;)L" + facade + "$PreparedExactBatch;", true);
        method("neoeco", facade + "$PreparedExactBatch", "craftCount", "()Ljava/math/BigInteger;", false);
        method("neoeco", facade + "$PreparedExactBatch", "submit", "()Z", false);
        method("neoeco", facade, "cancelJob", "(Lnet/minecraft/world/level/Level;Ljava/util/UUID;)V", true);
        method("neoeco", prefix + "crafting/execution/worker/ECOCraftingJobLifecycle", "finish",
                "(Lnet/minecraft/world/level/Level;Ljava/util/UUID;Z)V", true);
        var migration = prefix + "grid/PatternMigrationCoordinator";
        method("neoeco", migration, "forGrid", "(Ljava/lang/Object;)L" + migration + ";", true);
        for (var name : new String[] {"tryAcquire", "isOwner"})
            method("neoeco", migration, name, "(Ljava/lang/Object;)Z", false);
        method("neoeco", migration, "release", "(Ljava/lang/Object;)V", false);
    }

    @Test
    void thunderboltKeySubclassesRespectFinalGtoIdentityMethods() throws Exception {
        assertCoreKeyHierarchy("ae2");
        assertCoreKeyHierarchy("ae2Dev25");
    }

    private static void assertCoreKeyHierarchy(String ae2) throws Exception {
        var inheritedFinalMethods = new HashSet<String>();
        // Dev25 AEKey extends IdentityKey, whose hashCode is final; Dev23 permits
        // overriding it. Check both original jars without loading planner classes.
        for (var type = read(ae2, "appeng/api/stacks/AEKey"); type != null;) {
            for (var method : type.methods) {
                if ((method.access & Opcodes.ACC_FINAL) != 0
                        && (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
                    inheritedFinalMethods.add(method.name + method.desc);
                }
            }
            if (type.superName == null || type.superName.equals("java/lang/Object")) break;
            type = read(ae2, type.superName);
        }
        var coreClasses = new HashMap<String, ClassNode>();
        try (var jar = jar("thunderbolt")) {
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (var in = jar.getInputStream(entry)) {
                    var type = new ClassNode();
                    new ClassReader(in).accept(type,
                            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    coreClasses.put(type.name, type);
                }
            }
        }
        assertFalse(coreClasses.isEmpty(), "Actual Core audit jar has no classes");
        var overrides = new ArrayList<String>();
        for (var type : coreClasses.values()) {
            var parent = type.superName;
            var visited = new HashSet<String>();
            while (parent != null && !parent.equals("appeng/api/stacks/AEKey") && visited.add(parent)) {
                var ancestor = coreClasses.get(parent);
                parent = ancestor == null ? null : ancestor.superName;
            }
            if (!"appeng/api/stacks/AEKey".equals(parent)) continue;
            for (var method : type.methods) {
                if (inheritedFinalMethods.contains(method.name + method.desc)) {
                    overrides.add(type.name + "." + method.name + method.desc);
                }
            }
        }
        overrides.sort(String::compareTo);
        assertTrue(overrides.isEmpty(), ae2 + ": overrides final GTO AEKey/IdentityKey methods: " + overrides);
    }

    @Test
    void advancedAeUsesForgeArrayEncoderAndInheritedSparseAccessors() throws Exception {
        var prefix = "net/pedroksl/advanced_ae/common/";
        method("advancedae", prefix + "patterns/AdvPatternDetails", "directionalInputsSet", "()Z", false);
        method("advancedae", prefix + "patterns/AdvPatternDetails", "getDirectionSideForInputKey",
                "(Lappeng/api/stacks/AEKey;)Lnet/minecraft/core/Direction;", false);
        method("advancedae", prefix + "patterns/AdvPatternDetails", "getDirectionMap",
                "()Ljava/util/HashMap;", false);
        method("advancedae", prefix + "patterns/AdvPatternDetailsEncoder", "encodeProcessingPattern",
                "([Lappeng/api/stacks/GenericStack;[Lappeng/api/stacks/GenericStack;Ljava/util/HashMap;)Lnet/minecraft/world/item/ItemStack;", true);
        assertEquals("appeng/crafting/pattern/AEProcessingPattern",
                read("advancedae", prefix + "patterns/AdvProcessingPattern").superName);
        for (String name : new String[] {"getSparseInputs", "getSparseOutputs"}) {
            method("ae2", "appeng/crafting/pattern/AEProcessingPattern", name,
                    "()[Lappeng/api/stacks/GenericStack;", false);
        }
        method("advancedae", prefix + "cluster/AdvCraftingCPU", "markDirty", "()V", false);
        method("advancedae", prefix + "cluster/AdvCraftingCPU", "updateOutput",
                "(Lappeng/api/stacks/GenericStack;)V", false);
        method("advancedae", prefix + "logic/AdvCraftingCPULogic", "writeToNBT",
                "(Lnet/minecraft/nbt/CompoundTag;)V", false);
    }

    @Test
    void extendedAePlusHasUploadApiButNoLattraItems() throws Exception {
        var type = "com/extendedae_plus/api/upload/IPatternEncodingShiftUploadSync";
        method("extendedaeplus", type, "eap$clientSetShiftUpload", "(Z)V", false);
        method("extendedaeplus", type, "eap$consumeShiftUploadFlag", "()Z", false);
        // Registration constants determine availability; newer jars also carry unused Lattra assets.
        try (var jar = jar("extendedaeplus")) {
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    try (var in = jar.getInputStream(entry)) {
                        var bytes = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                        assertFalse(bytes.contains("lattra_crystal"), entry.getName());
                        assertFalse(bytes.contains("lattra_dust"), entry.getName());
                    }
                }
            }
        }
    }

    @Test
    void wtlibHasNativeTerminalAndMagnetApis() throws Exception {
        var prefix = "de/mari_023/ae2wtlib/";
        method("wtlib", prefix + "wut/WUTHandler", "hasTerminal",
                "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z", true);
        method("wtlib", prefix + "UpgradeHelper", "addUpgradeToAllTerminals",
                "(Lnet/minecraft/world/level/ItemLike;I)V", true);
        method("wtlib", prefix + "wct/CraftingTerminalHandler", "getCraftingTerminal",
                "()Lnet/minecraft/world/item/ItemStack;", false);
        method("wtlib", prefix + "wct/magnet_card/MagnetHandler", "handle",
                "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;)V", true);
    }

    @Test
    void bloodMagicHarvestAnointmentContractExists() throws Exception {
        var prefix = "wayoftime/bloodmagic/anointment/";
        var holder = "L" + prefix + "AnointmentHolder;";
        method("bloodmagic", prefix + "AnointmentHolder", "<init>", "(Ljava/util/Map;)V", false);
        method("bloodmagic", prefix + "AnointmentHolder", "fromItemStack",
                "(Lnet/minecraft/world/item/ItemStack;)" + holder, true);
        method("bloodmagic", prefix + "AnointmentHolder", "getAnointments", "()Ljava/util/Map;", false);
        method("bloodmagic", prefix + "AnointmentHolder", "toItemStack", "(Lnet/minecraft/world/item/ItemStack;)V", false);
        method("bloodmagic", prefix + "Anointment", "consumeOnHarvest", "()Z", false);
        method("bloodmagic", prefix + "Anointment", "removeAnointment",
                "(" + holder + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;)Z", false);
        for (String name : new String[] {"getMaxDamage", "getDamage"}) {
            method("bloodmagic", prefix + "AnointmentData", name, "()I", false);
        }
        method("bloodmagic", prefix + "AnointmentData", "damage", "(I)V", false);
        method("bloodmagic", prefix + "AnointmentData", "isMaxDamage", "()Z", false);
    }

    @Test
    void silentGearRetainedToolApiExists() throws Exception {
        exists("silentgear", "net/silentchaos512/gear/api/item/ICoreItem.class");
        method("silentgear", "net/silentchaos512/gear/util/GearHelper", "isBroken",
                "(Lnet/minecraft/world/item/ItemStack;)Z", true);
        method("silentgear", "net/silentchaos512/gear/util/GearHelper", "damageItem",
                "(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)I", true);
    }

    @Test
    void apotheosisBoonUsesForgeRegistryResources() throws Exception {
        exists("apotheosis", "data/apotheosis/tags/items/boon_drops.json");
        exists("apotheosis", "assets/apotheosis/patchouli_books/apoth_chronicle/en_us/entries/enchanting/enchantments/earths_boon.json");
    }

    @Test
    void forgeEnergyBridgesHaveExpectedLongAndFloatingLongApis() throws Exception {
        method("appflux", "com/glodblock/github/appflux/config/AFConfig", "getFluxAccessorIO", "()J", true);
        method("appflux", "com/glodblock/github/appflux/common/me/key/FluxKey", "of",
                "(Lcom/glodblock/github/appflux/common/me/key/type/EnergyType;)Lcom/glodblock/github/appflux/common/me/key/FluxKey;", true);
        method("fluxnetworks", "sonar/fluxnetworks/api/energy/IFNEnergyStorage", "receiveEnergyL", "(JZ)J", false);
        method("mekanism", "mekanism/api/energy/IStrictEnergyHandler", "insertEnergy",
                "(ILmekanism/api/math/FloatingLong;Lmekanism/api/Action;)Lmekanism/api/math/FloatingLong;", false);
    }

    @Test
    void recipeViewerAndJadeUse120Apis() throws Exception {
        method("jade", "snownee/jade/api/IServerDataProvider", "appendServerData",
                "(Lnet/minecraft/nbt/CompoundTag;Lsnownee/jade/api/Accessor;)V", false);
        exists("jei", "mezz/jei/api/recipe/transfer/IRecipeTransferHandler.class");
        exists("emi", "dev/emi/emi/api/EmiPlugin.class");
        exists("curios", "top/theillusivec4/curios/api/CuriosApi.class");
        exists("polymorph", "com/illusivesoulworks/polymorph/api/PolymorphApi.class");
    }

    @Test
    void veilForgeShaderBridgeSignatureExists() throws Exception {
        method("veil", "foundry/veil/api/client/render/VeilRenderBridge", "shaderState",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderStateShard$ShaderStateShard;", true);
    }

    private static ZipFile jar(String id) throws IOException {
        var property = System.getProperty("ae2lt.apiAudit." + id);
        assertNotNull(property, "Missing real audit jar for " + id);
        return new ZipFile(Path.of(property).toFile());
    }

    private static void exists(String id, String entry) throws IOException {
        try (var jar = jar(id)) { assertNotNull(jar.getEntry(entry), id + ": " + entry); }
    }

    private static void missing(String id, String entry) throws IOException {
        try (var jar = jar(id)) { assertNull(jar.getEntry(entry), id + " now has " + entry + "; re-audit support"); }
    }

    private static ClassNode read(String id, String name) throws IOException {
        try (var jar = jar(id)) {
            var entry = jar.getEntry(name + ".class");
            assertNotNull(entry, id + ": " + name);
            try (var in = jar.getInputStream(entry)) {
                var node = new ClassNode();
                new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return node;
            }
        }
    }

    private static void method(String id, String owner, String name, String descriptor, boolean isStatic) throws IOException {
        var match = read(id, owner).methods.stream()
                .filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst();
        assertTrue(match.isPresent(), id + ": " + owner + "." + name + descriptor);
        assertEquals(isStatic, (match.orElseThrow().access & Opcodes.ACC_STATIC) != 0, owner + "." + name);
        assertTrue((match.orElseThrow().access & Opcodes.ACC_PUBLIC) != 0, owner + "." + name + " must be public");
    }
}
