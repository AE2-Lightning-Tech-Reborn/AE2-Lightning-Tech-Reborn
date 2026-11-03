package com.moakiee.ae2lt.compat.gto;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

class GtoEaepClientMixinExtensionTest {
    private static final String BUTTON = "appeng/client/gui/widgets/ActionButton";

    @ParameterizedTest
    @ValueSource(strings = {"ae2lt.gtoDev23Ae2TestJar", "ae2lt.gtoDev25Ae2TestJar"})
    void bothRealTargetsRetainEaepEncodingAndUploadCallbacks(String targetProperty) throws Exception {
        var target = target(targetProperty);
        var mixin = mixin();
        var bodies = mixin.methods.stream().collect(Collectors.toMap(method -> method.name + method.desc,
                method -> List.of(method.instructions.toArray())));
        var handler = mixin.methods.stream().filter(method -> method.name.equals("eap$wrapEncodeButton"))
                .findFirst().orElseThrow();
        assertTrue(handler.visibleAnnotations.stream().anyMatch(annotation -> annotation.desc.endsWith("/ModifyVariable;")));

        GtoEaepMixinExtension.adaptEncodingScreen(mixin, target);

        assertEquals(bodies.size(), mixin.methods.size());
        for (var method : mixin.methods) {
            assertEquals(bodies.get(method.name + method.desc), List.of(method.instructions.toArray()),
                    "force encode packet, normal encoding and upload behavior must all survive retargeting");
        }
        var modifier = handler.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.endsWith("/ModifyExpressionValue;")).findFirst().orElseThrow();
        assertEquals(1, value(modifier, "require"));
        assertEquals(1, value(modifier, "allow"));
        assertEquals(List.of("<init>"), value(modifier, "method"));
        var points = (List<?>) value(modifier, "at");
        assertEquals(1, points.size());
        var point = (AnnotationNode) points.getFirst();
        assertEquals("NEW", value(point, "value"));
        assertEquals("(Lappeng/api/config/ActionItems;Ljava/util/function/Consumer;)L" + BUTTON + ";",
                value(point, "target"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ae2lt.gtoDev23Ae2TestJar", "ae2lt.gtoDev25Ae2TestJar"})
    void changedButtonConstructionIsRejected(String targetProperty) throws Exception {
        var target = target(targetProperty);
        var constructor = target.methods.stream().filter(method -> method.name.equals("<init>"))
                .findFirst().orElseThrow();
        for (var instruction : constructor.instructions.toArray()) {
            if (instruction instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW
                    && allocation.desc.equals(BUTTON)) constructor.instructions.remove(instruction);
        }
        assertThrows(IllegalStateException.class, () -> GtoEaepMixinExtension.adaptEncodingScreen(mixin(), target));
        var ambiguous = target(targetProperty);
        ambiguous.methods.stream().filter(method -> method.name.equals("<init>")).findFirst().orElseThrow()
                .instructions.add(new TypeInsnNode(Opcodes.NEW, BUTTON));
        assertThrows(IllegalStateException.class, () -> GtoEaepMixinExtension.adaptEncodingScreen(mixin(), ambiguous));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ae2lt.gtoDev23Ae2TestJar", "ae2lt.gtoDev25Ae2TestJar"})
    void changedEaepHookIsRejected(String targetProperty) throws Exception {
        var mixin = mixin();
        mixin.methods.removeIf(method -> method.name.equals("eap$wrapEncodeButton"));
        assertThrows(IllegalStateException.class, () -> GtoEaepMixinExtension.adaptEncodingScreen(mixin, target(targetProperty)));
    }

    @Test
    void lateEaepTitleWriteRetainsAllOriginalInstructionsAndOtherCallbacks() throws Exception {
        var node = actual("ae2lt.eaepTestJar", "com/extendedae_plus/mixin/ae2/client/gui/CraftConfirmScreenMixin");
        var before = node.methods.stream().collect(Collectors.toMap(method -> method.name + method.desc,
                method -> List.of(method.instructions.toArray())));
        int fields = node.fields.size();
        GtoEaepMixinExtension.adaptConfirmTitle(node);
        assertEquals(fields, node.fields.size());
        assertEquals(before.size(), node.methods.size());
        for (var method : node.methods) {
            var original = before.get(method.name + method.desc);
            if (!method.name.equals("eap$rewriteCraftingPlanTitle")) {
                assertEquals(original, List.of(method.instructions.toArray()), "retain EAEP force start/bookmark handling");
                continue;
            }
            var wrapper = java.util.Arrays.stream(method.instructions.toArray())
                    .filter(instruction -> instruction instanceof MethodInsnNode call
                            && call.owner.equals("com/moakiee/ae2lt/compat/gto/GtoEaepCraftConfirmSupport")
                            && call.name.equals("preserveCoreTitle")).findFirst().orElseThrow();
            assertEquals(Opcodes.ALOAD, wrapper.getPrevious().getOpcode());
            assertTrue(wrapper.getNext() instanceof MethodInsnNode call && call.name.equals("setTextContent"));
            assertEquals(original.size() + 2, method.instructions.size());
            method.instructions.remove(wrapper.getPrevious());
            method.instructions.remove(wrapper);
            assertEquals(original, List.of(method.instructions.toArray()), "preserve EAEP's numeric formatting");
        }
    }

    @Test
    void changedEaepTitleWriterIsRejected() throws Exception {
        var node = actual("ae2lt.eaepTestJar", "com/extendedae_plus/mixin/ae2/client/gui/CraftConfirmScreenMixin");
        node.methods.removeIf(method -> method.name.equals("eap$rewriteCraftingPlanTitle"));
        assertThrows(IllegalStateException.class, () -> GtoEaepMixinExtension.adaptConfirmTitle(node));
    }

    @Test
    void ordinaryTitleKeepsEaepFormattingAndCoreAlgorithm() {
        var original = net.minecraft.network.chat.Component.literal("EAEP formatted bytes");
        var name = net.minecraft.network.chat.Component.literal("planner");
        var plan = new appeng.menu.me.crafting.CraftingPlanSummary(73, false, List.of());
        assertSame(original, GtoEaepCraftConfirmSupport.title(original, plan, net.minecraft.network.chat.Component.empty()));
        var combined = GtoEaepCraftConfirmSupport.title(original, plan, name);
        var translated = (net.minecraft.network.chat.contents.TranslatableContents) combined.getContents();
        assertEquals("gui.thunderbolt.crafting_plan.algorithm", translated.getKey());
        assertArrayEquals(new Object[] {original, name}, translated.getArgs());
    }

    @Test
    void exactTitleSurvivesLateWriteAndOrdinaryRestorationHasNoStaleBytes() {
        var original = net.minecraft.network.chat.Component.literal("EAEP formatted long");
        var name = net.minecraft.network.chat.Component.literal("planner");
        var huge = BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(37));
        for (boolean preview : new boolean[] {false, true}) {
            var plan = new appeng.menu.me.crafting.CraftingPlanSummary(Long.MAX_VALUE, preview, List.of());
            com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports.attach(plan,
                    new com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport(huge, Map.of(), false, true));
            var title = GtoEaepCraftConfirmSupport.title(original, plan, name);
            var translated = (net.minecraft.network.chat.contents.TranslatableContents) title.getContents();
            assertArrayEquals(new Object[] {com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter.compact(huge, 1)},
                    translated.getArgs());
        }
        var ordinary = new appeng.menu.me.crafting.CraftingPlanSummary(73, false, List.of());
        var title = GtoEaepCraftConfirmSupport.title(original, ordinary, name);
        assertArrayEquals(new Object[] {original, name},
                ((net.minecraft.network.chat.contents.TranslatableContents) title.getContents()).getArgs());
    }

    private static ClassNode target(String property) throws Exception {
        return actual(property, "appeng/client/gui/me/items/PatternEncodingTermScreen");
    }

    private static ClassNode mixin() throws Exception {
        return actual("ae2lt.eaepTestJar", "com/extendedae_plus/mixin/ae2/client/gui/PatternEncodingTermScreenMixin");
    }

    private static ClassNode actual(String property, String name) throws Exception {
        String configured = System.getProperty(property);
        assumeTrue(configured != null, "requires actual supported addon/AE2 release fixture: " + property);
        var path = Path.of(configured);
        assertTrue(Files.isRegularFile(path));
        try (var archive = new ZipFile(path.toFile())) {
            var entry = archive.getEntry(name + ".class");
            assertNotNull(entry);
            try (var input = archive.getInputStream(entry)) {
                var node = new ClassNode();
                new ClassReader(input).accept(node, 0);
                return node;
            }
        }
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
}
